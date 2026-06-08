import SwiftUI

struct CaregiverTodayView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    private let onOpenNotifications: () -> Void
    private let patientName: String?
    private let headerView: AnyView?
    @StateObject private var viewModel: CaregiverTodayViewModel
    private let preferencesStore = NotificationPreferencesStore()
    @State private var doseToConfirm: ScheduleDoseDTO?
    @State private var slotToConfirm: SlotRecordConfirmation?

    init(
        sessionStore: SessionStore? = nil,
        onOpenPatients: @escaping () -> Void = {},
        onOpenNotifications: @escaping () -> Void = {},
        patientName: String? = nil,
        headerView: AnyView? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.onOpenPatients = onOpenPatients
        self.onOpenNotifications = onOpenNotifications
        self.patientName = patientName
        self.headerView = headerView
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverTodayViewModel(apiClient: APIClient(baseURL: baseURL, sessionStore: store))
        )
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .overlay(alignment: .top) {
                if let toastMessage = viewModel.toastMessage {
                    Text(toastMessage)
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .glassEffect(.regular, in: .capsule)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .accessibilityLabel(toastMessage)
                }
            }
            .overlay {
                if viewModel.isUpdating {
                    SchedulingRefreshOverlay()
                }
            }
            .onAppear {
            if sessionStore.currentPatientId != nil {
                viewModel.load(showLoading: true)
            }
        }
        .onChange(of: sessionStore.currentPatientId) { _, newValue in
            if newValue != nil {
                viewModel.load(showLoading: true)
            } else {
                viewModel.reset()
            }
        }
            .accessibilityIdentifier("CaregiverTodayView")
            .alert(
                NSLocalizedString("caregiver.today.confirm.title", comment: "Confirm record title"),
                isPresented: Binding(
                    get: { doseToConfirm != nil },
                    set: { if !$0 { doseToConfirm = nil } }
                )
            ) {
                Button(NSLocalizedString("caregiver.today.confirm.record", comment: "Confirm record")) {
                    if let dose = doseToConfirm {
                        viewModel.recordDose(dose)
                        doseToConfirm = nil
                    }
                }
                Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {
                    doseToConfirm = nil
                }
            } message: {
                if let dose = doseToConfirm {
                    Text(String(format: NSLocalizedString("caregiver.today.confirm.message", comment: "Confirm record message"), dose.medicationSnapshot.name))
                }
            }
            .alert(
                NSLocalizedString("caregiver.today.confirm.slot.title", comment: "Confirm slot record title"),
                isPresented: Binding(
                    get: { slotToConfirm != nil },
                    set: { if !$0 { slotToConfirm = nil } }
                )
            ) {
                Button(NSLocalizedString("caregiver.today.confirm.record", comment: "Confirm record")) {
                    if let confirmation = slotToConfirm {
                        viewModel.recordDoses(confirmation.doses)
                        slotToConfirm = nil
                    }
                }
                Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {
                    slotToConfirm = nil
                }
            } message: {
                if let confirmation = slotToConfirm {
                    Text(String(format: NSLocalizedString("caregiver.today.confirm.slot.message", comment: "Confirm slot record message"), confirmation.slotTitle, confirmation.doses.count))
                }
            }
            .environmentObject(sessionStore)
    }

    private var content: some View {
        Group {
            if sessionStore.currentPatientId == nil {
                VStack(spacing: 12) {
                    Spacer(minLength: 0)
                    VStack(spacing: 16) {
                        Image(systemName: "calendar.badge.questionmark")
                            .font(.system(size: 44))
                            .foregroundStyle(.secondary)
                        Text(NSLocalizedString("caregiver.medications.noSelection.title", comment: "No selection title"))
                            .font(.title3.weight(.semibold))
                            .multilineTextAlignment(.center)
                        Text(NSLocalizedString("caregiver.medications.noSelection.message", comment: "No selection message"))
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        Button {
                            onOpenPatients()
                        } label: {
                            Text(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab"))
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .glassEffect(.regular, in: .rect(cornerRadius: 20))
                    .padding(.horizontal, 24)
                    Spacer(minLength: 0)
                }
            } else if viewModel.isLoading {
                centeredLoadingState
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else if viewModel.items.isEmpty {
                CaregiverScreenBackground {
                    ScrollView {
                        VStack(spacing: 18) {
                            todayHeader
                            CaregiverCard {
                                VStack(spacing: 12) {
                                    Image(systemName: "checklist")
                                        .font(.system(size: 44, weight: .semibold))
                                        .foregroundStyle(CaregiverUI.teal)
                                    EmptyStateView(
                                        title: NSLocalizedString("caregiver.today.empty.title", comment: "Empty title"),
                                        message: NSLocalizedString("caregiver.today.empty.message", comment: "Empty message")
                                    )
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                        .padding(.bottom, 130)
                    }
                }
            } else {
                CaregiverScreenBackground {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            if let headerView {
                                headerView
                            }
                            todayHeader
                            nextDoseHeroCard
                            progressCard
                            todayScheduleTitle
                            timelineSection
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                        .padding(.bottom, 130)
                    }
                    .refreshable {
                        viewModel.load(showLoading: false)
                    }
                }
            }
        }
    }

    private var centeredLoadingState: some View {
        GeometryReader { proxy in
            LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var todayHeader: some View {
        HStack(alignment: .center, spacing: 12) {
            CaregiverAvatar(name: patientName, systemImage: "person.crop.circle.fill")
                .frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 3) {
                Text(patientNameText)
                    .font(.title3.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                Text(NSLocalizedString("caregiver.today.title", comment: "Caregiver today title"))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
            }
            Spacer()
            Button(action: onOpenNotifications) {
                Image(systemName: "bell")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(CaregiverUI.tealDark)
                    .frame(width: 42, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(NSLocalizedString("caregiver.dashboard.notifications", comment: "Notifications"))
            .accessibilityIdentifier("CaregiverTodayNotificationsButton")
        }
    }

    private var nextDoseHeroCard: some View {
        CaregiverCard(accent: nextDose == nil ? CaregiverUI.teal : nextDoseAccentColor) {
            VStack(alignment: .leading, spacing: 16) {
                Text(NSLocalizedString("caregiver.today.nextAction.title", comment: "Next action title"))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.primary)

                HStack(alignment: .center, spacing: 16) {
                    Image(systemName: nextDose == nil ? "checkmark.circle.fill" : "clock")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(nextDose == nil ? CaregiverUI.teal : CaregiverUI.tealDark)
                        .frame(width: 66, height: 66)
                        .background((nextDose == nil ? CaregiverUI.teal : CaregiverUI.tealDark).opacity(0.10), in: Circle())
                    VStack(alignment: .leading, spacing: 7) {
                        Text(nextDose == nil ? NSLocalizedString("caregiver.today.next.done", comment: "All done") : NSLocalizedString("caregiver.today.next.label", comment: "Next label"))
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.primary)
                        if let nextDose {
                            Text("\(slotTitle(for: slot(for: nextDose))) \(viewModel.timeText(for: nextDose.scheduledAt))")
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundStyle(CaregiverUI.tealDark)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        } else {
                            Text(NSLocalizedString("caregiver.today.next.done.message", comment: "All done message"))
                                .font(.headline.weight(.semibold))
                                .foregroundStyle(CaregiverUI.teal)
                        }
                    }
                    Spacer(minLength: 0)
                }

                HStack(alignment: .center, spacing: 10) {
                    CaregiverStatusPill(
                        text: nextDose == nil ? NSLocalizedString("patient.today.status.taken", comment: "Taken") : nextDoseStatusText,
                        color: nextDose == nil ? CaregiverUI.teal : nextDoseAccentColor,
                        systemImage: nextDose == nil ? "checkmark" : nil
                    )
                    Text(nextDoseHelperText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                nextActionDoseList

                primarySlotRecordButton
            }
        }
    }

    @ViewBuilder
    private var nextActionDoseList: some View {
        if let nextSlotRow, !nextSlotRow.doses.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(NSLocalizedString("caregiver.today.nextAction.medicinesTitle", comment: "Next action medicines title"))
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.secondary)
                ForEach(nextSlotRow.doses) { dose in
                    CaregiverTodayDoseLine(
                        dose: dose,
                        isOutOfStock: viewModel.isMedicationOutOfStock(dose.medicationId),
                        onRecord: { doseToConfirm = dose },
                        onDelete: { viewModel.deleteDose(dose) }
                    )
                }
            }
        }
    }

    private var progressCard: some View {
        CaregiverCard {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .stroke(CaregiverUI.teal.opacity(0.16), lineWidth: 9)
                    Circle()
                        .trim(from: 0, to: progressFraction)
                        .stroke(
                            CaregiverUI.teal,
                            style: StrokeStyle(lineWidth: 9, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                    Text("\(takenCount)/\(totalCount)")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundStyle(CaregiverUI.tealDark)
                        .accessibilityHidden(true)
                }
                .frame(width: 76, height: 76)

                VStack(alignment: .leading, spacing: 6) {
                    Text(NSLocalizedString("caregiver.today.progress.title", comment: "Progress title"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.primary)
                    Text(String(format: NSLocalizedString("caregiver.today.progress.format", comment: "Progress format"), takenCount, totalCount))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(CaregiverUI.tealDark)
                    Text(progressSummaryText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(NSLocalizedString("caregiver.today.progress.title", comment: "Progress title"))、\(String(format: NSLocalizedString("caregiver.today.progress.format", comment: "Progress format"), takenCount, totalCount))、\(progressSummaryText)")
    }

    private var todayScheduleTitle: some View {
        Text(NSLocalizedString("caregiver.today.timeline.title", comment: "Timeline title"))
            .font(.headline.weight(.bold))
            .foregroundStyle(.primary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var primarySlotRecordButton: some View {
        CaregiverPrimaryButton(
            title: primaryRecordTitle,
            systemImage: "pills.fill",
            color: canRecordNextSlot ? CaregiverUI.teal : .gray
        ) {
            if let nextSlotRow, !nextSlotRow.recordableDoses.isEmpty {
                slotToConfirm = SlotRecordConfirmation(
                    slotTitle: slotTitle(for: nextSlotRow.slot),
                    doses: nextSlotRow.recordableDoses
                )
            }
        }
        .disabled(!canRecordNextSlot)
        .accessibilityIdentifier("CaregiverTodayPrimaryRecordButton")
    }

    private var timelineSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(timelineRows) { row in
                CaregiverTodayTimelineRow(
                    row: row,
                    isOutOfStock: row.hasOutOfStock,
                    onRecordSlot: {
                        guard !row.recordableDoses.isEmpty else { return }
                        slotToConfirm = SlotRecordConfirmation(
                            slotTitle: slotTitle(for: row.slot),
                            doses: row.recordableDoses
                        )
                    },
                    onRecordDose: { dose in
                        doseToConfirm = dose
                    },
                    onDeleteDose: { dose in
                            viewModel.deleteDose(dose)
                    }
                )
            }
        }
    }

    private var patientNameText: String {
        guard let patientName, !patientName.isEmpty else {
            return NSLocalizedString("caregiver.common.patient.none", comment: "No patient selected")
        }
        return String(format: NSLocalizedString("caregiver.common.patient.format", comment: "Patient name format"), patientName)
    }

    private struct SlotSection: Identifiable {
        let id: String
        let slot: NotificationSlot?
        let items: [ScheduleDoseDTO]
    }

    private var slotSections: [SlotSection] {
        let orderedSlots: [NotificationSlot] = [.morning, .noon, .evening, .bedtime]
        var sections: [SlotSection] = []
        for slotValue in orderedSlots {
            let items = plannedItems.filter { slot(for: $0) == slotValue }
            if !items.isEmpty {
                sections.append(SlotSection(id: slotValue.rawValue, slot: slotValue, items: items))
            }
        }
        let otherItems = plannedItems.filter { slot(for: $0) == nil }
        if !otherItems.isEmpty {
            sections.append(SlotSection(id: "other", slot: nil, items: otherItems))
        }
        return sections
    }

    struct TimelineRow: Identifiable {
        let id: String
        let slot: NotificationSlot?
        let doses: [ScheduleDoseDTO]
        let timeText: String
        let statusText: String
        let statusColor: Color
        let slotColor: Color
        let isNextAction: Bool
        let recordableDoses: [ScheduleDoseDTO]
        let hasOutOfStock: Bool
    }

    private var timelineRows: [TimelineRow] {
        let orderedSlots: [NotificationSlot] = [.morning, .noon, .evening, .bedtime]
        let nextDoseId = nextDose?.id
        return orderedSlots.map { slotValue in
            let doses = viewModel.items
                .filter { slot(for: $0) == slotValue }
                .sorted { $0.scheduledAt < $1.scheduledAt }
            let representative = doses.sorted { $0.scheduledAt < $1.scheduledAt }.first
            let isNextAction = doses.contains { $0.id == nextDoseId }
            let recordableDoses = doses.filter {
                $0.effectiveStatus != .taken
                    && $0.effectiveStatus != .missed
                    && !viewModel.isMedicationOutOfStock($0.medicationId)
            }
            return TimelineRow(
                id: slotValue.rawValue,
                slot: slotValue,
                doses: doses,
                timeText: representative.map { viewModel.timeText(for: $0.scheduledAt) } ?? configuredTimeText(for: slotValue),
                statusText: timelineStatusText(for: doses, isNextAction: isNextAction),
                statusColor: statusColor(for: doses),
                slotColor: caregiverSlotColor(for: slotValue),
                isNextAction: isNextAction,
                recordableDoses: recordableDoses,
                hasOutOfStock: doses.contains { viewModel.isMedicationOutOfStock($0.medicationId) }
            )
        }
    }

    private var totalCount: Int { viewModel.items.count }
    private var takenCount: Int { viewModel.items.filter { $0.effectiveStatus == .taken }.count }
    private var missedCount: Int { viewModel.items.filter { $0.effectiveStatus == .missed }.count }
    private var pendingCount: Int { max(0, totalCount - takenCount - missedCount) }
    private var progressFraction: Double {
        guard totalCount > 0 else { return 0 }
        return Double(takenCount) / Double(totalCount)
    }

    private var nextDose: ScheduleDoseDTO? {
        viewModel.items
            .filter { $0.effectiveStatus != .taken && $0.effectiveStatus != .missed }
            .sorted { $0.scheduledAt < $1.scheduledAt }
            .first
    }

    private var canRecordNextDose: Bool {
        guard let nextDose else { return false }
        return !viewModel.isMedicationOutOfStock(nextDose.medicationId)
    }

    private var nextSlotRow: TimelineRow? {
        guard let nextDose else { return nil }
        let nextSlot = slot(for: nextDose)
        return timelineRows.first { $0.slot == nextSlot }
    }

    private var canRecordNextSlot: Bool {
        guard let nextSlotRow else { return false }
        return !nextSlotRow.recordableDoses.isEmpty
    }

    private var nextDoseAccentColor: Color {
        guard let nextDose else { return CaregiverUI.teal }
        if viewModel.isMedicationOutOfStock(nextDose.medicationId) { return CaregiverUI.red }
        return CaregiverUI.orange
    }

    private var nextDoseStatusText: String {
        guard let nextDose else { return NSLocalizedString("patient.today.status.taken", comment: "Taken") }
        if viewModel.isMedicationOutOfStock(nextDose.medicationId) {
            return NSLocalizedString("patient.today.outOfStock", comment: "Out of stock")
        }
        return NSLocalizedString("patient.today.status.pending", comment: "Pending")
    }

    private var primaryRecordTitle: String {
        nextDose == nil
            ? NSLocalizedString("caregiver.today.primaryRecord.done", comment: "Primary record done")
            : NSLocalizedString("caregiver.today.primaryRecord.slot", comment: "Primary record slot")
    }

    private var nextDoseHelperText: String {
        if nextDose == nil {
            return NSLocalizedString("caregiver.today.nextAction.doneHelp", comment: "Next action done help")
        }
        if !canRecordNextSlot {
            return NSLocalizedString("caregiver.today.nextAction.outOfStockHelp", comment: "Next action out of stock help")
        }
        return String(
            format: NSLocalizedString("caregiver.today.nextAction.slotHelp", comment: "Next action slot help"),
            nextSlotRow?.recordableDoses.count ?? 0
        )
    }

    private var progressSummaryText: String {
        if totalCount == 0 {
            return NSLocalizedString("caregiver.today.progress.empty", comment: "Progress empty")
        }
        if pendingCount > 0, let nextDose {
            return String(
                format: NSLocalizedString("caregiver.today.progress.nextSummary", comment: "Progress next summary"),
                slotTitle(for: slot(for: nextDose))
            )
        }
        if missedCount > 0 {
            return String(format: NSLocalizedString("caregiver.today.progress.missedSummary", comment: "Progress missed summary"), missedCount)
        }
        return NSLocalizedString("caregiver.today.progress.doneSummary", comment: "Progress done summary")
    }

    private func slotHeader(for slot: NotificationSlot?) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(slotColor(for: slot))
                .frame(width: 10, height: 10)
            Text(slotTitle(for: slot))
                .font(.headline)
                .foregroundStyle(.primary)
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(slotColor(for: slot).opacity(0.18))
        )
        .textCase(nil)
    }

    private func slotTitle(for slot: NotificationSlot?) -> String {
        switch slot {
        case .morning:
            return NSLocalizedString("patient.today.section.slot.morning", comment: "Morning slot")
        case .noon:
            return NSLocalizedString("patient.today.section.slot.noon", comment: "Noon slot")
        case .evening:
            return NSLocalizedString("patient.today.section.slot.evening", comment: "Evening slot")
        case .bedtime:
            return NSLocalizedString("patient.today.section.slot.bedtime", comment: "Bedtime slot")
        case .none:
            return NSLocalizedString("patient.today.section.slot.other", comment: "Other slot")
        }
    }

    private func slot(for dose: ScheduleDoseDTO?) -> NotificationSlot? {
        guard let dose else { return nil }
        return slot(for: dose)
    }

    private func slotColor(for slot: NotificationSlot?) -> Color {
        AppConstants.slotColor(for: slot)
    }

    private func slot(for dose: ScheduleDoseDTO) -> NotificationSlot? {
        NotificationSlot.from(date: dose.scheduledAt, slotTimes: preferencesStore.slotTimesMap())
    }

    private func configuredTimeText(for slot: NotificationSlot) -> String {
        let time = preferencesStore.slotTime(for: slot)
        return String(format: "%02d:%02d", time.hour, time.minute)
    }

    private func timelineStatusText(for doses: [ScheduleDoseDTO], isNextAction: Bool) -> String {
        guard !doses.isEmpty else {
            return NSLocalizedString("caregiver.today.timeline.noPlan", comment: "No plan")
        }
        if doses.allSatisfy({ $0.effectiveStatus == .taken }) {
            return NSLocalizedString("caregiver.today.timeline.taken", comment: "Taken")
        }
        if doses.allSatisfy({ $0.effectiveStatus == .missed }) {
            return NSLocalizedString("caregiver.today.timeline.missed", comment: "Missed")
        }
        if isNextAction {
            return NSLocalizedString("caregiver.today.timeline.next", comment: "Next")
        }
        return NSLocalizedString("caregiver.today.timeline.pending", comment: "Pending")
    }

    private func statusColor(for doses: [ScheduleDoseDTO]) -> Color {
        if doses.isEmpty {
            return .gray
        }
        if doses.allSatisfy({ $0.effectiveStatus == .taken }) {
            return CaregiverUI.teal
        }
        if doses.allSatisfy({ $0.effectiveStatus == .missed }) {
            return CaregiverUI.red
        }
        return CaregiverUI.orange
    }

    private func caregiverSlotColor(for slot: NotificationSlot?) -> Color {
        switch slot {
        case .morning:
            return CaregiverUI.teal
        case .noon:
            return CaregiverUI.orange
        case .evening:
            return CaregiverUI.blue
        case .bedtime:
            return CaregiverUI.tealDark
        case .none:
            return .gray
        }
    }

    private var plannedItems: [ScheduleDoseDTO] {
        viewModel.items.filter { dose in
            switch dose.effectiveStatus {
            case .taken:
                return false
            case .missed:
                return false
            case .pending, .none:
                return true
            }
        }
    }

    private var missedItems: [ScheduleDoseDTO] {
        viewModel.items.filter { $0.effectiveStatus == .missed }
    }

    private var takenItems: [ScheduleDoseDTO] {
        viewModel.items.filter { $0.effectiveStatus == .taken }
    }

    private func recordedByText(for dose: ScheduleDoseDTO) -> String? {
        guard dose.effectiveStatus == .taken, let recordedByType = dose.recordedByType else {
            return nil
        }
        let nameKey: String
        switch recordedByType {
        case .patient:
            nameKey = "caregiver.today.recordedBy.patient"
        case .caregiver:
            nameKey = "caregiver.today.recordedBy.caregiver"
        }
        let name = NSLocalizedString(nameKey, comment: "Recorded by actor")
        let format = NSLocalizedString("caregiver.today.recordedBy", comment: "Recorded by label")
        return String(format: format, name)
    }
}

private struct SlotRecordConfirmation {
    let slotTitle: String
    let doses: [ScheduleDoseDTO]
}

private struct CaregiverTodayTimelineRow: View {
    let row: CaregiverTodayView.TimelineRow
    let isOutOfStock: Bool
    let onRecordSlot: () -> Void
    let onRecordDose: (ScheduleDoseDTO) -> Void
    let onDeleteDose: (ScheduleDoseDTO) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: iconName)
                    .font(.system(size: 21, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(row.isNextAction ? CaregiverUI.orange : row.slotColor, in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(slotTitle)
                            .font(.headline.weight(.bold))
                            .foregroundStyle(row.isNextAction ? CaregiverUI.orange : row.slotColor)
                        Text(row.timeText)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                }

                Spacer(minLength: 0)

                CaregiverStatusPill(
                    text: isOutOfStock ? NSLocalizedString("patient.today.outOfStock", comment: "Out of stock") : row.statusText,
                    color: isOutOfStock ? CaregiverUI.red : row.statusColor,
                    systemImage: statusIcon
                )
            }

            VStack(spacing: 8) {
                if row.doses.isEmpty {
                    Text(NSLocalizedString("caregiver.today.timeline.noDose", comment: "No dose"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 4)
                } else {
                    ForEach(row.doses) { dose in
                        CaregiverTodayDoseLine(
                            dose: dose,
                            isOutOfStock: isOutOfStockDose(dose),
                            onRecord: { onRecordDose(dose) },
                            onDelete: { onDeleteDose(dose) }
                        )
                    }
                }
            }

            if !row.recordableDoses.isEmpty {
                Button(action: onRecordSlot) {
                    Label(
                        String(
                            format: NSLocalizedString("caregiver.today.timeline.recordSlot", comment: "Record slot"),
                            row.recordableDoses.count
                        ),
                        systemImage: "checkmark.circle.fill"
                    )
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(CaregiverUI.teal, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("CaregiverTodayRecordSlotButton.\(row.id)")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(row.isNextAction ? CaregiverUI.orange.opacity(0.06) : Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(borderColor, lineWidth: row.isNextAction ? 1.6 : 1)
        )
        .shadow(color: CaregiverUI.cardShadow, radius: 8, y: 3)
    }

    private var slotTitle: String {
        switch row.slot {
        case .morning:
            return NSLocalizedString("patient.today.section.slot.morning", comment: "Morning")
        case .noon:
            return NSLocalizedString("patient.today.section.slot.noon", comment: "Noon")
        case .evening:
            return NSLocalizedString("patient.today.section.slot.evening", comment: "Evening")
        case .bedtime:
            return NSLocalizedString("patient.today.section.slot.bedtime", comment: "Bedtime")
        case .none:
            return NSLocalizedString("patient.today.section.slot.other", comment: "Other")
        }
    }

    private var iconName: String {
        switch row.slot {
        case .morning:
            return "sunrise.fill"
        case .noon:
            return "sun.max.fill"
        case .evening:
            return "moon.fill"
        case .bedtime:
            return "bed.double.fill"
        case .none:
            return "clock.fill"
        }
    }

    private var statusIcon: String? {
        if isOutOfStock { return "exclamationmark" }
        if row.isNextAction { return "arrow.right" }
        if row.statusColor == CaregiverUI.teal { return "checkmark" }
        if row.statusColor == CaregiverUI.orange { return "exclamationmark" }
        return nil
    }

    private var borderColor: Color {
        if row.isNextAction {
            return CaregiverUI.orange.opacity(0.72)
        }
        return row.slotColor.opacity(0.38)
    }

    private func isOutOfStockDose(_ dose: ScheduleDoseDTO) -> Bool {
        isOutOfStock && dose.effectiveStatus != .taken && !row.recordableDoses.contains(where: { $0.id == dose.id })
    }
}

private struct CaregiverTodayDoseLine: View {
    let dose: ScheduleDoseDTO
    let isOutOfStock: Bool
    let onRecord: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "pills.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(statusColor)
                .frame(width: 30, height: 30)
                .background(statusColor.opacity(0.12), in: Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(dose.medicationSnapshot.name)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 0)

            CaregiverStatusPill(
                text: isOutOfStock ? NSLocalizedString("patient.today.outOfStock", comment: "Out of stock") : statusText,
                color: isOutOfStock ? CaregiverUI.red : statusColor,
                systemImage: statusIcon
            )

            if dose.effectiveStatus == .taken {
                Button(action: onDelete) {
                    Image(systemName: "arrow.uturn.backward")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 24, height: 24)
                        .background(Color.gray, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(NSLocalizedString("caregiver.today.delete.button", comment: "Delete"))
            } else if dose.effectiveStatus != .missed {
                Button(action: onRecord) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 24, height: 24)
                        .background(isOutOfStock ? Color.gray : CaregiverUI.teal, in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(isOutOfStock)
                .accessibilityLabel(NSLocalizedString("caregiver.today.record.button", comment: "Record"))
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .background(Color.white.opacity(0.78), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.black.opacity(0.06), lineWidth: 1)
        }
    }

    private var statusText: String {
        switch dose.effectiveStatus {
        case .taken:
            return NSLocalizedString("caregiver.today.timeline.taken", comment: "Taken")
        case .missed:
            return NSLocalizedString("caregiver.today.timeline.missed", comment: "Missed")
        case .pending, .none:
            return NSLocalizedString("caregiver.today.timeline.pending", comment: "Pending")
        }
    }

    private var statusColor: Color {
        switch dose.effectiveStatus {
        case .taken:
            return CaregiverUI.teal
        case .missed:
            return CaregiverUI.red
        case .pending, .none:
            return CaregiverUI.orange
        }
    }

    private var statusIcon: String? {
        if isOutOfStock { return "exclamationmark" }
        if dose.effectiveStatus == .taken { return "checkmark" }
        return nil
    }
}

private struct CaregiverTodayRow: View {
    let dose: ScheduleDoseDTO
    let timeText: String
    let actionTitle: String
    let recordedByText: String?
    let isDestructive: Bool
    let slotColor: Color?
    let onAction: () -> Void
    var isOutOfStock: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(timeText)
                        .font(.headline)
                    Text(medicationDisplayName)
                        .font(.title3.weight(.semibold))
                    Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
                        .font(.body)
                        .foregroundStyle(.secondary)
                    if let recordedByText {
                        Text(recordedByText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if isOutOfStock {
                        Text(NSLocalizedString("patient.today.outOfStock", comment: "Out of stock"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.vertical, 3)
                            .padding(.horizontal, 8)
                            .background(Color.red)
                            .clipShape(Capsule())
                    }
                }
                Spacer()
                if let statusText = statusText(for: dose.effectiveStatus) {
                    Text(statusText)
                        .font(.caption.weight(.semibold))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(statusBackground(for: dose.effectiveStatus))
                        .clipShape(Capsule())
                }
            }

            actionButton
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
        .overlay(alignment: .leading) {
            if let slotColor {
                RoundedRectangle(cornerRadius: 3)
                    .fill(slotColor)
                    .frame(width: 6)
                    .padding(.vertical, 12)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private var medicationDisplayName: String {
        let trimmed = dose.medicationSnapshot.dosageText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "不明" {
            return dose.medicationSnapshot.name
        }
        return "\(dose.medicationSnapshot.name) \(trimmed)"
    }

    private var accessibilitySummary: String {
        var parts = [timeText, medicationDisplayName]
        parts.append(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
        if let recordedByText {
            parts.append(recordedByText)
        }
        if let statusText = statusText(for: dose.effectiveStatus) {
            parts.append(statusText)
        }
        return parts.joined(separator: ", ")
    }

    private func statusText(for status: DoseStatusDTO?) -> String? {
        switch status {
        case .pending:
            return NSLocalizedString("patient.today.status.pending", comment: "Pending")
        case .taken:
            return NSLocalizedString("patient.today.status.taken", comment: "Taken")
        case .missed:
            return NSLocalizedString("patient.today.status.missed", comment: "Missed")
        case .none:
            return nil
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        if isDestructive {
            Button(action: onAction) {
                Text(actionTitle)
                    .font(.title3.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .tint(.red)
            .accessibilityLabel(actionTitle)
        } else {
            Button(action: onAction) {
                Text(actionTitle)
                    .font(.title3.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(.accentColor)
            .disabled(isOutOfStock)
            .accessibilityLabel(actionTitle)
        }
    }

    private func statusBackground(for status: DoseStatusDTO?) -> Color {
        switch status {
        case .missed:
            return Color.red.opacity(0.15)
        case .taken:
            return Color.green.opacity(0.15)
        case .pending, .none:
            return Color.primary.opacity(0.06)
        }
    }

}
