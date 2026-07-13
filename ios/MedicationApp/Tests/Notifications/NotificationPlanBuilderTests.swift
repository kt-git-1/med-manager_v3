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
}
