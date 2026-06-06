import XCTest
@testable import MedicationApp

@MainActor
final class SessionStoreTests: XCTestCase {
    private let suiteName = "SessionStoreTests"
    private var userDefaults: UserDefaults!
    private var secureStorage: InMemorySessionKeychainStore!
    private var clock: TestClock!

    override func setUp() {
        super.setUp()
        userDefaults = UserDefaults(suiteName: suiteName)
        userDefaults.removePersistentDomain(forName: suiteName)
        secureStorage = InMemorySessionKeychainStore()
        clock = TestClock(now: Date(timeIntervalSince1970: 1_700_000_000))
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        secureStorage = nil
        clock = nil
        userDefaults = nil
        super.tearDown()
    }

    func testCurrentPatientIdPersistsAndRestores() {
        let store = makeStore()
        XCTAssertNil(store.currentPatientId)

        store.setCurrentPatientId("patient-123")

        let restored = makeStore()
        XCTAssertEqual(restored.currentPatientId, "patient-123")

        store.clearCurrentPatientId()

        let cleared = makeStore()
        XCTAssertNil(cleared.currentPatientId)
    }

    func testClearCaregiverTokenClearsCurrentPatientId() {
        let store = makeStore()
        store.setCurrentPatientId("patient-456")

        store.clearCaregiverToken()

        XCTAssertNil(store.currentPatientId)

        let restored = makeStore()
        XCTAssertNil(restored.currentPatientId)
    }

    func testClearCurrentPatientIfMatches() {
        let store = makeStore()
        store.setCurrentPatientId("patient-789")

        store.clearCurrentPatientIfMatches("patient-000")
        XCTAssertEqual(store.currentPatientId, "patient-789")

        store.clearCurrentPatientIfMatches("patient-789")
        XCTAssertNil(store.currentPatientId)
    }

    func testModePersistsAndRestores() {
        let store = makeStore()

        store.setMode(.patient)

        let restored = makeStore()
        XCTAssertEqual(restored.mode, .patient)
    }

    func testCaregiverTokenPersistsInSecureStorageAndRestoresBeforeExpiry() {
        let store = makeStore()
        store.setMode(.caregiver)
        store.saveCaregiverSession(
            SupabaseSession(
                accessToken: "caregiver-token",
                refreshToken: "refresh-token",
                expiresIn: 3600
            )
        )

        clock.now = clock.now.addingTimeInterval(29 * 24 * 60 * 60)
        let restored = makeStore()

        XCTAssertEqual(restored.mode, .caregiver)
        XCTAssertEqual(restored.caregiverToken, "caregiver-token")
        XCTAssertEqual(
            secureStorage.string(forKey: SessionStore.caregiverRefreshTokenStorageKey),
            "refresh-token"
        )
        XCTAssertNil(userDefaults.string(forKey: SessionStore.caregiverTokenStorageKey))
    }

    func testPatientTokenPersistsInSecureStorageAndRestoresBeforeExpiry() {
        let store = makeStore()
        store.setMode(.patient)
        store.savePatientToken("patient-token")

        clock.now = clock.now.addingTimeInterval(29 * 24 * 60 * 60)
        let restored = makeStore()

        XCTAssertEqual(restored.mode, .patient)
        XCTAssertEqual(restored.patientToken, "patient-token")
        XCTAssertNil(userDefaults.string(forKey: SessionStore.patientTokenStorageKey))
    }

    func testExpiredTokenIsClearedButModeRemains() {
        let store = makeStore()
        store.setMode(.caregiver)
        store.saveCaregiverToken("caregiver-token")

        clock.now = clock.now.addingTimeInterval(31 * 24 * 60 * 60)
        let restored = makeStore()

        XCTAssertEqual(restored.mode, .caregiver)
        XCTAssertNil(restored.caregiverToken)
        XCTAssertNil(secureStorage.string(forKey: SessionStore.caregiverTokenStorageKey))
    }

    func testResetModeDoesNotClearTokens() {
        let store = makeStore()
        store.setMode(.patient)
        store.savePatientToken("patient-token")

        store.resetMode()

        XCTAssertNil(store.mode)
        XCTAssertEqual(secureStorage.string(forKey: SessionStore.patientTokenStorageKey), "patient-token")

        let restored = makeStore()
        XCTAssertNil(restored.mode)
        XCTAssertEqual(restored.patientToken, "patient-token")
    }

    func testClearTokensRemoveSecureStorageAndMode() {
        let store = makeStore()
        store.setMode(.caregiver)
        store.saveCaregiverSession(
            SupabaseSession(
                accessToken: "caregiver-token",
                refreshToken: "refresh-token",
                expiresIn: 3600
            )
        )

        store.clearCaregiverToken()

        XCTAssertNil(store.mode)
        XCTAssertNil(store.caregiverToken)
        XCTAssertNil(secureStorage.string(forKey: SessionStore.caregiverTokenStorageKey))
        XCTAssertNil(secureStorage.string(forKey: SessionStore.caregiverRefreshTokenStorageKey))
        XCTAssertNil(secureStorage.string(forKey: SessionStore.caregiverExpiresAtStorageKey))
    }

    func testLegacyUserDefaultsTokensMigrateToSecureStorage() {
        userDefaults.set("legacy-caregiver-token", forKey: SessionStore.caregiverTokenStorageKey)
        userDefaults.set("legacy-patient-token", forKey: SessionStore.patientTokenStorageKey)

        let restored = makeStore()

        XCTAssertEqual(restored.caregiverToken, "legacy-caregiver-token")
        XCTAssertEqual(restored.patientToken, "legacy-patient-token")
        XCTAssertEqual(
            secureStorage.string(forKey: SessionStore.caregiverTokenStorageKey),
            "legacy-caregiver-token"
        )
        XCTAssertEqual(
            secureStorage.string(forKey: SessionStore.patientTokenStorageKey),
            "legacy-patient-token"
        )
        XCTAssertNil(userDefaults.string(forKey: SessionStore.caregiverTokenStorageKey))
        XCTAssertNil(userDefaults.string(forKey: SessionStore.patientTokenStorageKey))
    }

    private func makeStore() -> SessionStore {
        let clock = clock!
        return SessionStore(
            userDefaults: userDefaults,
            secureStorage: secureStorage,
            now: { clock.now }
        )
    }
}

private final class TestClock {
    var now: Date

    init(now: Date) {
        self.now = now
    }
}

private final class InMemorySessionKeychainStore: SessionSecureStorage {
    private var values: [String: String] = [:]

    func string(forKey key: String) -> String? {
        values[key]
    }

    func setString(_ value: String, forKey key: String) {
        values[key] = value
    }

    func removeString(forKey key: String) {
        values.removeValue(forKey: key)
    }
}
