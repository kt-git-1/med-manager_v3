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

    @MainActor
    func testServerSlotTimesReplaceStalePatientScopedValues() {
        let defaults = makeDefaults("NotificationSettingsTests.serverSlotTimes")
        let store = NotificationPreferencesStore(defaults: defaults)
        store.switchPatient("patient-1")
        store.setSlotTime(.morning, hour: 8, minute: 0)
        store.setSlotTime(.noon, hour: 13, minute: 0)

        store.applyServerSlotTimes(
            PatientSlotTimesDTO(
                morning: "07:15",
                noon: "12:20",
                evening: "18:40",
                bedtime: "22:30"
            )
        )

        XCTAssertEqual(store.slotTime(for: .morning).hour, 7)
        XCTAssertEqual(store.slotTime(for: .morning).minute, 15)
        XCTAssertEqual(store.slotTime(for: .noon).hour, 12)
        XCTAssertEqual(store.slotTime(for: .noon).minute, 20)
        XCTAssertEqual(store.slotTime(for: .evening).hour, 18)
        XCTAssertEqual(store.slotTime(for: .evening).minute, 40)
        XCTAssertEqual(store.slotTime(for: .bedtime).hour, 22)
        XCTAssertEqual(store.slotTime(for: .bedtime).minute, 30)
    }

    func testSettingsToggleTriggersReschedule() throws {
        throw XCTSkip("Notification settings wiring is implemented in a later task.")
    }

    func testCustomEveningAndBedtimeTimesRemainSeparateSlots() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        let evening = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 8, day: 26, hour: 21)))
        let bedtime = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 8, day: 26, hour: 23)))
        let slotTimes: [NotificationSlot: (hour: Int, minute: Int)] = [
            .morning: (10, 0),
            .noon: (14, 0),
            .evening: (21, 0),
            .bedtime: (23, 0)
        ]

        XCTAssertEqual(NotificationSlot.from(date: evening, slotTimes: slotTimes), .evening)
        XCTAssertEqual(NotificationSlot.from(date: bedtime, slotTimes: slotTimes), .bedtime)
    }
}
