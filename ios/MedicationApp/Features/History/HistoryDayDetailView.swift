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

            if viewModel.isLoading && viewModel.day == nil {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else if sortedDoses.isEmpty {
                EmptyStateView(
                    title: NSLocalizedString("history.day.empty.title", comment: "History day empty title"),
                    message: NSLocalizedString("history.day.empty.message", comment: "History day empty message")
                )
            } else {
                VStack(spacing: 12) {
                    ForEach(Array(sortedDoses.enumerated()), id: \.offset) { _, dose in
                        HistoryDayRow(
                            timeText: HistoryDayDetailView.timeFormatter.string(from: dose.scheduledAt),
                            name: dose.medicationName,
                            dosage: dose.dosageText,
                            status: dose.effectiveStatus
                        )
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

    private var sortedDoses: [HistoryDayItemDTO] {
        let doses = viewModel.day?.doses ?? []
        return doses.sorted { left, right in
            let slotDiff = slotOrderIndex(left.slot) - slotOrderIndex(right.slot)
            if slotDiff != 0 {
                return slotDiff < 0
            }
            return left.medicationName.localizedCompare(right.medicationName) == .orderedAscending
        }
    }

    private func slotOrderIndex(_ slot: HistorySlotDTO) -> Int {
        switch slot {
        case .morning:
            return 0
        case .noon:
            return 1
        case .evening:
            return 2
        case .bedtime:
            return 3
        }
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
