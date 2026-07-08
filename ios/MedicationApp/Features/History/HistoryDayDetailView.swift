import SwiftUI

struct HistoryDayDetailView: View {
    private static let historyTimeZone = AppConstants.defaultTimeZone
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = historyTimeZone
        calendar.locale = AppConstants.japaneseLocale
        return calendar
    }()
    private static let headerFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = HistoryDayDetailView.calendar
        formatter.timeZone = HistoryDayDetailView.historyTimeZone
        formatter.locale = AppConstants.japaneseLocale
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
    var highlightedSlot: NotificationSlot?
    var style: HistoryDayDetailStyle = .caregiver
    var onReturnToLogin: () -> Void = {}
    var onRecordMissedDose: (HistoryDayItemDTO) -> Void = { _ in }
    @State private var doseToBackfill: HistoryDayItemDTO?
    @State private var showingBackfillConfirmation = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(dayTitle)
                .font(style == .patient ? .title2.weight(.bold) : .headline)

            if viewModel.isLoadingDay && viewModel.day == nil {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.dayErrorMessage {
                if style == .caregiver {
                    CaregiverDataUnavailableView(
                        message: errorMessage,
                        onRetry: { retryLoad() },
                        onReturnToLogin: { onReturnToLogin() }
                    )
                    .accessibilityIdentifier("HistoryDayRetryButton")
                } else {
                    VStack(spacing: 12) {
                        ErrorStateView(message: errorMessage)
                        Button(NSLocalizedString("common.retry", comment: "Retry")) {
                            retryLoad()
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("HistoryDayRetryButton")
                    }
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
                                slotText: slotTitle(for: dose.slot),
                                slotColor: slotColor(for: dose.slot),
                                name: dose.medicationName,
                                dosage: dose.dosageText,
                                status: dose.effectiveStatus,
                                recordedByText: recordedByText(for: dose),
                                isHighlighted: isSlotHighlighted(dose.slot),
                                style: style,
                                canBackfill: style == .caregiver && dose.effectiveStatus == .missed,
                                onBackfill: {
                                    doseToBackfill = dose
                                    showingBackfillConfirmation = true
                                }
                            )
                        case .prn(let record):
                            HistoryDayPrnRow(
                                timeText: HistoryDayDetailView.timeFormatter.string(from: record.takenAt),
                                name: record.medicationName,
                                quantity: record.quantityTaken,
                                recordedByText: recordedByText(for: record.actorType),
                                style: style
                            )
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .alert(
            NSLocalizedString("history.day.backfill.confirm.title", comment: "Backfill confirm title"),
            isPresented: $showingBackfillConfirmation,
            presenting: doseToBackfill
        ) { dose in
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {
                doseToBackfill = nil
            }
            Button(NSLocalizedString("history.day.backfill.confirm.action", comment: "Backfill confirm action")) {
                onRecordMissedDose(dose)
                doseToBackfill = nil
            }
        } message: { dose in
            Text(
                String(
                    format: NSLocalizedString("history.day.backfill.confirm.message", comment: "Backfill confirm message"),
                    dose.medicationName
                )
            )
        }
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

    /// Maps a `HistorySlotDTO` to `NotificationSlot` to compare with the deep link highlight target.
    private func isSlotHighlighted(_ slot: HistorySlotDTO) -> Bool {
        guard let highlightedSlot else { return false }
        return slot.rawValue == highlightedSlot.rawValue
    }

    private func slotTitle(for slot: HistorySlotDTO) -> String {
        switch slot {
        case .morning:
            return NSLocalizedString("history.slot.morning", comment: "Morning slot")
        case .noon:
            return NSLocalizedString("history.slot.noon", comment: "Noon slot")
        case .evening:
            return NSLocalizedString("history.slot.evening", comment: "Evening slot")
        case .bedtime:
            return NSLocalizedString("history.slot.bedtime", comment: "Bedtime slot")
        }
    }

    private func slotColor(for slot: HistorySlotDTO) -> Color {
        switch slot {
        case .morning:
            return AppConstants.slotColor(for: .morning)
        case .noon:
            return AppConstants.slotColor(for: .noon)
        case .evening:
            return AppConstants.slotColor(for: .evening)
        case .bedtime:
            return AppConstants.slotColor(for: .bedtime)
        }
    }

    private func recordedByText(for dose: HistoryDayItemDTO) -> String? {
        guard dose.effectiveStatus == .taken, let recordedByType = dose.recordedByType else {
            return nil
        }
        return recordedByText(for: recordedByType)
    }

    private func recordedByText(for actorType: RecordedByTypeDTO) -> String {
        switch actorType {
        case .patient:
            return NSLocalizedString("history.recordedBy.patient", comment: "Patient recorded")
        case .caregiver:
            return NSLocalizedString("history.recordedBy.caregiver", comment: "Caregiver recorded")
        }
    }

    private func recordedByText(for actorType: PrnActorTypeDTO) -> String {
        switch actorType {
        case .patient:
            return NSLocalizedString("history.recordedBy.patient", comment: "Patient recorded")
        case .caregiver:
            return NSLocalizedString("history.recordedBy.caregiver", comment: "Caregiver recorded")
        }
    }
}

enum HistoryDayDetailStyle {
    case caregiver
    case patient
}

private struct HistoryDayRow: View {
    let timeText: String
    let slotText: String
    let slotColor: Color
    let name: String
    let dosage: String
    let status: HistoryDoseStatusDTO
    let recordedByText: String?
    var isHighlighted: Bool = false
    var style: HistoryDayDetailStyle = .caregiver
    var canBackfill = false
    var onBackfill: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(timeText)
                            .font(style == .patient ? .title3.weight(.bold) : .headline)
                        Text(slotText)
                            .font(.caption.weight(.bold))
                            .padding(.vertical, 3)
                            .padding(.horizontal, 8)
                            .background(slotColor.opacity(0.16))
                            .foregroundStyle(slotColor)
                            .clipShape(Capsule())
                    }
                    Text(medicationDisplayName)
                        .font(style == .patient ? .title3.weight(.bold) : .title3.weight(.semibold))
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                    if let recordedByText {
                        HistoryRecordedByLabel(text: recordedByText, style: style)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
                Spacer()
                statusMarker
            }

            if canBackfill {
                Button(action: onBackfill) {
                    Label(NSLocalizedString("history.day.backfill.button", comment: "Backfill button"), systemImage: "checkmark.circle.fill")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(CaregiverUI.teal, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(NSLocalizedString("history.day.backfill.button", comment: "Backfill button"))
            }
        }
        .padding(style == .patient ? 16 : 14)
        .background(rowBackground)
        .overlay {
            if style == .patient {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(rowStroke, lineWidth: 1)
            }
        }
        .shadow(color: style == .patient ? PatientUI.cardShadow : Color.clear, radius: 10, y: 4)
        .todaySlotHighlight(isHighlighted)
    }

    private var medicationDisplayName: String {
        let trimmed = dosage.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage") {
            return name
        }
        return "\(name) \(trimmed)"
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

    @ViewBuilder
    private var statusMarker: some View {
        if style == .caregiver {
            Image(systemName: statusSymbolName)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(statusForeground)
                .frame(width: 36, height: 36)
                .background(statusBackground, in: Circle())
                .accessibilityLabel(statusText)
        } else {
            Text(statusText)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .padding(.vertical, 4)
                .padding(.horizontal, 8)
                .background(statusBackground)
                .foregroundStyle(statusForeground)
                .clipShape(Capsule())
        }
    }

    private var statusSymbolName: String {
        switch status {
        case .pending:
            return "clock"
        case .taken:
            return "checkmark"
        case .missed:
            return "exclamationmark"
        }
    }

    private var statusForeground: Color {
        switch status {
        case .missed:
            return style == .patient ? PatientUI.red : Color.red
        case .taken:
            return style == .patient ? PatientUI.teal : Color.green
        case .pending:
            return Color.primary
        }
    }

    private var statusBackground: Color {
        switch status {
        case .missed:
            return (style == .patient ? PatientUI.red : Color.red).opacity(0.15)
        case .taken:
            return (style == .patient ? PatientUI.teal : Color.green).opacity(0.15)
        case .pending:
            return Color.primary.opacity(0.06)
        }
    }

    private var rowBackground: some ShapeStyle {
        if style == .patient {
            return AnyShapeStyle(PatientUI.cardBackground)
        }
        return AnyShapeStyle(.regularMaterial)
    }

    private var rowStroke: Color {
        status == .missed ? PatientUI.red.opacity(0.30) : PatientUI.cardStroke
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
    let quantity: Double
    let recordedByText: String
    var style: HistoryDayDetailStyle = .caregiver

    private var prnPrefix: String {
        NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(timeText)
                    .font(style == .patient ? .title3.weight(.bold) : .headline)
                Text("\(prnPrefix): \(name)")
                    .font(style == .patient ? .title3.weight(.bold) : .title3.weight(.semibold))
                HistoryRecordedByLabel(text: recordedByText, style: style)
            }
            Spacer()
            Text(prnPrefix)
                .font(.caption.weight(.semibold))
                .padding(.vertical, 4)
                .padding(.horizontal, 8)
                .background(prnColor.opacity(0.18))
                .foregroundStyle(prnColor)
                .clipShape(Capsule())
        }
        .padding(style == .patient ? 16 : 14)
        .background(rowBackground)
        .overlay {
            if style == .patient {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(PatientUI.indigo.opacity(0.26), lineWidth: 1)
            }
        }
        .shadow(color: style == .patient ? PatientUI.cardShadow : Color.clear, radius: 10, y: 4)
    }

    private var prnColor: Color {
        style == .patient ? PatientUI.indigo : Color.purple
    }

    private var rowBackground: some ShapeStyle {
        if style == .patient {
            return AnyShapeStyle(PatientUI.cardBackground)
        }
        return AnyShapeStyle(.regularMaterial)
    }
}

private struct HistoryRecordedByLabel: View {
    let text: String
    let style: HistoryDayDetailStyle

    var body: some View {
        Label(text, systemImage: "person.crop.circle.badge.checkmark")
            .font(.caption.weight(.semibold))
            .foregroundStyle(style == .patient ? PatientUI.teal : CaregiverUI.tealDark)
            .lineLimit(2)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityLabel(text)
    }
}
