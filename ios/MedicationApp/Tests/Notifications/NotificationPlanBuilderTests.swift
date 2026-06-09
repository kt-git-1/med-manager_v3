import XCTest
@testable import MedicationApp

final class NotificationPlanBuilderTests: XCTestCase {
    func testUsesCustomBedtimeSlotTimeWhenBuildingPlan() throws {
        let month = try makeMonthResponse(
            date: "2026-06-09",
            bedtime: "pending"
        )
        let now = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-06-09T00:00:00+09:00"))
        let builder = NotificationPlanBuilder()

        let plan = builder.buildPlan(
            monthSummaries: [month],
            includeSecondary: false,
            slotTimes: [.bedtime: (hour: 23, minute: 0)],
            now: now
        )

        XCTAssertEqual(plan.count, 1)
        XCTAssertEqual(plan.first?.slot, .bedtime)
        XCTAssertEqual(plan.first?.scheduledAt, ISO8601DateFormatter().date(from: "2026-06-09T23:00:00+09:00"))
    }

    func testBuildsSevenDayWindowWithMonthCrossover() throws {
        throw XCTSkip("NotificationPlanBuilder is implemented in a later task.")
    }

    func testFiltersPendingSlotsOnly() throws {
        throw XCTSkip("NotificationPlanBuilder is implemented in a later task.")
    }

    private func makeMonthResponse(date: String, bedtime: String) throws -> HistoryMonthResponseDTO {
        let data = """
        {
          "year": 2026,
          "month": 6,
          "days": [
            {
              "date": "\(date)",
              "slotSummary": {
                "morning": "none",
                "noon": "none",
                "evening": "none",
                "bedtime": "\(bedtime)"
              }
            }
          ]
        }
        """.data(using: .utf8)!
        return try JSONDecoder().decode(HistoryMonthResponseDTO.self, from: data)
    }
}
