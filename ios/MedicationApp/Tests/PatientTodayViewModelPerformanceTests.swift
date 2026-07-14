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
        let sessionStore = SessionStore(userDefaults: userDefaults)
        sessionStore.setMode(.patient)
        sessionStore.savePatientToken("patient-token")
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

        viewModel.confirmBulkRecord(for: .noon)
        viewModel.executeBulkRecord()

        await fulfillment(of: [refreshStarted], timeout: 1)
        try await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertFalse(viewModel.isUpdating)
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
