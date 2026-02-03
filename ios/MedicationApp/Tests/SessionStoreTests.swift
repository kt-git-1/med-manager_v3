import XCTest
@testable import MedicationApp

final class SessionStoreTests: XCTestCase {
    private let suiteName = "SessionStoreTests"
    private var userDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        userDefaults = UserDefaults(suiteName: suiteName)
        userDefaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        userDefaults = nil
        super.tearDown()
    }

    func testCurrentPatientIdPersistsAndRestores() {
        let store = SessionStore(userDefaults: userDefaults)
        XCTAssertNil(store.currentPatientId)

        store.setCurrentPatientId("patient-123")

        let restored = SessionStore(userDefaults: userDefaults)
        XCTAssertEqual(restored.currentPatientId, "patient-123")

        store.clearCurrentPatientId()

        let cleared = SessionStore(userDefaults: userDefaults)
        XCTAssertNil(cleared.currentPatientId)
    }

    func testClearCaregiverTokenClearsCurrentPatientId() {
        let store = SessionStore(userDefaults: userDefaults)
        store.setCurrentPatientId("patient-456")

        store.clearCaregiverToken()

        XCTAssertNil(store.currentPatientId)

        let restored = SessionStore(userDefaults: userDefaults)
        XCTAssertNil(restored.currentPatientId)
    }

    func testClearCurrentPatientIfMatches() {
        let store = SessionStore(userDefaults: userDefaults)
        store.setCurrentPatientId("patient-789")

        store.clearCurrentPatientIfMatches("patient-000")
        XCTAssertEqual(store.currentPatientId, "patient-789")

        store.clearCurrentPatientIfMatches("patient-789")
        XCTAssertNil(store.currentPatientId)
    }
}
