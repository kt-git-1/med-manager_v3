import XCTest
@testable import MedicationApp

// ---------------------------------------------------------------------------
// T005: Unit tests for range validation logic (client-side)
// ---------------------------------------------------------------------------

@MainActor
final class RangeValidationTests: XCTestCase {

    // MARK: - Helpers

    private let tokyoTZ = TimeZone(identifier: "Asia/Tokyo")!

    /// Creates a Date for the given YYYY-MM-DD in Asia/Tokyo timezone.
    private func tokyoDate(_ dateStr: String) -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = tokyoTZ
        return formatter.date(from: dateStr)!
    }

    /// Creates a ViewModel with a fixed "today" for deterministic testing.
    private func makeViewModel(today: String) -> PeriodPickerViewModel {
        let vm = PeriodPickerViewModel()
        vm._todayOverrideForTesting = tokyoDate(today)
        vm.selectedPreset = .custom
        return vm
    }

    // MARK: - Invalid: to > todayTokyo

    func testToAfterTodayIsInvalid() throws {
        // Given: today = 2026-02-11, to = 2026-02-12 (tomorrow)
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2026-02-11")
        vm.customTo = tokyoDate("2026-02-12")

        // When/Then
        XCTAssertFalse(vm.isValid)
        XCTAssertEqual(vm.validationError, "終了日は今日以前を指定してください")
    }

    // MARK: - Invalid: from > to

    func testFromAfterToIsInvalid() throws {
        // Given: from = 2026-02-10, to = 2026-02-05
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2026-02-10")
        vm.customTo = tokyoDate("2026-02-05")

        // When/Then
        XCTAssertFalse(vm.isValid)
        XCTAssertEqual(vm.validationError, "開始日は終了日以前を指定してください")
    }

    // MARK: - Invalid: range > 90 days

    func testRangeExceeds90DaysIsInvalid() throws {
        // Given: from = 2025-11-12, to = 2026-02-11 → 92 days (> 90)
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2025-11-12")
        vm.customTo = tokyoDate("2026-02-11")

        // When/Then
        XCTAssertFalse(vm.isValid)
        XCTAssertEqual(vm.validationError, "期間は90日以内で指定してください")
    }

    // MARK: - Valid: exact 90 days

    func testExactly90DaysIsValid() throws {
        // Given: from = 2025-11-14, to = 2026-02-11 → exactly 90 days
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2025-11-14")
        vm.customTo = tokyoDate("2026-02-11")

        // When/Then
        XCTAssertTrue(vm.isValid, "90 days should be valid (max allowed)")
        XCTAssertNil(vm.validationError)
        XCTAssertEqual(vm.dayCount, 90)
    }

    // MARK: - Valid: single day (from == to)

    func testSingleDayRangeIsValid() throws {
        // Given: from = 2026-02-11, to = 2026-02-11 → 1 day
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2026-02-11")
        vm.customTo = tokyoDate("2026-02-11")

        // When/Then
        XCTAssertTrue(vm.isValid)
        XCTAssertNil(vm.validationError)
        XCTAssertEqual(vm.dayCount, 1)
    }

    // MARK: - Valid: to = todayTokyo

    func testToEqualsTodayIsValid() throws {
        // Given: today = 2026-02-11, to = today, from = today - 10 days
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2026-02-01")
        vm.customTo = tokyoDate("2026-02-11")

        // When/Then
        XCTAssertTrue(vm.isValid)
        XCTAssertNil(vm.validationError)
    }

    // MARK: - Computed properties for valid range

    func testValidRangeComputedProperties() throws {
        // Given: from = 2026-01-01, to = 2026-01-30 (30 days)
        let vm = makeViewModel(today: "2026-02-11")
        vm.customFrom = tokyoDate("2026-01-01")
        vm.customTo = tokyoDate("2026-01-30")

        // When/Then
        XCTAssertTrue(vm.isValid)
        XCTAssertNil(vm.validationError)
        XCTAssertEqual(vm.dayCount, 30)
        XCTAssertEqual(vm.rangeText, "2026/01/01〜2026/01/30")
    }
}
