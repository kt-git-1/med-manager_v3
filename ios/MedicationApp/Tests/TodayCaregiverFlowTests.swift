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
            takenAt: try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-07-14T04:05:00Z")),
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

    func testCaregiverTodayRefreshUsesServerPatientSlotTimes() async throws {
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
        let preferencesStore = NotificationPreferencesStore(defaults: defaults)
        preferencesStore.switchPatient("patient-1")
        preferencesStore.setSlotTime(.noon, hour: 13, minute: 0)
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
            case "/api/patients":
                return (response, Data(#"{"data":[{"id":"patient-1","displayName":"QA","slotTimes":{"morning":"07:15","noon":"12:20","evening":"18:40","bedtime":"22:30"}}]}"#.utf8))
            case "/api/patients/patient-1/inventory":
                return (response, Data(#"{"data":{"patientId":"patient-1","medications":[]}}"#.utf8))
            default:
                return (response, Data(#"{"data":[]}"#.utf8))
            }
        }

        let viewModel = CaregiverTodayViewModel(
            apiClient: apiClient,
            preferencesStore: preferencesStore
        )
        viewModel.load(showLoading: true)
        for _ in 0..<100 where preferencesStore.slotTime(for: .noon).hour == 13 {
            try await Task.sleep(for: .milliseconds(10))
        }

        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertEqual(preferencesStore.slotTime(for: .noon).hour, 12)
        XCTAssertEqual(preferencesStore.slotTime(for: .noon).minute, 20)
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
            takenAt: nil,
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
        let analytics = CaregiverAnalyticsTrackingSpy()
        let viewModel = CaregiverTodayViewModel(apiClient: apiClient, analytics: analytics)
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
        XCTAssertEqual(
            analytics.events,
            [
                "started:dose_recorded:caregiver",
                "completed:dose_recorded:caregiver"
            ]
        )
        try await Task.sleep(for: .milliseconds(50))
    }

    func testOverviewShowsNoPlanInsteadOfTakenForEmptySlot() {
        let state = CaregiverTodayOverviewState.resolve(statuses: [], isLate: false)

        XCTAssertEqual(state, .noPlan)
        XCTAssertEqual(state.iconName, "minus")
    }

    func testOverviewShowsTakenOnlyWhenScheduledDosesAreTaken() {
        let state = CaregiverTodayOverviewState.resolve(
            statuses: [.taken, .taken],
            isLate: false
        )

        XCTAssertEqual(state, .taken)
        XCTAssertEqual(state.iconName, "checkmark")
    }

    func testTimelineRecorderSummaryIsNilWithoutTakenDoses() {
        XCTAssertNil(CaregiverTodayView.TimelineRecorderSummary.resolve(recorders: []))
    }

    func testTimelineRecorderSummaryShowsSingleRecorder() {
        XCTAssertEqual(
            CaregiverTodayView.TimelineRecorderSummary.resolve(recorders: [.patient, .patient]),
            .patient
        )
        XCTAssertEqual(
            CaregiverTodayView.TimelineRecorderSummary.resolve(recorders: [.caregiver]),
            .caregiver
        )
    }

    func testTimelineRecorderSummaryShowsMixedRecorders() {
        XCTAssertEqual(
            CaregiverTodayView.TimelineRecorderSummary.resolve(recorders: [.patient, .caregiver]),
            .mixed
        )
    }

    func testTimelineRecorderSummaryShowsUnknownWhenAnyRecorderIsMissing() {
        XCTAssertEqual(
            CaregiverTodayView.TimelineRecorderSummary.resolve(recorders: [.patient, nil]),
            .unknown
        )
    }

    func testRegisteredMedicationIsDistinguishedFromNoMedicationWhenTodayHasNoSchedule() async throws {
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
            case "/api/medications":
                return (
                    response,
                    Data(
                        #"{"data":[{"id":"med-1","patientId":"patient-1","name":"朝の薬","dosageText":"5 mg","doseCountPerIntake":1,"dosageStrengthValue":5,"dosageStrengthUnit":"mg","notes":null,"isPrn":false,"prnInstructions":null,"startDate":"2026-08-23T00:00:00Z","endDate":null,"inventoryCount":7,"inventoryUnit":"錠","inventoryEnabled":false,"inventoryQuantity":7,"inventoryOut":false,"isActive":true,"isArchived":false,"nextScheduledAt":"2026-08-24T08:00:00Z","regimenTimes":["morning"],"regimenDaysOfWeek":[]}] }"#.utf8
                    )
                )
            case "/api/patients/patient-1/inventory":
                return (response, Data(#"{"data":{"patientId":"patient-1","medications":[]}}"#.utf8))
            default:
                return (response, Data(#"{"data":[]}"#.utf8))
            }
        }

        let viewModel = CaregiverTodayViewModel(apiClient: apiClient)
        await viewModel.refresh()

        XCTAssertTrue(viewModel.items.isEmpty)
        XCTAssertTrue(viewModel.prnMedications.isEmpty)
        XCTAssertTrue(viewModel.hasRegisteredMedications)
        XCTAssertNil(viewModel.errorMessage)
    }

    func testRefreshRequestedDuringInitialLoadRunsAgain() async throws {
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
        let todayRequestCount = LockedCounter()

        CaregiverTodayURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["content-type": "application/json"]
            )!
            if request.url?.path == "/api/patients/patient-1/today" {
                let count = todayRequestCount.increment()
                if count == 1 {
                    Thread.sleep(forTimeInterval: 0.05)
                }
            }
            if request.url?.path == "/api/patients/patient-1/inventory" {
                return (response, Data(#"{"data":{"patientId":"patient-1","medications":[]}}"#.utf8))
            }
            return (response, Data(#"{"data":[]}"#.utf8))
        }

        let viewModel = CaregiverTodayViewModel(apiClient: apiClient)
        viewModel.load(showLoading: true)
        await viewModel.refresh()

        XCTAssertEqual(todayRequestCount.value, 2)
        XCTAssertNil(viewModel.errorMessage)
    }
}

@MainActor
private final class CaregiverAnalyticsTrackingSpy: AnalyticsTracking {
    private(set) var events: [String] = []

    func logCoreActionStarted(_ action: AnalyticsCoreAction, mode: AnalyticsAppMode?) {
        events.append("started:\(action.rawValue):\(mode?.rawValue ?? "none")")
    }

    func logCoreActionCompleted(_ action: AnalyticsCoreAction, mode: AnalyticsAppMode?) {
        events.append("completed:\(action.rawValue):\(mode?.rawValue ?? "none")")
    }

    func logCoreActionFailed(
        _ action: AnalyticsCoreAction,
        reason: AnalyticsFailureReason,
        mode: AnalyticsAppMode?
    ) {
        events.append("failed:\(action.rawValue):\(mode?.rawValue ?? "none"):\(reason.rawValue)")
    }

    func logNotificationOpened(source: AnalyticsNotificationSource) {}
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

private final class LockedCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var storage = 0

    var value: Int {
        lock.withLock { storage }
    }

    func increment() -> Int {
        lock.withLock {
            storage += 1
            return storage
        }
    }
}
