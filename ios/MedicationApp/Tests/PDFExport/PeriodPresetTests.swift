import XCTest
@testable import MedicationApp

// ---------------------------------------------------------------------------
// T004: Unit tests for period preset calculations (Asia/Tokyo)
// ---------------------------------------------------------------------------

@MainActor
final class PeriodPresetTests: XCTestCase {

    // MARK: - Helpers

    private let tokyoTZ = TimeZone(identifier: "Asia/Tokyo")!

    private var tokyoCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tokyoTZ
        return cal
    }

    /// Creates a Date for the given YYYY-MM-DD in Asia/Tokyo timezone.
    private func tokyoDate(_ dateStr: String) -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = tokyoTZ
        return formatter.date(from: dateStr)!
    }

    /// Formats a Date as YYYY-MM-DD in Asia/Tokyo timezone.
    private func formatTokyo(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = tokyoTZ
        return formatter.string(from: date)
    }

    // MARK: - "今月" Preset

    func testThisMonthPresetOnFeb11() throws {
        // Given: today = 2026-02-11 in Asia/Tokyo
        let today = tokyoDate("2026-02-11")
        let cal = tokyoCalendar

        // When
        let result = PeriodPickerViewModel.computePreset(.thisMonth, today: today, calendar: cal)

        // Then
        XCTAssertEqual(formatTokyo(result.from), "2026-02-01")
        XCTAssertEqual(formatTokyo(result.to), "2026-02-11")
    }

    func testThisMonthPresetOnJanuary1st() throws {
        // Given: today = 2026-01-01 in Asia/Tokyo (single day)
        let today = tokyoDate("2026-01-01")
        let cal = tokyoCalendar

        // When
        let result = PeriodPickerViewModel.computePreset(.thisMonth, today: today, calendar: cal)

        // Then
        XCTAssertEqual(formatTokyo(result.from), "2026-01-01")
        XCTAssertEqual(formatTokyo(result.to), "2026-01-01")
    }

    // MARK: - "先月" Preset

    func testLastMonthPresetOnFeb11() throws {
        // Given: today = 2026-02-11 in Asia/Tokyo
        let today = tokyoDate("2026-02-11")
        let cal = tokyoCalendar

        // When
        let result = PeriodPickerViewModel.computePreset(.lastMonth, today: today, calendar: cal)

        // Then
        XCTAssertEqual(formatTokyo(result.from), "2026-01-01")
        XCTAssertEqual(formatTokyo(result.to), "2026-01-31")
    }

    func testLastMonthPresetOnJanuary2026_YearBoundary() throws {
        // Given: today = 2026-01-15 in Asia/Tokyo
        let today = tokyoDate("2026-01-15")
        let cal = tokyoCalendar

        // When
        let result = PeriodPickerViewModel.computePreset(.lastMonth, today: today, calendar: cal)

        // Then (year boundary: December 2025)
        XCTAssertEqual(formatTokyo(result.from), "2025-12-01")
        XCTAssertEqual(formatTokyo(result.to), "2025-12-31")
    }

    // MARK: - "直近30日" Preset

    func testLast30DaysPresetOnFeb11() throws {
        // Given: today = 2026-02-11 in Asia/Tokyo
        let today = tokyoDate("2026-02-11")
        let cal = tokyoCalendar

        // When
        let result = PeriodPickerViewModel.computePreset(.last30Days, today: today, calendar: cal)

        // Then: 30 days inclusive: 2026-01-13 to 2026-02-11
        XCTAssertEqual(formatTokyo(result.from), "2026-01-13")
        XCTAssertEqual(formatTokyo(result.to), "2026-02-11")
    }

    // MARK: - "直近90日" Preset

    func testLast90DaysPresetOnFeb11() throws {
        // Given: today = 2026-02-11 in Asia/Tokyo
        let today = tokyoDate("2026-02-11")
        let cal = tokyoCalendar

        // When
        let result = PeriodPickerViewModel.computePreset(.last90Days, today: today, calendar: cal)

        // Then: 90 days inclusive: 2025-11-14 to 2026-02-11
        XCTAssertEqual(formatTokyo(result.from), "2025-11-14")
        XCTAssertEqual(formatTokyo(result.to), "2026-02-11")
    }

    // MARK: - Adherence Rate Calculation

    func testAdherenceRateWithMixedStatuses() throws {
        // Given: TAKEN=8, MISSED=2, PENDING=5
        let report = makeReport(taken: 8, missed: 2, pending: 5)

        // When
        let rate = PDFGenerator.adherenceRate(from: report)

        // Then: TAKEN / (TAKEN + MISSED) = 8 / 10 = 80%
        XCTAssertEqual(rate, "80%")
    }

    func testAdherenceRateAllPendingShowsDash() throws {
        // Given: TAKEN=0, MISSED=0, PENDING=3
        let report = makeReport(taken: 0, missed: 0, pending: 3)

        // When
        let rate = PDFGenerator.adherenceRate(from: report)

        // Then: denominator is 0 → "—"
        XCTAssertEqual(rate, "—")
    }

    // MARK: - FeatureGate.pdfExport (existing from 008)

    func testPdfExportGateRequiresPremiumTier() throws {
        XCTAssertEqual(
            FeatureGate.pdfExport.requiredTier,
            .premium,
            "pdfExport gate should require premium tier"
        )
    }

    func testPdfExportUnlockedForPremium() throws {
        let result = FeatureGate.isUnlocked(.pdfExport, for: .premium)
        XCTAssertTrue(result, "pdfExport should be unlocked for premium users")
    }

    func testPdfExportLockedForFree() throws {
        let result = FeatureGate.isUnlocked(.pdfExport, for: .free)
        XCTAssertFalse(result, "pdfExport should be locked for free users")
    }

    func testPdfExportLockedForUnknown() throws {
        let result = FeatureGate.isUnlocked(.pdfExport, for: .unknown)
        XCTAssertFalse(result, "pdfExport should be locked when entitlement is unknown")
    }

    // MARK: - Test Helpers

    /// Builds a minimal HistoryReportResponseDTO with the given status counts in a single day.
    private func makeReport(taken: Int, missed: Int, pending: Int) -> HistoryReportResponseDTO {
        var items: [HistoryReportSlotItemDTO] = []
        for i in 0..<taken {
            items.append(HistoryReportSlotItemDTO(
                medicationId: "med-\(i)", name: "Med\(i)", dosageText: "5mg",
                doseCount: 1, status: "TAKEN", recordedAt: nil
            ))
        }
        for i in 0..<missed {
            items.append(HistoryReportSlotItemDTO(
                medicationId: "med-m\(i)", name: "MedM\(i)", dosageText: "5mg",
                doseCount: 1, status: "MISSED", recordedAt: nil
            ))
        }
        for i in 0..<pending {
            items.append(HistoryReportSlotItemDTO(
                medicationId: "med-p\(i)", name: "MedP\(i)", dosageText: "5mg",
                doseCount: 1, status: "PENDING", recordedAt: nil
            ))
        }
        let slots = HistoryReportSlotsDTO(
            morning: items, noon: [], evening: [], bedtime: []
        )
        let day = HistoryReportDayDTO(date: "2026-02-11", slots: slots, prn: [])
        return HistoryReportResponseDTO(
            patient: HistoryReportPatientDTO(id: "p1", displayName: "太郎"),
            range: HistoryReportRangeDTO(from: "2026-02-11", to: "2026-02-11", timezone: "Asia/Tokyo", days: 1),
            days: [day]
        )
    }
}
