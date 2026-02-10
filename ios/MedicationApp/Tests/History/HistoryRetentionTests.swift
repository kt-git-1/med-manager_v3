import XCTest
@testable import MedicationApp

// ---------------------------------------------------------------------------
// T004: Unit tests for cutoff date calculation, error parsing, and banner text
// ---------------------------------------------------------------------------

@MainActor
final class HistoryRetentionTests: XCTestCase {

    // MARK: - Cutoff Date Calculation

    func testHistoryCutoffDateReturnsTodayMinus29Days() throws {
        // Given: Today's date in Asia/Tokyo timezone
        let cutoffDate = FeatureGate.historyCutoffDate()

        // Then: Returns a string in YYYY-MM-DD format
        XCTAssertEqual(cutoffDate.count, 10, "Cutoff date should be in YYYY-MM-DD format")
        XCTAssertTrue(cutoffDate.contains("-"), "Cutoff date should contain hyphens")

        // Verify it matches todayTokyo - 29 days
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        let todayTokyo = calendar.startOfDay(for: Date())
        let expected = calendar.date(byAdding: .day, value: -29, to: todayTokyo)!
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        XCTAssertEqual(cutoffDate, formatter.string(from: expected))
    }

    func testCutoffDateAtMidnightJSTBoundary() throws {
        // Given: The cutoff date calculation uses Asia/Tokyo timezone
        let cutoffDate = FeatureGate.historyCutoffDate()

        // Then: The cutoff date is a valid YYYY-MM-DD string
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        let parsed = formatter.date(from: cutoffDate)
        XCTAssertNotNil(parsed, "Cutoff date should be a valid date string")
    }

    // MARK: - Error Parsing

    func testHistoryRetentionLimitErrorCase() throws {
        // Given: An APIError.historyRetentionLimit case
        let error = APIError.historyRetentionLimit(cutoffDate: "2026-01-12", retentionDays: 30)

        // Then: It has the correct associated values
        if case .historyRetentionLimit(let cutoffDate, let retentionDays) = error {
            XCTAssertEqual(cutoffDate, "2026-01-12")
            XCTAssertEqual(retentionDays, 30)
        } else {
            XCTFail("Expected historyRetentionLimit case")
        }
    }

    func testRetentionErrorDescription() throws {
        // Given: An APIError.historyRetentionLimit
        let error = APIError.historyRetentionLimit(cutoffDate: "2026-01-12", retentionDays: 30)

        // Then: The error description is the localized retention message
        XCTAssertNotNil(error.errorDescription)
        XCTAssertFalse(error.errorDescription!.isEmpty)
    }

    func testRetentionErrorIsNotForbidden() throws {
        // Given: An APIError.historyRetentionLimit
        let error = APIError.historyRetentionLimit(cutoffDate: "2026-01-12", retentionDays: 30)

        // Then: It is NOT the same as .forbidden
        if case .forbidden = error {
            XCTFail("historyRetentionLimit should not be matched as .forbidden")
        }
    }

    // MARK: - Banner Text

    func testFreeBannerTextContainsCutoffDate() throws {
        // Given: The free banner format string
        let cutoffDate = FeatureGate.historyCutoffDate()
        let bannerText = String(
            format: NSLocalizedString("history.retention.banner.free", comment: ""),
            cutoffDate
        )

        // Then: It contains the cutoff date
        XCTAssertTrue(bannerText.contains(cutoffDate), "Free banner should contain the cutoff date")
        XCTAssertTrue(bannerText.contains("30日"), "Free banner should mention 30 days")
    }

    func testPremiumBannerTextShowsFullRange() throws {
        // Given: The premium banner string
        let bannerText = NSLocalizedString("history.retention.banner.premium", comment: "")

        // Then: It shows full range text
        XCTAssertEqual(bannerText, "全期間表示中")
    }

    // MARK: - Constants

    func testRetentionDaysFreeEquals30() throws {
        XCTAssertEqual(FeatureGate.retentionDaysFree, 30)
    }

    func testExtendedHistoryGateRequiresPremium() throws {
        // This test can run now — FeatureGate.extendedHistory already exists (008).
        XCTAssertEqual(
            FeatureGate.extendedHistory.requiredTier,
            .premium,
            "extendedHistory gate should require premium tier"
        )
    }

    func testExtendedHistoryLockedWhenFree() throws {
        // This test can run now — FeatureGate.isUnlocked already exists (008).
        let result = FeatureGate.isUnlocked(.extendedHistory, for: .free)
        XCTAssertFalse(result, "extendedHistory should be locked for free users")
    }

    func testExtendedHistoryUnlockedWhenPremium() throws {
        // This test can run now — FeatureGate.isUnlocked already exists (008).
        let result = FeatureGate.isUnlocked(.extendedHistory, for: .premium)
        XCTAssertTrue(result, "extendedHistory should be unlocked for premium users")
    }
}
