import SwiftUI

struct HistoryDayDetailView: View {
    private static let prnGroupKey = "prn"
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
    fileprivate static let timeFormatter: DateFormatter = {
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
    var onCancelDose: (HistoryDayItemDTO) -> Void = { _ in }
    @State private var pendingAction: HistoryDoseAction?
    @State private var expandedSlotKeys: Set<String> = []

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
                timelineContent
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .alert(item: $pendingAction) { action in
            let dose = action.dose
            return Alert(
                title: Text(action.confirmationTitle),
                message: Text(confirmationMessage(for: action)),
                primaryButton: .cancel(Text(NSLocalizedString("common.cancel", comment: "Cancel"))),
                secondaryButton: .default(Text(action.confirmationButtonTitle)) {
                    switch action {
                    case .backfill:
                        onRecordMissedDose(dose)
                    case .cancel:
                        onCancelDose(dose)
                    }
                }
            )
        }
        .onAppear {
            resetExpandedGroups()
        }
        .onChange(of: viewModel.day?.date) { _, _ in
            resetExpandedGroups()
        }
    }

    @ViewBuilder
    private var timelineContent: some View {
        if style == .caregiver {
            VStack(spacing: 10) {
                ForEach(slotGroups) { group in
                    CaregiverHistorySlotGroupView(
                        group: group,
                        isExpanded: expandedSlotKeys.contains(group.slot.rawValue),
                        isHighlighted: isSlotHighlighted(group.slot),
                        slotTitle: slotTitle(for: group.slot),
                        slotColor: slotColor(for: group.slot),
                        scheduledTimeText: HistoryDayDetailView.timeFormatter.string(from: group.scheduledAt),
                        takenTimeText: group.takenAt.map { HistoryDayDetailView.timeFormatter.string(from: $0) },
                        recordedByText: recordedByText(for: group),
                        delayText: delayText(for: group),
                        onToggle: { toggleSlot(group.slot) },
                        onBackfill: { dose in
                            pendingAction = .backfill(dose)
                        },
                        onCancel: { dose in pendingAction = .cancel(dose) }
                    )
                }

                if !prnItems.isEmpty {
                    CaregiverHistoryPrnGroupView(
                        records: prnItems,
                        isExpanded: expandedSlotKeys.contains(Self.prnGroupKey),
                        timeText: { HistoryDayDetailView.timeFormatter.string(from: $0.takenAt) },
                        recordedByText: { recordedByText(for: $0.actorType) },
                        onToggle: { toggleGroup(Self.prnGroupKey) }
                    )
                }
            }
        } else {
            VStack(spacing: 12) {
                ForEach(timelineItems) { item in
                    switch item {
                    case .scheduled(let dose):
                        HistoryDayRow(
                            scheduledTimeText: HistoryDayDetailView.timeFormatter.string(from: dose.scheduledAt),
                            takenTimeText: dose.takenAt.map { HistoryDayDetailView.timeFormatter.string(from: $0) },
                            isLate: dose.takenAt.map { $0.timeIntervalSince(dose.scheduledAt) >= 60 * 60 } ?? false,
                            slotText: slotTitle(for: dose.slot),
                            slotColor: slotColor(for: dose.slot),
                            name: dose.medicationName,
                            dosage: dose.dosageText,
                            status: dose.effectiveStatus,
                            recordedByText: recordedByText(for: dose),
                            isHighlighted: isSlotHighlighted(dose.slot),
                            style: style,
                            canBackfill: false
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

    private var slotGroups: [HistoryDaySlotGroup] {
        let doses = viewModel.day?.doses ?? []
        return HistorySlotDTO.allCases.compactMap { slot in
            let slotDoses = doses.filter { $0.slot == slot }
            return slotDoses.isEmpty ? nil : HistoryDaySlotGroup(slot: slot, doses: slotDoses)
        }
    }

    private var prnItems: [PrnHistoryItemDTO] {
        (viewModel.day?.prnItems ?? []).sorted {
            if $0.takenAt != $1.takenAt { return $0.takenAt < $1.takenAt }
            return $0.medicationName.localizedCompare($1.medicationName) == .orderedAscending
        }
    }

    private func toggleSlot(_ slot: HistorySlotDTO) {
        toggleGroup(slot.rawValue)
    }

    private func toggleGroup(_ key: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            if expandedSlotKeys.contains(key) {
                expandedSlotKeys.remove(key)
            } else {
                expandedSlotKeys.insert(key)
            }
        }
    }

    private func resetExpandedGroups() {
        var expandedKeys = Set(
            slotGroups
                .filter { $0.status == .taken || isSlotHighlighted($0.slot) }
                .map { $0.slot.rawValue }
        )
        if !prnItems.isEmpty {
            expandedKeys.insert(Self.prnGroupKey)
        }
        expandedSlotKeys = expandedKeys
    }

    private func delayText(for group: HistoryDaySlotGroup) -> String? {
        guard group.isLate else { return nil }
        let totalMinutes = Int(group.maximumDelay / 60)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours > 0, minutes > 0 {
            return String(format: NSLocalizedString("history.delay.hoursMinutes", comment: "Delay hours and minutes"), hours, minutes)
        }
        if hours > 0 {
            return String(format: NSLocalizedString("history.delay.hours", comment: "Delay hours"), hours)
        }
        return String(format: NSLocalizedString("history.delay.minutes", comment: "Delay minutes"), minutes)
    }

    private func recordedByText(for group: HistoryDaySlotGroup) -> String? {
        guard !group.recordedByTypes.isEmpty else { return nil }
        if group.recordedByTypes.count > 1 {
            return NSLocalizedString("history.recordedBy.mixed", comment: "Mixed recorders")
        }
        guard let actor = group.recordedByTypes.first else {
            return NSLocalizedString("history.recordedBy.unknown", comment: "Unknown recorder")
        }
        return recordedByText(for: actor)
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

    private func confirmationMessage(for action: HistoryDoseAction) -> String {
        let dose = action.dose
        switch action {
        case .backfill:
            return String(
                format: NSLocalizedString("history.day.backfill.confirm.message", comment: "Backfill confirm message"),
                dose.medicationName,
                HistoryDayDetailView.timeFormatter.string(from: dose.scheduledAt)
            )
        case .cancel:
            let recorder = dose.recordedByType.map(recordedByText(for:))
                ?? NSLocalizedString("history.recordedBy.unknown", comment: "Unknown recorder")
            let actualTime = dose.takenAt.map { HistoryDayDetailView.timeFormatter.string(from: $0) } ?? "—"
            return String(
                format: NSLocalizedString("history.day.cancel.confirm.message", comment: "Cancel confirm message"),
                dose.medicationName,
                HistoryDayDetailView.timeFormatter.string(from: dose.scheduledAt),
                actualTime,
                recorder
            )
        }
    }
}

#if targetEnvironment(simulator)
struct CaregiverHistoryV105DebugPreview: View {
    @StateObject private var viewModel: HistoryViewModel
    @State private var selectedTab: CaregiverTab = .history
    private let selectedDate: Date

    init() {
        let sessionStore = SessionStore()
        sessionStore.setMode(.caregiver)
        let client = APIClient(baseURL: URL(string: "http://localhost")!, sessionStore: sessionStore)
        let model = HistoryViewModel(apiClient: client, sessionStore: sessionStore)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let data = Data(Self.previewJSON.utf8)
        model.day = try? decoder.decode(HistoryDayResponseDTO.self, from: data)
        _viewModel = StateObject(wrappedValue: model)
        selectedDate = ISO8601DateFormatter().date(from: "2026-07-22T03:00:00Z") ?? Date()
    }

    var body: some View {
        ZStack {
            CaregiverUI.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CaregiverCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("選択中の日付")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Color.readableSecondaryText)
                            Text("7月22日（水）")
                                .font(.title3.weight(.bold))
                            Text("2/4回分 記録済み")
                                .font(.title2.weight(.bold))
                            Text("まだ記録されていない服薬があります。未来の予定は未記録として表示されます。")
                                .font(.subheadline)
                                .foregroundStyle(Color.readableSecondaryText)
                            HStack(spacing: 10) {
                                CaregiverStatusPill(text: "記録済み 2回分", color: CaregiverUI.teal, systemImage: "checkmark.circle.fill")
                                CaregiverStatusPill(text: "未記録 2回分", color: CaregiverUI.orange, systemImage: "clock.fill")
                            }
                        }
                    }

                    HistoryDayDetailView(
                        viewModel: viewModel,
                        selectedDate: selectedDate,
                        style: .caregiver
                    )
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 120)
            }
        }
        .safeAreaInset(edge: .bottom) {
            CaregiverBottomTabBar(selectedTab: $selectedTab, hasLowStock: false, highlightedTab: nil)
                .padding(.horizontal, 12)
                .padding(.bottom, 4)
        }
    }

    private static let previewJSON = #"""
    {
      "date": "2026-07-22",
      "doses": [
        {"medicationId":"morning-1","medicationName":"朝・昼・夜 確認用のお薬","dosageText":"1錠","doseCountPerIntake":1,"scheduledAt":"2026-07-21T23:00:00Z","takenAt":"2026-07-22T08:21:00Z","slot":"morning","effectiveStatus":"taken","recordedByType":"patient"},
        {"medicationId":"noon-1","medicationName":"カルボシステイン","dosageText":"500 mg","doseCountPerIntake":1,"scheduledAt":"2026-07-22T03:30:00Z","takenAt":"2026-07-22T08:51:00Z","slot":"noon","effectiveStatus":"taken","recordedByType":"patient"},
        {"medicationId":"noon-2","medicationName":"整腸剤","dosageText":"50 mg","doseCountPerIntake":1,"scheduledAt":"2026-07-22T03:30:00Z","takenAt":"2026-07-22T08:51:00Z","slot":"noon","effectiveStatus":"taken","recordedByType":"patient"},
        {"medicationId":"evening-1","medicationName":"夕食後の薬","dosageText":"1錠","doseCountPerIntake":1,"scheduledAt":"2026-07-22T10:00:00Z","takenAt":null,"slot":"evening","effectiveStatus":"missed","recordedByType":null},
        {"medicationId":"bedtime-1","medicationName":"眠前の薬","dosageText":"1錠","doseCountPerIntake":1,"scheduledAt":"2026-07-22T14:50:00Z","takenAt":null,"slot":"bedtime","effectiveStatus":"missed","recordedByType":null,"cancelledAt":"2026-07-22T15:25:00Z","cancelledByType":"caregiver","cancelledRecordTakenAt":"2026-07-22T15:13:00Z","inventoryRestored":true}
      ],
      "prnItems": [
        {"medicationId":"prn-1","medicationName":"頭痛薬","takenAt":"2026-07-22T05:10:00Z","quantityTaken":1,"actorType":"PATIENT"},
        {"medicationId":"prn-2","medicationName":"解熱剤","takenAt":"2026-07-22T11:30:00Z","quantityTaken":2,"actorType":"CAREGIVER"}
      ]
    }
    """#
}
#endif

enum HistoryDayDetailStyle {
    case caregiver
    case patient
}

private enum HistoryDoseAction: Identifiable {
    case backfill(HistoryDayItemDTO)
    case cancel(HistoryDayItemDTO)

    var id: String {
        switch self {
        case .backfill(let dose): return "backfill-\(dose.historyRowID)"
        case .cancel(let dose): return "cancel-\(dose.historyRowID)"
        }
    }

    var dose: HistoryDayItemDTO {
        switch self {
        case .backfill(let dose), .cancel(let dose): return dose
        }
    }

    var confirmationTitle: String {
        switch self {
        case .backfill:
            return NSLocalizedString("history.day.backfill.confirm.title", comment: "Backfill confirm title")
        case .cancel:
            return NSLocalizedString("history.day.cancel.confirm.title", comment: "Cancel confirm title")
        }
    }

    var confirmationButtonTitle: String {
        switch self {
        case .backfill:
            return NSLocalizedString("history.day.backfill.confirm.action", comment: "Backfill confirm action")
        case .cancel:
            return NSLocalizedString("history.day.cancel.confirm.action", comment: "Cancel confirm action")
        }
    }
}

struct HistoryDaySlotGroup: Identifiable, Equatable {
    let slot: HistorySlotDTO
    let doses: [HistoryDayItemDTO]

    var id: String { slot.rawValue }

    var scheduledAt: Date {
        doses.map(\.scheduledAt).min() ?? .distantPast
    }

    var takenAt: Date? {
        doses.compactMap(\.takenAt).max()
    }

    var status: HistoryDoseStatusDTO {
        if doses.allSatisfy({ $0.effectiveStatus == .taken }) { return .taken }
        if doses.contains(where: { $0.effectiveStatus == .missed }) { return .missed }
        return .pending
    }

    var isPartiallyTaken: Bool {
        let takenCount = doses.filter { $0.effectiveStatus == .taken }.count
        return takenCount > 0 && takenCount < doses.count
    }

    var maximumDelay: TimeInterval {
        doses.compactMap { dose in
            guard let takenAt = dose.takenAt else { return nil }
            return max(0, takenAt.timeIntervalSince(dose.scheduledAt))
        }.max() ?? 0
    }

    var isLate: Bool {
        maximumDelay >= MedicationRecordingPolicy.lateThresholdSeconds
    }

    var recordedByTypes: Set<RecordedByTypeDTO> {
        Set(doses.compactMap(\.recordedByType))
    }

    var allCancelled: Bool {
        !doses.isEmpty && doses.allSatisfy { $0.cancelledAt != nil }
    }
}

private struct CaregiverHistorySlotGroupView: View {
    let group: HistoryDaySlotGroup
    let isExpanded: Bool
    let isHighlighted: Bool
    let slotTitle: String
    let slotColor: Color
    let scheduledTimeText: String
    let takenTimeText: String?
    let recordedByText: String?
    let delayText: String?
    let onToggle: () -> Void
    let onBackfill: (HistoryDayItemDTO) -> Void
    let onCancel: (HistoryDayItemDTO) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onToggle) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        Image(systemName: slotIconName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 34, height: 34)
                            .background(slotColor, in: Circle())

                        Text(slotTitle)
                            .font(.headline.weight(.bold))
                            .foregroundStyle(slotColor)
                        Text(String(format: NSLocalizedString("history.schedule.format", comment: "Scheduled time"), scheduledTimeText))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)

                        Spacer(minLength: 4)

                        CaregiverStatusPill(
                            text: statusText,
                            color: statusColor,
                            systemImage: statusIconName
                        )

                        Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                    }

                    if let takenTimeText {
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: 10) { recordingDetails(takenTimeText: takenTimeText) }
                            VStack(alignment: .leading, spacing: 6) { recordingDetails(takenTimeText: takenTimeText) }
                        }
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("CaregiverHistorySlotHeader.\(group.slot.rawValue)")

            if isExpanded {
                Divider()
                    .padding(.vertical, 10)

                VStack(spacing: 8) {
                    ForEach(group.doses.sorted(by: doseSort), id: \.historyRowID) { dose in
                        CaregiverHistoryMedicationRow(
                            dose: dose,
                            canBackfill: dose.effectiveStatus == .missed || dose.cancelledAt != nil,
                            canCancel: dose.effectiveStatus == .taken,
                            onBackfill: { onBackfill(dose) },
                            onCancel: { onCancel(dose) }
                        )
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(14)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(isHighlighted ? slotColor : slotColor.opacity(0.38), lineWidth: isHighlighted ? 2 : 1)
        }
        .shadow(color: CaregiverUI.cardShadow, radius: 7, y: 3)
        .accessibilityIdentifier("CaregiverHistorySlotGroup.\(group.slot.rawValue)")
    }

    @ViewBuilder
    private func recordingDetails(takenTimeText: String) -> some View {
        Label(
            String(format: NSLocalizedString("caregiver.today.actualTime.format", comment: "Actual record time"), takenTimeText),
            systemImage: "clock.fill"
        )
        .font(.caption.weight(.bold))
        .foregroundStyle(group.isLate ? CaregiverUI.orange : CaregiverUI.tealDark)

        if let delayText {
            Text(delayText)
                .font(.caption.weight(.bold))
                .foregroundStyle(CaregiverUI.orange)
        }

        if let recordedByText {
            Label(recordedByText, systemImage: "person.crop.circle.badge.checkmark")
                .font(.caption.weight(.bold))
                .foregroundStyle(CaregiverUI.tealDark)
        }
    }

    private var statusText: String {
        if group.allCancelled {
            return NSLocalizedString("history.status.cancelled", comment: "Cancelled")
        }
        if group.isPartiallyTaken {
            return NSLocalizedString("history.status.partial", comment: "Partially recorded")
        }
        switch group.status {
        case .taken:
            return group.isLate
                ? NSLocalizedString("history.status.late", comment: "Late")
                : NSLocalizedString("history.status.taken", comment: "Taken")
        case .missed:
            return NSLocalizedString("history.status.missed", comment: "Missed")
        case .pending:
            return NSLocalizedString("history.status.pending", comment: "Pending")
        }
    }

    private var statusColor: Color {
        if group.allCancelled { return .gray }
        if group.isPartiallyTaken { return CaregiverUI.orange }
        if group.isLate { return CaregiverUI.orange }
        switch group.status {
        case .taken: return CaregiverUI.teal
        case .missed: return CaregiverUI.red
        case .pending: return .gray
        }
    }

    private var statusIconName: String? {
        if group.allCancelled { return "arrow.uturn.backward" }
        if group.isPartiallyTaken { return "exclamationmark" }
        if group.isLate { return "clock.badge.exclamationmark.fill" }
        switch group.status {
        case .taken: return "checkmark"
        case .missed: return "exclamationmark"
        case .pending: return "clock"
        }
    }

    private var slotIconName: String {
        switch group.slot {
        case .morning: return "sunrise.fill"
        case .noon: return "sun.max.fill"
        case .evening: return "moon.fill"
        case .bedtime: return "bed.double.fill"
        }
    }

    private func doseSort(_ lhs: HistoryDayItemDTO, _ rhs: HistoryDayItemDTO) -> Bool {
        lhs.medicationName.localizedCompare(rhs.medicationName) == .orderedAscending
    }
}

private struct CaregiverHistoryMedicationRow: View {
    let dose: HistoryDayItemDTO
    let canBackfill: Bool
    let canCancel: Bool
    let onBackfill: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                MedicationSymbolView(tint: statusColor)
                    .frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text(displayName)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count"), AppConstants.formatDecimal(dose.doseCountPerIntake)))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                    if let cancelledAt = dose.cancelledAt {
                        Text(
                            String(
                                format: NSLocalizedString("history.day.cancelled.detail", comment: "Cancelled detail"),
                                HistoryDayDetailView.timeFormatter.string(from: cancelledAt)
                            )
                        )
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.readableSecondaryText)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: statusIconName)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 26, height: 26)
                    .background(statusColor, in: Circle())
            }

            if canBackfill {
                Button(action: onBackfill) {
                    Label(
                        dose.cancelledAt == nil
                            ? NSLocalizedString("history.day.backfill.button", comment: "Backfill button")
                            : NSLocalizedString("history.day.backfill.again.button", comment: "Backfill again button"),
                        systemImage: "checkmark.circle.fill"
                    )
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(CaregiverUI.teal, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
            }

            if canCancel {
                Button(action: onCancel) {
                    Label(NSLocalizedString("history.day.cancel.button", comment: "Cancel dose button"), systemImage: "arrow.uturn.backward.circle.fill")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(CaregiverUI.red)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(CaregiverUI.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .background(CaregiverUI.elevatedBackground.opacity(0.78), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(CaregiverUI.cardStroke, lineWidth: 1)
        }
    }

    private var displayName: String {
        let dosage = dose.dosageText.trimmingCharacters(in: .whitespacesAndNewlines)
        return dosage.isEmpty ? dose.medicationName : "\(dose.medicationName) \(dosage)"
    }

    private var statusColor: Color {
        if dose.cancelledAt != nil { return .gray }
        switch dose.effectiveStatus {
        case .taken: return CaregiverUI.teal
        case .missed: return CaregiverUI.red
        case .pending: return .gray
        }
    }

    private var statusIconName: String {
        if dose.cancelledAt != nil { return "arrow.uturn.backward" }
        switch dose.effectiveStatus {
        case .taken: return "checkmark"
        case .missed: return "exclamationmark"
        case .pending: return "clock"
        }
    }
}

private struct HistoryDayRow: View {
    let scheduledTimeText: String
    let takenTimeText: String?
    let isLate: Bool
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
                        Text(primaryTimeText)
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
                    if takenTimeText != nil {
                        Text(String(format: NSLocalizedString("history.schedule.format", comment: "Scheduled time"), scheduledTimeText))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.readableSecondaryText)
                    }
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
            return isLate
                ? NSLocalizedString("history.status.late", comment: "History late")
                : NSLocalizedString("history.status.taken", comment: "History taken")
        case .missed:
            return NSLocalizedString("history.status.missed", comment: "History missed")
        }
    }

    private var primaryTimeText: String {
        guard let takenTimeText else { return scheduledTimeText }
        return String(format: NSLocalizedString("history.taken.format", comment: "Actual taken time"), takenTimeText)
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
            if isLate { return style == .patient ? PatientUI.orange : Color.orange }
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
            if isLate { return (style == .patient ? PatientUI.orange : Color.orange).opacity(0.15) }
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

private struct CaregiverHistoryPrnGroupView: View {
    let records: [PrnHistoryItemDTO]
    let isExpanded: Bool
    let timeText: (PrnHistoryItemDTO) -> String
    let recordedByText: (PrnHistoryItemDTO) -> String
    let onToggle: () -> Void

    private let tint = Color.purple

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: 10) {
                    Image(systemName: "cross.case.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 34, height: 34)
                        .background(tint, in: Circle())

                    VStack(alignment: .leading, spacing: 2) {
                        Text(NSLocalizedString("history.prn.group.title", comment: "PRN history group title"))
                            .font(.headline.weight(.bold))
                            .foregroundStyle(tint)
                        Text(
                            String(
                                format: NSLocalizedString("history.prn.group.count", comment: "PRN history group count"),
                                records.count
                            )
                        )
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                    }

                    Spacer(minLength: 4)

                    CaregiverStatusPill(
                        text: NSLocalizedString("history.status.taken", comment: "Taken"),
                        color: tint,
                        systemImage: "checkmark"
                    )

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("CaregiverHistoryPrnHeader")

            if isExpanded {
                Divider()
                    .padding(.vertical, 10)

                VStack(spacing: 8) {
                    ForEach(records, id: \.historyRowID) { record in
                        HistoryDayPrnRow(
                            timeText: timeText(record),
                            name: record.medicationName,
                            quantity: record.quantityTaken,
                            recordedByText: recordedByText(record),
                            style: .caregiver
                        )
                        .accessibilityIdentifier("CaregiverHistoryPrnRecord.\(record.historyRowID)")
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(14)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(tint.opacity(0.38), lineWidth: 1)
        }
        .shadow(color: CaregiverUI.cardShadow, radius: 7, y: 3)
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
                Text(
                    String(
                        format: NSLocalizedString("history.day.prn.doseCount", comment: "PRN dose count"),
                        AppConstants.formatDecimal(quantity)
                    )
                )
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.readableSecondaryText)
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
