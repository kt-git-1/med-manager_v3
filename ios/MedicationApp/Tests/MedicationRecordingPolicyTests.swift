import XCTest
@testable import MedicationApp

final class MedicationRecordingPolicyTests: XCTestCase {
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        return calendar
    }

    func testDoseBecomesLateAtOneHourButRemainsRecordable() throws {
        let scheduledAt = try date("2026-07-22T12:30:00+09:00")
        let now = try date("2026-07-22T13:30:00+09:00")

        XCTAssertTrue(MedicationRecordingPolicy.isLate(scheduledAt: scheduledAt, takenAt: now))
        XCTAssertTrue(MedicationRecordingPolicy.isRecordable(scheduledAt: scheduledAt, now: now, calendar: calendar))
    }

    func testDoseIsRecordableImmediatelyBeforeNextDayFourAM() throws {
        let scheduledAt = try date("2026-07-22T23:00:00+09:00")
        let now = try date("2026-07-23T03:59:59+09:00")

        XCTAssertTrue(MedicationRecordingPolicy.isRecordable(scheduledAt: scheduledAt, now: now, calendar: calendar))
    }

    func testDoseIsNoLongerRecordableAtNextDayFourAM() throws {
        let scheduledAt = try date("2026-07-22T23:00:00+09:00")
        let now = try date("2026-07-23T04:00:00+09:00")

        XCTAssertFalse(MedicationRecordingPolicy.isRecordable(scheduledAt: scheduledAt, now: now, calendar: calendar))
    }

    func testDelayTextIncludesHoursAndMinutes() {
        XCTAssertEqual(MedicationRecordingPolicy.delayText(for: 77 * 60), "1時間17分遅れ")
    }

    func testHistorySlotGroupUsesActualTimeAndDetectsLateRecord() throws {
        let scheduledAt = try date("2026-07-22T12:30:00+09:00")
        let takenAt = try date("2026-07-22T17:51:00+09:00")
        let group = HistoryDaySlotGroup(
            slot: .noon,
            doses: [historyDose(id: "med-1", scheduledAt: scheduledAt, takenAt: takenAt, status: .taken, actor: .patient)]
        )

        XCTAssertEqual(group.status, .taken)
        XCTAssertEqual(group.takenAt, takenAt)
        XCTAssertEqual(group.maximumDelay, 5 * 60 * 60 + 21 * 60, accuracy: 0.1)
        XCTAssertTrue(group.isLate)
        XCTAssertEqual(group.recordedByTypes, [.patient])
    }

    func testHistorySlotGroupKeepsMissedSlotRecordableByCaregiverUI() throws {
        let scheduledAt = try date("2026-07-22T08:00:00+09:00")
        let group = HistoryDaySlotGroup(
            slot: .morning,
            doses: [historyDose(id: "med-1", scheduledAt: scheduledAt, takenAt: nil, status: .missed, actor: nil)]
        )

        XCTAssertEqual(group.status, .missed)
        XCTAssertNil(group.takenAt)
        XCTAssertFalse(group.isLate)
    }

    func testHistorySlotGroupPreservesActualTimeForPartiallyRecordedSlot() throws {
        let scheduledAt = try date("2026-07-22T12:30:00+09:00")
        let takenAt = try date("2026-07-22T17:51:00+09:00")
        let group = HistoryDaySlotGroup(
            slot: .noon,
            doses: [
                historyDose(id: "med-1", scheduledAt: scheduledAt, takenAt: takenAt, status: .taken, actor: .patient),
                historyDose(id: "med-2", scheduledAt: scheduledAt, takenAt: nil, status: .missed, actor: nil)
            ]
        )

        XCTAssertTrue(group.isPartiallyTaken)
        XCTAssertEqual(group.takenAt, takenAt)
        XCTAssertTrue(group.isLate)
        XCTAssertEqual(group.recordedByTypes, [.patient])
    }

    private func historyDose(
        id: String,
        scheduledAt: Date,
        takenAt: Date?,
        status: HistoryDoseStatusDTO,
        actor: RecordedByTypeDTO?
    ) -> HistoryDayItemDTO {
        HistoryDayItemDTO(
            medicationId: id,
            medicationName: "テスト薬",
            dosageText: "1錠",
            doseCountPerIntake: 1,
            scheduledAt: scheduledAt,
            takenAt: takenAt,
            slot: .noon,
            effectiveStatus: status,
            recordedByType: actor,
            cancelledAt: nil,
            cancelledByType: nil,
            cancelledRecordTakenAt: nil,
            inventoryRestored: nil
        )
    }

    private func date(_ value: String) throws -> Date {
        try XCTUnwrap(ISO8601DateFormatter().date(from: value))
    }
}
