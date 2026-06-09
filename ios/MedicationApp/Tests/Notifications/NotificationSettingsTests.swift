import XCTest
@testable import MedicationApp

final class NotificationSettingsTests: XCTestCase {
    private func makeDefaults(_ suiteName: String) -> UserDefaults {
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return defaults
    }

    @MainActor
    func testDefaultNoonSlotTimeIsOnePm() {
        let defaults = makeDefaults("NotificationSettingsTests.defaultNoon")
        let store = NotificationPreferencesStore(defaults: defaults)

        let noon = store.slotTime(for: .noon)

        XCTAssertEqual(noon.hour, 13)
        XCTAssertEqual(noon.minute, 0)
    }

    @MainActor
    func testLegacyNoonDefaultMigratesToOnePmOnce() {
        let defaults = makeDefaults("NotificationSettingsTests.legacyNoon")
        defaults.set("12:00", forKey: "notif.slotTime.noon")

        let store = NotificationPreferencesStore(defaults: defaults)
        let noon = store.slotTime(for: .noon)

        XCTAssertEqual(noon.hour, 13)
        XCTAssertEqual(noon.minute, 0)
        XCTAssertEqual(defaults.string(forKey: "notif.slotTime.noon"), "13:00")
    }

    @MainActor
    func testUserSelectedNoonAfterMigrationStaysAtNoon() {
        let defaults = makeDefaults("NotificationSettingsTests.userSelectedNoon")
        _ = NotificationPreferencesStore(defaults: defaults)
        defaults.set("12:00", forKey: "notif.slotTime.noon")

        let store = NotificationPreferencesStore(defaults: defaults)
        let noon = store.slotTime(for: .noon)

        XCTAssertEqual(noon.hour, 12)
        XCTAssertEqual(noon.minute, 0)
    }

    func testSettingsToggleTriggersReschedule() throws {
        throw XCTSkip("Notification settings wiring is implemented in a later task.")
    }
}
