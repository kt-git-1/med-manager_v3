import XCTest
@testable import MedicationApp

final class PatientTodayNextSlotSelectorTests: XCTestCase {
    func testSelectsCurrentEveningSlotOverPastNoonMissedSlot() {
        let now = tokyoDate(year: 2026, month: 6, day: 8, hour: 19, minute: 24)
        let noon = tokyoDate(year: 2026, month: 6, day: 8, hour: 13, minute: 0)
        let evening = tokyoDate(year: 2026, month: 6, day: 8, hour: 19, minute: 0)

        let selected = PatientTodayNextSlotSelector.selectSlot(
            from: [
                .init(
                    slot: .noon,
                    scheduledAt: noon,
                    remainingCount: 2,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: true
                ),
                .init(
                    slot: .evening,
                    scheduledAt: evening,
                    remainingCount: 1,
                    isWithinRecordingWindow: true,
                    hasRecordableInventory: true
                )
            ],
            now: now
        )

        XCTAssertEqual(selected, .evening)
    }

    func testSkipsPastSlotOutsideRecordingWindow() {
        let now = tokyoDate(year: 2026, month: 6, day: 8, hour: 19, minute: 24)
        let noon = tokyoDate(year: 2026, month: 6, day: 8, hour: 13, minute: 0)
        let bedtime = tokyoDate(year: 2026, month: 6, day: 8, hour: 22, minute: 0)

        let selected = PatientTodayNextSlotSelector.selectSlot(
            from: [
                .init(
                    slot: .noon,
                    scheduledAt: noon,
                    remainingCount: 2,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: true
                ),
                .init(
                    slot: .bedtime,
                    scheduledAt: bedtime,
                    remainingCount: 1,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: true
                )
            ],
            now: now
        )

        XCTAssertEqual(selected, .bedtime)
    }

    func testReturnsNilWhenOnlyPastClosedSlotsRemain() {
        let now = tokyoDate(year: 2026, month: 6, day: 8, hour: 19, minute: 24)
        let noon = tokyoDate(year: 2026, month: 6, day: 8, hour: 13, minute: 0)

        let selected = PatientTodayNextSlotSelector.selectSlot(
            from: [
                .init(
                    slot: .noon,
                    scheduledAt: noon,
                    remainingCount: 2,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: true
                )
            ],
            now: now
        )

        XCTAssertNil(selected)
    }

    func testSkipsSlotWhenOnlyOutOfStockDosesRemain() {
        let now = tokyoDate(year: 2026, month: 6, day: 8, hour: 11, minute: 30)
        let noon = tokyoDate(year: 2026, month: 6, day: 8, hour: 13, minute: 0)
        let evening = tokyoDate(year: 2026, month: 6, day: 8, hour: 19, minute: 0)

        let selected = PatientTodayNextSlotSelector.selectSlot(
            from: [
                .init(
                    slot: .noon,
                    scheduledAt: noon,
                    remainingCount: 1,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: false
                ),
                .init(
                    slot: .evening,
                    scheduledAt: evening,
                    remainingCount: 1,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: true
                )
            ],
            now: now
        )

        XCTAssertEqual(selected, .evening)
    }

    func testReturnsNilWhenRemainingDosesAreAllOutOfStock() {
        let now = tokyoDate(year: 2026, month: 6, day: 8, hour: 11, minute: 30)
        let noon = tokyoDate(year: 2026, month: 6, day: 8, hour: 13, minute: 0)

        let selected = PatientTodayNextSlotSelector.selectSlot(
            from: [
                .init(
                    slot: .noon,
                    scheduledAt: noon,
                    remainingCount: 1,
                    isWithinRecordingWindow: false,
                    hasRecordableInventory: false
                )
            ],
            now: now
        )

        XCTAssertNil(selected)
    }

    private func tokyoDate(year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        return calendar.date(from: DateComponents(
            timeZone: calendar.timeZone,
            year: year,
            month: month,
            day: day,
            hour: hour,
            minute: minute
        ))!
    }
}
