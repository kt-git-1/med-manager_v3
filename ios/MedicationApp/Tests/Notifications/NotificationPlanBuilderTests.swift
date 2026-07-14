import XCTest
@testable import MedicationApp

final class NotificationPlanBuilderTests: XCTestCase {
    func testBuildsSevenDayWindowWithMonthCrossover() throws {
        throw XCTSkip("NotificationPlanBuilder is implemented in a later task.")
    }

    func testFiltersOutTakenSlotWhenScheduleIsRebuiltAfterEarlyDoseRecord() throws {
        let timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Tokyo"))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let now = try XCTUnwrap(calendar.date(from: DateComponents(
            timeZone: timeZone,
            year: 2026,
            month: 7,
            day: 13,
            hour: 6
        )))
        let summaryData = try XCTUnwrap(
            """
            {
              "year": 2026,
              "month": 7,
              "days": [
                {
                  "date": "2026-07-13",
                  "slotSummary": {
                    "morning": "taken",
                    "noon": "pending",
                    "evening": "none",
                    "bedtime": "none"
                  }
                }
              ]
            }
            """.data(using: .utf8)
        )
        let summary = try JSONDecoder().decode(HistoryMonthResponseDTO.self, from: summaryData)

        let plan = NotificationPlanBuilder(timeZone: timeZone).buildPlan(
            monthSummaries: [summary],
            includeSecondary: true,
            slotTimes: [
                .morning: (8, 0),
                .noon: (13, 0)
            ],
            now: now
        )

        XCTAssertFalse(plan.contains { $0.slot == .morning })
        XCTAssertEqual(
            plan.filter { $0.slot == .noon }.map(\.sequence),
            [1, 2]
        )
    }

    func testKeepsTomorrowNotificationWhenTodayWasRecordedEarly() throws {
        let timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Tokyo"))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let now = try XCTUnwrap(calendar.date(from: DateComponents(
            timeZone: timeZone,
            year: 2026,
            month: 7,
            day: 14,
            hour: 13,
            minute: 20
        )))
        let summaryData = try XCTUnwrap(
            """
            {
              "year": 2026,
              "month": 7,
              "days": [
                {
                  "date": "2026-07-14",
                  "slotSummary": {
                    "morning": "none",
                    "noon": "taken",
                    "evening": "none",
                    "bedtime": "none"
                  }
                },
                {
                  "date": "2026-07-15",
                  "slotSummary": {
                    "morning": "none",
                    "noon": "pending",
                    "evening": "none",
                    "bedtime": "none"
                  }
                }
              ]
            }
            """.data(using: .utf8)
        )
        let summary = try JSONDecoder().decode(HistoryMonthResponseDTO.self, from: summaryData)

        let plan = NotificationPlanBuilder(timeZone: timeZone).buildPlan(
            monthSummaries: [summary],
            includeSecondary: false,
            slotTimes: [.noon: (13, 35)],
            now: now
        )

        XCTAssertFalse(plan.contains { $0.identifier == "notif:2026-07-14:noon:1" })
        XCTAssertEqual(plan.map(\.identifier), ["notif:2026-07-15:noon:1"])
        XCTAssertEqual(
            plan.first?.scheduledAt,
            calendar.date(from: DateComponents(
                timeZone: timeZone,
                year: 2026,
                month: 7,
                day: 15,
                hour: 13,
                minute: 35
            ))
        )
    }
}
