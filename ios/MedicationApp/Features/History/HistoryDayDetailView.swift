import SwiftUI

struct HistoryDayDetailView: View {
    private static let historyTimeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = historyTimeZone
        calendar.locale = Locale(identifier: "ja_JP")
        return calendar
    }()
    private static let headerFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryDayDetailView.calendar
        formatter.timeZone = HistoryDayDetailView.historyTimeZone
        formatter.dateFormat = "M月d日 (E)"
        return formatter
    }()
    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryDayDetailView.calendar
        formatter.timeZone = HistoryDayDetailView.historyTimeZone
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    @ObservedObject var viewModel: HistoryViewModel
    let selectedDate: Date?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(dayTitle)
                .font(.headline)

            if viewModel.isLoadingDay && viewModel.day == nil {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.dayErrorMessage {
                VStack(spacing: 12) {
                    ErrorStateView(message: errorMessage)
                    Button(NSLocalizedString("common.retry", comment: "Retry")) {
                        retryLoad()
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("HistoryDayRetryButton")
                }
            } else if timelineItems.isEmpty {
                EmptyStateView(
                    title: NSLocalizedString("history.day.empty.title", comment: "History day empty title"),
                    message: NSLocalizedString("history.day.empty.message", comment: "History day empty message")
                )
                .accessibilityIdentifier("HistoryDayEmptyState")
            } else {
                VStack(spacing: 12) {
                    ForEach(timelineItems) { item in
                        switch item {
                        case .scheduled(let dose):
                            HistoryDayRow(
                                timeText: HistoryDayDetailView.timeFormatter.string(from: dose.scheduledAt),
                                name: dose.medicationName,
                                dosage: dose.dosageText,
                                status: dose.effectiveStatus
                            )
                        case .prn(let record):
                            HistoryDayPrnRow(
                                timeText: HistoryDayDetailView.timeFormatter.string(from: record.takenAt),
                                name: record.medicationName,
                                quantity: record.quantityTaken
                            )
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("HistoryDayDetailView")
    }

    private var dayTitle: String {
        guard let selectedDate else {
            return NSLocalizedString("history.day.title", comment: "History day title")
        }
        return HistoryDayDetailView.headerFormatter.string(from: selectedDate)
    }

    private var timelineItems: [HistoryTimelineItem] {
        let doses = viewModel.day?.doses ?? []
        let prnItems = viewModel.day?.prnItems ?? []
        var items = doses.map { HistoryTimelineItem.scheduled($0) }
        items.append(contentsOf: prnItems.map { HistoryTimelineItem.prn($0) })
        return items.sorted { left, right in
            if left.date != right.date {
                return left.date < right.date
            }
            return left.sortName.localizedCompare(right.sortName) == .orderedAscending
        }
    }

    private func retryLoad() {
        guard let selectedDate else { return }
        viewModel.loadDay(date: HistoryDayDetailView.dateKey(for: selectedDate))
    }

    private static func dateKey(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = HistoryDayDetailView.calendar
        formatter.timeZone = HistoryDayDetailView.historyTimeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}

private struct HistoryDayRow: View {
    let timeText: String
    let name: String
    let dosage: String
    let status: HistoryDoseStatusDTO

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(timeText)
                    .font(.headline)
                Text(name)
                    .font(.title3.weight(.semibold))
                Text(dosage)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(statusText)
                .font(.caption.weight(.semibold))
                .padding(.vertical, 4)
                .padding(.horizontal, 8)
                .background(statusBackground)
                .foregroundStyle(statusForeground)
                .clipShape(Capsule())
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .shadow(color: Color.black.opacity(0.05), radius: 6, y: 2)
    }

    private var statusText: String {
        switch status {
        case .pending:
            return NSLocalizedString("history.status.pending", comment: "History pending")
        case .taken:
            return NSLocalizedString("history.status.taken", comment: "History taken")
        case .missed:
            return NSLocalizedString("history.status.missed", comment: "History missed")
        }
    }

    private var statusForeground: Color {
        switch status {
        case .missed:
            return Color.red
        case .taken:
            return Color.green
        case .pending:
            return Color.primary
        }
    }

    private var statusBackground: Color {
        switch status {
        case .missed:
            return Color.red.opacity(0.15)
        case .taken:
            return Color.green.opacity(0.12)
        case .pending:
            return Color(.secondarySystemBackground)
        }
    }
}

private enum HistoryTimelineItem: Identifiable {
    case scheduled(HistoryDayItemDTO)
    case prn(PrnHistoryItemDTO)

    var id: String {
        switch self {
        case .scheduled(let dose):
            return "\(dose.medicationId)-\(dose.scheduledAt.timeIntervalSince1970)"
        case .prn(let record):
            return "\(record.medicationId)-\(record.takenAt.timeIntervalSince1970)"
        }
    }

    var date: Date {
        switch self {
        case .scheduled(let dose):
            return dose.scheduledAt
        case .prn(let record):
            return record.takenAt
        }
    }

    var sortName: String {
        switch self {
        case .scheduled(let dose):
            return dose.medicationName
        case .prn(let record):
            return record.medicationName
        }
    }
}

private struct HistoryDayPrnRow: View {
    let timeText: String
    let name: String
    let quantity: Int

    private var prnPrefix: String {
        NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(timeText)
                    .font(.headline)
                Text("\(prnPrefix): \(name)")
                    .font(.title3.weight(.semibold))
                Text("1回\(quantity)錠")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .shadow(color: Color.black.opacity(0.05), radius: 6, y: 2)
    }
}
