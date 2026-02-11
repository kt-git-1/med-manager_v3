import Foundation
import Observation

// ---------------------------------------------------------------------------
// 011-pdf-export: ViewModel for period selection, validation, and generate flow
// ---------------------------------------------------------------------------

@Observable
@MainActor
final class PeriodPickerViewModel {

    // MARK: - Preset Enum

    enum ReportPeriodPreset: String, CaseIterable, Identifiable {
        case thisMonth
        case lastMonth
        case last30Days
        case last90Days
        case custom

        var id: String { rawValue }

        var displayName: String {
            switch self {
            case .thisMonth:  return NSLocalizedString("pdfexport.picker.thisMonth", comment: "This month")
            case .lastMonth:  return NSLocalizedString("pdfexport.picker.lastMonth", comment: "Last month")
            case .last30Days: return NSLocalizedString("pdfexport.picker.last30", comment: "Last 30 days")
            case .last90Days: return NSLocalizedString("pdfexport.picker.last90", comment: "Last 90 days")
            case .custom:     return NSLocalizedString("pdfexport.picker.custom", comment: "Custom")
            }
        }
    }

    // MARK: - State

    var selectedPreset: ReportPeriodPreset = .thisMonth
    var customFrom: Date
    var customTo: Date
    var isGenerating = false

    /// Override for testing. Set to a specific date to fix "today" for deterministic tests.
    var _todayOverrideForTesting: Date?

    // MARK: - Private

    private static let tokyoTimeZone = TimeZone(identifier: "Asia/Tokyo")!

    private let tokyoCalendar: Calendar

    // MARK: - Init

    init() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = PeriodPickerViewModel.tokyoTimeZone
        self.tokyoCalendar = cal
        let today = cal.startOfDay(for: Date())
        self.customFrom = today
        self.customTo = today
    }

    // MARK: - Today

    var todayTokyo: Date {
        if let override = _todayOverrideForTesting {
            return tokyoCalendar.startOfDay(for: override)
        }
        return tokyoCalendar.startOfDay(for: Date())
    }

    // MARK: - Effective Range (recomputed on each access)

    var effectiveFrom: Date {
        if selectedPreset == .custom {
            return tokyoCalendar.startOfDay(for: customFrom)
        }
        return Self.computePreset(selectedPreset, today: todayTokyo, calendar: tokyoCalendar).from
    }

    var effectiveTo: Date {
        if selectedPreset == .custom {
            return tokyoCalendar.startOfDay(for: customTo)
        }
        return Self.computePreset(selectedPreset, today: todayTokyo, calendar: tokyoCalendar).to
    }

    // MARK: - Validation

    var validationError: String? {
        let from = effectiveFrom
        let to = effectiveTo
        let today = todayTokyo

        if to > today {
            return NSLocalizedString("pdfexport.validation.toFuture", comment: "To date is future")
        }
        if from > to {
            return NSLocalizedString("pdfexport.validation.fromAfterTo", comment: "From after to")
        }
        let count = dayCount
        if count > 90 {
            return NSLocalizedString("pdfexport.validation.rangeExceeded", comment: "Range exceeded")
        }
        return nil
    }

    var isValid: Bool { validationError == nil }

    // MARK: - Computed Display Properties

    var dayCount: Int {
        let from = effectiveFrom
        let to = effectiveTo
        let days = tokyoCalendar.dateComponents([.day], from: from, to: to).day ?? 0
        return max(days + 1, 0)
    }

    var rangeText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy/MM/dd"
        formatter.timeZone = tokyoCalendar.timeZone
        return "\(formatter.string(from: effectiveFrom))〜\(formatter.string(from: effectiveTo))"
    }

    // MARK: - Preset Computation (pure static function)

    static func computePreset(
        _ preset: ReportPeriodPreset,
        today: Date,
        calendar: Calendar
    ) -> (from: Date, to: Date) {
        switch preset {
        case .thisMonth:
            let components = calendar.dateComponents([.year, .month], from: today)
            let from = calendar.date(from: components)!
            return (from, today)

        case .lastMonth:
            let thisMonthComponents = calendar.dateComponents([.year, .month], from: today)
            let thisMonthStart = calendar.date(from: thisMonthComponents)!
            let lastMonthStart = calendar.date(byAdding: .month, value: -1, to: thisMonthStart)!
            let lastMonthEnd = calendar.date(byAdding: .day, value: -1, to: thisMonthStart)!
            return (lastMonthStart, lastMonthEnd)

        case .last30Days:
            let from = calendar.date(byAdding: .day, value: -29, to: today)!
            return (from, today)

        case .last90Days:
            let from = calendar.date(byAdding: .day, value: -89, to: today)!
            return (from, today)

        case .custom:
            return (today, today)
        }
    }

    // MARK: - Generate & Share

    func generateAndShare(apiClient: APIClient, patientId: String) async throws -> URL {
        // Recompute at submission time (research decision 6)
        let (from, to): (Date, Date)
        if selectedPreset == .custom {
            from = effectiveFrom
            to = effectiveTo
        } else {
            let result = Self.computePreset(selectedPreset, today: todayTokyo, calendar: tokyoCalendar)
            from = result.from
            to = result.to
        }

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        dateFormatter.timeZone = tokyoCalendar.timeZone
        let fromStr = dateFormatter.string(from: from)
        let toStr = dateFormatter.string(from: to)

        let report = try await apiClient.fetchCaregiverHistoryReport(
            patientId: patientId,
            from: fromStr,
            to: toStr
        )
        let pdfURL = try PDFGenerator.generate(from: report)
        return pdfURL
    }
}
