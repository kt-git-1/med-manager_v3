import XCTest
@testable import MedicationApp

@MainActor
final class TodayCaregiverFlowTests: XCTestCase {
    private let suiteName = "TodayCaregiverFlowTests"

    override func setUp() {
        super.setUp()
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        CaregiverTodayURLProtocol.requestHandler = nil
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testDeletingDoseNotifiesCachedHistoryToRefresh() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [CaregiverTodayURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        let sessionStore = SessionStore(
            userDefaults: defaults,
            secureStorage: CaregiverTodayTestSecureStorage()
        )
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverSession(
            SupabaseSession(
                accessToken: "caregiver-token",
                refreshToken: "refresh-token",
                expiresIn: 3_600
            )
        )
        sessionStore.setCurrentPatientId("patient-1")
        let apiClient = APIClient(
            baseURL: try XCTUnwrap(URL(string: "http://localhost:3000")),
            sessionStore: sessionStore,
            urlSession: urlSession
        )

        CaregiverTodayURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["content-type": "application/json"]
            )!
            switch request.url?.path {
            case "/api/patients/patient-1/inventory":
                return (response, Data(#"{"data":{"patientId":"patient-1","medications":[]}}"#.utf8))
            default:
                return (response, Data(#"{"data":[]}"#.utf8))
            }
        }

        let historyRefresh = expectation(
            forNotification: .doseRecordsUpdated,
            object: nil
        )
        let viewModel = CaregiverTodayViewModel(apiClient: apiClient)
        let dose = ScheduleDoseDTO(
            key: "patient-1:med-1:2026-07-14T04:00:00.000Z",
            patientId: "patient-1",
            medicationId: "med-1",
            scheduledAt: try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-07-14T04:00:00Z")),
            effectiveStatus: .taken,
            recordedByType: .caregiver,
            medicationSnapshot: MedicationSnapshotDTO(
                name: "Test",
                dosageText: "1 tablet",
                doseCountPerIntake: 1,
                dosageStrengthValue: 1,
                dosageStrengthUnit: "tablet",
                notes: nil
            )
        )

        viewModel.deleteDose(dose)

        await fulfillment(of: [historyRefresh], timeout: 1)
    }

    func testBulkRecordUpdatesTodayImmediatelyAfterMutationSucceeds() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [CaregiverTodayURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        let sessionStore = SessionStore(
            userDefaults: defaults,
            secureStorage: CaregiverTodayTestSecureStorage()
        )
        sessionStore.setMode(.caregiver)
        sessionStore.saveCaregiverSession(
            SupabaseSession(
                accessToken: "caregiver-token",
                refreshToken: "refresh-token",
                expiresIn: 3_600
            )
        )
        sessionStore.setCurrentPatientId("patient-1")
        let apiClient = APIClient(
            baseURL: try XCTUnwrap(URL(string: "http://localhost:3000")),
            sessionStore: sessionStore,
            urlSession: urlSession
        )

        CaregiverTodayURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["content-type": "application/json"]
            )!
            if request.httpMethod == "POST",
               request.url?.path == "/api/patients/patient-1/dose-records/slot" {
                return (
                    response,
                    Data(
                        #"{"updatedCount":1,"remainingCount":0,"insufficientCount":0,"totalPills":1,"medCount":1,"slotTime":"12:00","slotSummary":{"morning":"none","noon":"taken","evening":"none","bedtime":"none"},"recordingGroupId":"group-1"}"#.utf8
                    )
                )
            }

            if request.url?.path == "/api/patients/patient-1/inventory" {
                return (response, Data(#"{"data":{"patientId":"patient-1","medications":[]}}"#.utf8))
            }
            return (response, Data(#"{"data":[]}"#.utf8))
        }

        let scheduledAt = try XCTUnwrap(
            ISO8601DateFormatter().date(from: "2026-07-16T03:00:00Z")
        )
        let dose = ScheduleDoseDTO(
            key: "patient-1:med-1:2026-07-16T03:00:00.000Z",
            patientId: "patient-1",
            medicationId: "med-1",
            scheduledAt: scheduledAt,
            effectiveStatus: .missed,
            recordedByType: nil,
            medicationSnapshot: MedicationSnapshotDTO(
                name: "Test",
                dosageText: "1 tablet",
                doseCountPerIntake: 1,
                dosageStrengthValue: 1,
                dosageStrengthUnit: "tablet",
                notes: nil
            )
        )
        let viewModel = CaregiverTodayViewModel(apiClient: apiClient)
        viewModel.items = [dose]
        let mutationSucceeded = expectation(
            forNotification: .doseRecordsUpdated,
            object: nil
        )

        viewModel.recordDoses([dose], slot: .noon)

        await fulfillment(of: [mutationSucceeded], timeout: 2)
        for _ in 0..<5 where viewModel.isUpdating {
            await Task.yield()
        }
        XCTAssertEqual(viewModel.items.first?.effectiveStatus, .taken)
        XCTAssertEqual(viewModel.items.first?.recordedByType, .caregiver)
        XCTAssertEqual(viewModel.scrollToTopRequest, 1)
        XCTAssertFalse(viewModel.isUpdating)
        try await Task.sleep(for: .milliseconds(50))
    }
}

private final class CaregiverTodayURLProtocol: URLProtocol {
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.requestHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

private final class CaregiverTodayTestSecureStorage: SessionSecureStorage {
    private var values: [String: String] = [:]

    func string(forKey key: String) -> String? { values[key] }
    func setString(_ value: String, forKey key: String) { values[key] = value }
    func removeString(forKey key: String) { values.removeValue(forKey: key) }
}
