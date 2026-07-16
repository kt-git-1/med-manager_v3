import XCTest
@testable import MedicationApp

@MainActor
final class PatientTodayViewModelPerformanceTests: XCTestCase {
    private let suiteName = "PatientTodayViewModelPerformanceTests"

    override func setUp() {
        super.setUp()
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        PatientTodayPerformanceURLProtocol.requestHandler = nil
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testBulkRecordDoesNotWaitForNotificationRefreshToFinish() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PatientTodayPerformanceURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        let userDefaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        let sessionStore = SessionStore(
            userDefaults: userDefaults,
            secureStorage: PatientTodayPerformanceTestSecureStorage()
        )
        sessionStore.setMode(.patient)
        sessionStore.savePatientToken(
            "patient-token",
            expiresAt: Date().addingTimeInterval(60 * 24 * 60 * 60)
        )
        let apiClient = APIClient(
            baseURL: try XCTUnwrap(URL(string: "http://localhost:3000")),
            sessionStore: sessionStore,
            urlSession: urlSession
        )
        PatientTodayPerformanceURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["content-type": "application/json"]
            )!
            if request.url?.path == "/api/patient/dose-records/slot" {
                return (
                    response,
                    Data(
                        #"{"updatedCount":1,"remainingCount":0,"insufficientCount":0,"totalPills":1,"medCount":1,"slotTime":"12:00","slotSummary":{"morning":"none","noon":"taken","evening":"none","bedtime":"none"},"recordingGroupId":"group-1"}"#.utf8
                    )
                )
            }
            Thread.sleep(forTimeInterval: 1)
            return (response, Data(#"{"data":[]}"#.utf8))
        }
        let refreshStarted = expectation(description: "notification refresh started")
        let viewModel = PatientTodayViewModel(
            apiClient: apiClient,
            onScheduledDoseRecorded: {
                refreshStarted.fulfill()
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        )
        let scheduledAt = try XCTUnwrap(
            ISO8601DateFormatter().date(from: "2026-07-16T03:00:00Z")
        )
        viewModel.items = [
            ScheduleDoseDTO(
                key: "patient-1:med-1:2026-07-16T03:00:00.000Z",
                patientId: "patient-1",
                medicationId: "med-1",
                scheduledAt: scheduledAt,
                effectiveStatus: .pending,
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
        ]

        viewModel.confirmBulkRecord(for: .noon)
        viewModel.executeBulkRecord()

        await fulfillment(of: [refreshStarted], timeout: 1)
        try await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertFalse(viewModel.isUpdating)
        XCTAssertEqual(viewModel.items.first?.effectiveStatus, .taken)
        XCTAssertEqual(viewModel.items.first?.recordedByType, .patient)

        // Let the deliberately slow background data refresh finish before teardown.
        try await Task.sleep(for: .seconds(1))
    }
}

private final class PatientTodayPerformanceURLProtocol: URLProtocol {
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

private final class PatientTodayPerformanceTestSecureStorage: SessionSecureStorage {
    private var values: [String: String] = [:]

    func string(forKey key: String) -> String? { values[key] }
    func setString(_ value: String, forKey key: String) { values[key] = value }
    func removeString(forKey key: String) { values.removeValue(forKey: key) }
}
