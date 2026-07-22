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

    private func date(_ value: String) throws -> Date {
        try XCTUnwrap(ISO8601DateFormatter().date(from: value))
    }
}
