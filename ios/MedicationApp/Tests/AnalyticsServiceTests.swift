import XCTest
@testable import MedicationApp

@MainActor
final class AnalyticsServiceTests: XCTestCase {
    private var defaults: UserDefaults!
    private var suiteName: String!
    private var backend: AnalyticsBackendSpy!

    override func setUp() {
        super.setUp()
        suiteName = "AnalyticsServiceTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
        backend = AnalyticsBackendSpy()
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        backend = nil
        defaults = nil
        suiteName = nil
        super.tearDown()
    }

    func testCollectionIsDisabledAndConsentIsUndecidedByDefault() {
        let service = makeService()

        XCTAssertFalse(service.isEnabled)
        XCTAssertFalse(service.hasConsentDecision)

        service.configure()

        XCTAssertEqual(backend.collectionStates, [false])
        XCTAssertEqual(backend.userIDs.count, 1)
        XCTAssertNil(backend.userIDs[0])
        XCTAssertEqual(backend.userProperties.count, 1)
        XCTAssertEqual(backend.userProperties.values.first!, "false")
        XCTAssertTrue(backend.events.isEmpty)
    }

    func testEventsAreNotSentBeforeConfigurationOrConsent() {
        let service = makeService()

        service.logCoreActionCompleted(.doseRecorded, mode: .patient)
        service.configure()
        service.logCoreActionCompleted(.doseRecorded, mode: .patient)

        XCTAssertTrue(backend.events.isEmpty)
    }

    func testOptInPersistsConsentAndSendsOnlyFixedDoseParameters() {
        let service = makeService()
        service.configure()

        service.setCollectionEnabled(true)
        service.logCoreActionStarted(.doseRecorded, mode: .caregiver)
        service.logCoreActionCompleted(.doseRecorded, mode: .caregiver)

        XCTAssertTrue(service.isEnabled)
        XCTAssertTrue(service.hasConsentDecision)
        XCTAssertTrue(defaults.bool(forKey: "analytics.collection.enabled"))
        XCTAssertTrue(defaults.bool(forKey: "analytics.collection.consentDecided"))
        XCTAssertEqual(backend.collectionStates, [false, true])
        XCTAssertEqual(
            backend.events,
            [
                .init(
                    name: "core_action_started",
                    parameters: ["action_name": "dose_recorded", "mode": "caregiver"]
                ),
                .init(
                    name: "core_action_completed",
                    parameters: ["action_name": "dose_recorded", "mode": "caregiver"]
                )
            ]
        )
        assertNoSensitiveKeysOrValues(in: backend.events)
    }

    func testOptOutStopsFutureEventsAndResetsAnalyticsIdentity() {
        let service = makeService()
        service.configure()
        service.setCollectionEnabled(true)
        service.logCaregiverTabViewed(.history)

        service.setCollectionEnabled(false)
        service.logCaregiverTabViewed(.settings)

        XCTAssertFalse(service.isEnabled)
        XCTAssertTrue(service.hasConsentDecision)
        XCTAssertEqual(backend.events.count, 1)
        XCTAssertEqual(backend.resetCount, 1)
        XCTAssertEqual(backend.collectionStates.suffix(2), [true, false])
    }

    func testSuppressedEnvironmentAlwaysDisablesCollectionAndDropsEvents() {
        let service = makeService(environmentIsSuppressed: { true })

        service.setCollectionEnabled(true)
        service.configure()
        service.logCoreActionCompleted(.doseRecorded, mode: .patient)

        XCTAssertEqual(backend.collectionStates, [false, false])
        XCTAssertTrue(backend.events.isEmpty)
    }

    func testTutorialStepOnlyLogsSupportedBoundaryValues() {
        let service = makeEnabledService()

        service.logTutorialStepViewed(mode: .patient, step: 0)
        service.logTutorialStepViewed(mode: .patient, step: 1)
        service.logTutorialStepViewed(mode: .caregiver, step: 20)
        service.logTutorialStepViewed(mode: .caregiver, step: 21)

        XCTAssertEqual(
            backend.events,
            [
                .init(
                    name: "tutorial_step_viewed",
                    parameters: ["mode": "patient", "step": "1"]
                ),
                .init(
                    name: "tutorial_step_viewed",
                    parameters: ["mode": "caregiver", "step": "20"]
                )
            ]
        )
    }

    func testPublicEventSurfaceUsesOnlyPrivacySafeKeys() {
        let service = makeEnabledService()

        service.logScreenViewed(.caregiverLogin)
        service.logModeSelected(.caregiver)
        service.logAuth(.loginFailed, method: .email, reason: .invalidCredentials)
        service.logPatientLinkFailed(reason: .notFound)
        service.logPatientLinkCodeShareTapped()
        service.logNotificationPermissionResult(.denied, surface: .settings)
        service.logNotificationOpened(source: .remotePush)
        service.logTutorialFinished(mode: .patient, skipped: false)

        XCTAssertEqual(backend.events.count, 8)
        assertNoSensitiveKeysOrValues(in: backend.events)
        let allowedKeys: Set<String> = [
            "screen_name", "mode", "auth_method", "reason", "surface", "result", "source"
        ]
        XCTAssertTrue(
            backend.events.allSatisfy { Set($0.parameters.keys).isSubset(of: allowedKeys) }
        )
    }

    func testFailureReasonMapsErrorsWithoutSendingErrorText() {
        XCTAssertEqual(
            AnalyticsService.failureReason(for: APIError.network("patient@example.com")),
            .network
        )
        XCTAssertEqual(AnalyticsService.failureReason(for: APIError.notFound), .notFound)
        XCTAssertEqual(AnalyticsService.failureReason(for: APIError.unauthorized), .invalidCredentials)
        XCTAssertEqual(AnalyticsService.failureReason(for: APIError.forbidden), .invalidCredentials)
        XCTAssertEqual(AnalyticsService.failureReason(for: APIError.conflict), .credentialConflict)
        XCTAssertEqual(AnalyticsService.failureReason(for: NSError(domain: "patient@example.com", code: 1)), .unknown)
    }

    private func makeService(
        environmentIsSuppressed: @escaping () -> Bool = { false }
    ) -> AnalyticsService {
        AnalyticsService(
            defaults: defaults,
            backend: backend,
            environmentIsSuppressed: environmentIsSuppressed
        )
    }

    private func makeEnabledService() -> AnalyticsService {
        let service = makeService()
        service.configure()
        service.setCollectionEnabled(true)
        backend.events.removeAll()
        return service
    }

    private func assertNoSensitiveKeysOrValues(
        in events: [AnalyticsBackendSpy.Event],
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let forbiddenKeys: Set<String> = [
            "patient_id", "caregiver_id", "medication_id", "medication_name",
            "patient_name", "caregiver_name", "email_address", "auth_token",
            "dose_time", "inventory", "notification_body", "free_text"
        ]
        let keys = Set(events.flatMap { $0.parameters.keys })
        XCTAssertTrue(
            keys.isDisjoint(with: forbiddenKeys),
            "Sensitive keys: \(keys.intersection(forbiddenKeys))",
            file: file,
            line: line
        )

        let values = events.flatMap { $0.parameters.values }.map { $0.lowercased() }
        for value in values {
            XCTAssertFalse(value.contains("@"), "Email-like value: \(value)", file: file, line: line)
            XCTAssertFalse(value.contains("patient@example.com"), file: file, line: line)
            XCTAssertFalse(value.contains("カルボシステイン"), file: file, line: line)
        }
    }
}

@MainActor
private final class AnalyticsBackendSpy: AnalyticsBackend {
    struct Event: Equatable {
        let name: String
        let parameters: [String: String]
    }

    private(set) var collectionStates: [Bool] = []
    private(set) var userIDs: [String?] = []
    private(set) var userProperties: [String: String?] = [:]
    private(set) var resetCount = 0
    var events: [Event] = []

    func setCollectionEnabled(_ enabled: Bool) {
        collectionStates.append(enabled)
    }

    func setUserID(_ userID: String?) {
        userIDs.append(userID)
    }

    func setUserProperty(_ value: String?, forName name: String) {
        userProperties[name] = value
    }

    func resetData() {
        resetCount += 1
    }

    func logEvent(_ name: String, parameters: [String: String]) {
        events.append(.init(name: name, parameters: parameters))
    }
}
