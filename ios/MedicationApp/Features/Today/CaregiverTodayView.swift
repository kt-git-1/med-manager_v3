import SwiftUI

struct CaregiverTodayView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    private let onOpenMedications: () -> Void
    private let patientName: String?
    private let headerView: AnyView?
    private let loadDataOnAppear: Bool
    private let hasSelectedPatientOverride: Bool?
    @StateObject private var viewModel: CaregiverTodayViewModel
    @EnvironmentObject private var toastPresenter: ToastPresenter
    private let preferencesStore: NotificationPreferencesStore
    @State private var slotToConfirm: SlotRecordConfirmation?

    init(
        sessionStore: SessionStore? = nil,
        onOpenPatients: @escaping () -> Void = {},
        onOpenMedications: @escaping () -> Void = {},
        patientName: String? = nil,
        headerView: AnyView? = nil,
        onLowStockChange: @escaping (Bool) -> Void = { _ in },
        previewItems: [ScheduleDoseDTO]? = nil,
        previewOutOfStockMedicationIds: Set<String> = []
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.onOpenPatients = onOpenPatients
        self.onOpenMedications = onOpenMedications
        self.patientName = patientName
        self.headerView = headerView
        self.loadDataOnAppear = previewItems == nil
        self.hasSelectedPatientOverride = previewItems == nil ? nil : true
        let baseURL = SessionStore.resolveBaseURL()
        let preferencesStore = NotificationPreferencesStore()
        self.preferencesStore = preferencesStore
        let viewModel = CaregiverTodayViewModel(
            apiClient: APIClient(baseURL: baseURL, sessionStore: store),
            onLowStockChange: onLowStockChange
        )
        if let previewItems {
            viewModel.items = previewItems
            viewModel.outOfStockMedicationIds = previewOutOfStockMedicationIds
        }
        _viewModel = StateObject(
            wrappedValue: viewModel
        )
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .overlay {
                if viewModel.isUpdating {
                    SchedulingRefreshOverlay()
                }
            }
            .onAppear {
                viewModel.toastPresenter = toastPresenter
                preferencesStore.switchPatient(sessionStore.currentPatientId)
                if loadDataOnAppear, sessionStore.currentPatientId != nil {
                    viewModel.load(showLoading: true)
                }
            }
            .onChange(of: sessionStore.currentPatientId) { _, newValue in
                preferencesStore.switchPatient(newValue)
                if newValue != nil {
                    viewModel.load(showLoading: true)
                } else {
                    viewModel.reset()
                }
            }
            .accessibilityIdentifier("CaregiverTodayView")
            .alert(
                NSLocalizedString("caregiver.today.confirm.slot.title", comment: "Confirm slot record title"),
                isPresented: Binding(
                    get: { slotToConfirm != nil },
                    set: { if !$0 { slotToConfirm = nil } }
                )
            ) {
                Button(NSLocalizedString("caregiver.today.confirm.record", comment: "Confirm record")) {
                    if let confirmation = slotToConfirm {
                        viewModel.recordDoses(confirmation.doses, slot: confirmation.slot)
                        slotToConfirm = nil
                    }
                }
                Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {
                    slotToConfirm = nil
                }
            } message: {
                if let confirmation = slotToConfirm {
                    Text(confirmSlotMessage(for: confirmation))
                }
            }
            .environmentObject(sessionStore)
    }

    private var content: some View {
        Group {
            if !(hasSelectedPatientOverride ?? (sessionStore.currentPatientId != nil)) {
                CaregiverPatientSelectionRequiredView(
                    systemImage: "calendar.badge.questionmark",
                    onOpenPatients: onOpenPatients
                )
            } else if viewModel.isLoading {
                centeredLoadingState
            } else if let errorMessage = viewModel.errorMessage {
                CaregiverDataUnavailableView(
                    message: errorMessage,
                    onRetry: { viewModel.load(showLoading: true) },
                    onReturnToLogin: { sessionStore.returnToCaregiverLogin() }
                )
            } else if viewModel.items.isEmpty && viewModel.prnMedications.isEmpty {
                CaregiverScreenBackground {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            if let headerView {
                                headerView
                            }
                            todayHeader
                            emptyTodayCard
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .safeAreaPadding(.bottom, 120)
                }
            } else {
                CaregiverScreenBackground {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            if let headerView {
                                headerView
                            }
                            todayHeader
                            if hasMissedDoses {
                                missedAlertCard
                            }
                            if !viewModel.items.isEmpty {
                                progressCard
                            }
                            if !viewModel.prnMedications.isEmpty {
                                prnEntryCard
                            }
                            if !viewModel.items.isEmpty {
                                todayScheduleTitle
                                timelineSection
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .safeAreaPadding(.bottom, 120)
                    .refreshable {
                        viewModel.load(showLoading: false)
                    }
                }
            }
        }
    }

    private var emptyTodayCard: some View {
        CaregiverCard(accent: CaregiverUI.teal) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top, spacing: 14) {
                    ZStack {
                        Circle()
                            .fill(CaregiverUI.teal.opacity(0.13))
                            .frame(width: 58, height: 58)
                        Image(systemName: "calendar.badge.plus")
                            .font(.title.weight(.bold))
                            .symbolRenderingMode(.hierarchical)
                            .foregroundStyle(CaregiverUI.teal)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("caregiver.today.empty.title", comment: "Empty title"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(NSLocalizedString("caregiver.today.empty.message", comment: "Empty message"))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                VStack(spacing: 10) {
                    CaregiverOnboardingStepRow(
                        number: 1,
                        title: NSLocalizedString("caregiver.today.empty.step.medication", comment: "Empty today medication step"),
                        systemImage: "pills.fill",
                        tint: CaregiverUI.teal
                    )
                    CaregiverOnboardingStepRow(
                        number: 2,
                        title: NSLocalizedString("caregiver.today.empty.step.schedule", comment: "Empty today schedule step"),
                        systemImage: "clock.fill",
                        tint: CaregiverUI.blue
                    )
                    CaregiverOnboardingStepRow(
                        number: 3,
                        title: NSLocalizedString("caregiver.today.empty.step.record", comment: "Empty today record step"),
                        systemImage: "checkmark.circle.fill",
                        tint: CaregiverUI.orange
                    )
                }

                CaregiverPrimaryButton(
                    title: NSLocalizedString("caregiver.today.empty.action", comment: "Open medications action"),
                    systemImage: "pills.fill"
                ) {
                    onOpenMedications()
                }
            }
        }
        .accessibilityIdentifier("CaregiverTodayEmptyCard")
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
        }
    }

    private var missedAlertCard: some View {
        CaregiverCard(accent: CaregiverUI.red) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(CaregiverUI.red, in: Circle())

                VStack(alignment: .leading, spacing: 6) {
                    Text(NSLocalizedString("caregiver.today.missedAlert.title", comment: "Missed alert title"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(CaregiverUI.red)
                    Text(missedAlertMessage)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
        }
        .accessibilityIdentifier("CaregiverTodayMissedAlertCard")
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

    private var prnEntryCard: some View {
        NavigationLink {
            CaregiverPrnMedicationListView(
                medications: viewModel.prnMedications,
                patientName: patientName,
                isDisabled: viewModel.isUpdating,
                onRecordConfirmed: { medication, onSuccess in
                    viewModel.recordPrnDose(for: medication, onSuccess: onSuccess)
                }
            )
        } label: {
            CaregiverCard(accent: CaregiverUI.orange) {
                HStack(alignment: .center, spacing: 16) {
                    Image(systemName: "cross.case.fill")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundStyle(CaregiverUI.orange)
                        .frame(width: 62, height: 62)
                        .background(CaregiverUI.orange.opacity(0.12), in: Circle())

                    VStack(alignment: .leading, spacing: 7) {
                        Text(NSLocalizedString("caregiver.today.prn.entry.title", comment: "Caregiver PRN entry title"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.82)
                        Text(
                            String(
                                format: NSLocalizedString("caregiver.today.prn.entry.message", comment: "Caregiver PRN entry message"),
                                viewModel.prnMedications.count
                            )
                        )
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("CaregiverTodayPrnEntryCard")
    }

    private var timelineSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(timelineRows) { row in
                CaregiverTodayTimelineRow(
                    row: row,
                    isOutOfStock: row.hasOutOfStock,
                    onRecordSlot: {
                        guard let slot = row.slot, !row.recordableDoses.isEmpty else { return }
                        slotToConfirm = SlotRecordConfirmation(
                            slot: slot,
                            slotTitle: slotTitle(for: row.slot),
                            doses: row.recordableDoses
                        )
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
        let recordableDoses: [ScheduleDoseDTO]
        let hasOutOfStock: Bool
    }

    private var timelineRows: [TimelineRow] {
        let orderedSlots: [NotificationSlot] = [.morning, .noon, .evening, .bedtime]
        return orderedSlots.map { slotValue in
            let doses = viewModel.items
                .filter { slot(for: $0) == slotValue }
                .sorted { $0.scheduledAt < $1.scheduledAt }
            let representative = doses.sorted { $0.scheduledAt < $1.scheduledAt }.first
            let recordableDoses = doses.filter {
                $0.effectiveStatus != .taken
                    && !viewModel.isMedicationOutOfStock($0.medicationId)
            }
            return TimelineRow(
                id: slotValue.rawValue,
                slot: slotValue,
                doses: doses,
                timeText: representative.map { viewModel.timeText(for: $0.scheduledAt) } ?? configuredTimeText(for: slotValue),
                statusText: timelineStatusText(for: doses),
                statusColor: statusColor(for: doses),
                slotColor: caregiverSlotColor(for: slotValue),
                recordableDoses: recordableDoses,
                hasOutOfStock: doses.contains { viewModel.isMedicationOutOfStock($0.medicationId) }
            )
        }
    }

    private var scheduledTimelineRows: [TimelineRow] {
        timelineRows.filter { !$0.doses.isEmpty }
    }

    private var totalCount: Int { scheduledTimelineRows.count }
    private var takenCount: Int {
        scheduledTimelineRows.filter { row in
            row.doses.allSatisfy { $0.effectiveStatus == .taken }
        }.count
    }
    private var missedCount: Int {
        missedTimelineRows.count
    }
    private var pendingCount: Int {
        scheduledTimelineRows.filter { row in
            row.doses.contains { $0.effectiveStatus == .pending || $0.effectiveStatus == nil }
        }.count
    }
    private var progressFraction: Double {
        guard totalCount > 0 else { return 0 }
        return Double(takenCount) / Double(totalCount)
    }

    private var progressSummaryText: String {
        if totalCount == 0 {
            return NSLocalizedString("caregiver.today.progress.empty", comment: "Progress empty")
        }
        if missedCount > 0 {
            return String(format: NSLocalizedString("caregiver.today.progress.missedSummary", comment: "Progress missed summary"), missedCount)
        }
        if pendingCount > 0 {
            return String(
                format: NSLocalizedString("caregiver.today.progress.pendingSummary", comment: "Progress pending summary"),
                pendingCount
            )
        }
        return NSLocalizedString("caregiver.today.progress.doneSummary", comment: "Progress done summary")
    }

    private var hasMissedDoses: Bool {
        !missedTimelineRows.isEmpty
    }

    private var missedTimelineRows: [TimelineRow] {
        scheduledTimelineRows.filter { row in
            row.doses.contains { $0.effectiveStatus == .missed }
        }
    }

    private var missedAlertMessage: String {
        if missedTimelineRows.count == 1, let row = missedTimelineRows.first {
            return String(
                format: NSLocalizedString("caregiver.today.missedAlert.single", comment: "Single missed alert message"),
                slotTitle(for: row.slot),
                row.timeText
            )
        }
        return String(
            format: NSLocalizedString("caregiver.today.missedAlert.multiple", comment: "Multiple missed alert message"),
            missedTimelineRows.count
        )
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
        NotificationSlot.from(date: dose.scheduledAt)
    }

    private func configuredTimeText(for slot: NotificationSlot) -> String {
        let time = preferencesStore.slotTime(for: slot)
        return String(format: "%02d:%02d", time.hour, time.minute)
    }

    private func timelineStatusText(for doses: [ScheduleDoseDTO]) -> String {
        guard !doses.isEmpty else {
            return NSLocalizedString("caregiver.today.timeline.noPlan", comment: "No plan")
        }
        if doses.allSatisfy({ $0.effectiveStatus == .taken }) {
            return NSLocalizedString("caregiver.today.timeline.taken", comment: "Taken")
        }
        if doses.allSatisfy({ $0.effectiveStatus == .missed }) {
            return NSLocalizedString("caregiver.today.timeline.missed", comment: "Missed")
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

    private func confirmSlotMessage(for confirmation: SlotRecordConfirmation) -> String {
        let key = confirmation.doses.contains { $0.effectiveStatus == .missed }
            ? "caregiver.today.confirm.slot.missed.message"
            : "caregiver.today.confirm.slot.message"
        return String(
            format: NSLocalizedString(key, comment: "Confirm slot record message"),
            confirmation.slotTitle,
            confirmation.doses.count
        )
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

struct CaregiverTodayDebugPreview: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        NavigationStack {
            CaregiverTodayView(
                sessionStore: sessionStore,
                patientName: "なおみ",
                previewItems: Self.previewItems
            )
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private static var previewItems: [ScheduleDoseDTO] {
        [
            dose(
                key: "preview-morning",
                medicationId: "preview-blood-pressure",
                hour: 8,
                name: "血圧の薬",
                dosageText: "5 mg",
                status: .missed,
                recordedByType: nil
            ),
            dose(
                key: "preview-noon-1",
                medicationId: "preview-calcium",
                hour: 13,
                name: "カルボシステイン",
                dosageText: "500 mg",
                status: .pending,
                recordedByType: nil
            ),
            dose(
                key: "preview-noon-2",
                medicationId: "preview-stomach",
                hour: 13,
                name: "整腸剤",
                dosageText: "50 mg",
                status: .pending,
                recordedByType: nil
            ),
            dose(
                key: "preview-evening",
                medicationId: "preview-evening-medication",
                hour: 19,
                name: "夕食後の薬",
                dosageText: "10 mg",
                status: .taken,
                recordedByType: .patient
            )
        ]
    }

    private static func dose(
        key: String,
        medicationId: String,
        hour: Int,
        name: String,
        dosageText: String,
        status: DoseStatusDTO,
        recordedByType: RecordedByTypeDTO?
    ) -> ScheduleDoseDTO {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        let scheduledAt = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: Date()) ?? Date()
        return ScheduleDoseDTO(
            key: key,
            patientId: "preview-patient",
            medicationId: medicationId,
            scheduledAt: scheduledAt,
            effectiveStatus: status,
            recordedByType: recordedByType,
            medicationSnapshot: MedicationSnapshotDTO(
                name: name,
                dosageText: dosageText,
                doseCountPerIntake: 1,
                dosageStrengthValue: 1,
                dosageStrengthUnit: "錠",
                notes: nil
            )
        )
    }
}

private struct SlotRecordConfirmation {
    let slot: NotificationSlot
    let slotTitle: String
    let doses: [ScheduleDoseDTO]
}

private struct CaregiverPrnMedicationListView: View {
    let medications: [MedicationDTO]
    let patientName: String?
    let isDisabled: Bool
    let onRecordConfirmed: (MedicationDTO, @escaping () -> Void) -> Void
    @State private var selectedMedication: MedicationDTO?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            CaregiverScreenBackground {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 16) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(NSLocalizedString("caregiver.today.prn.list.title", comment: "Caregiver PRN list title"))
                                .font(.title2.weight(.bold))
                                .foregroundStyle(.primary)
                            Text(NSLocalizedString("caregiver.today.prn.list.subtitle", comment: "Caregiver PRN list subtitle"))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.top, 4)

                        ForEach(medications) { medication in
                            CaregiverPrnMedicationCard(
                                medication: medication,
                                isDisabled: isDisabled,
                                onRecord: {
                                    selectedMedication = medication
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .safeAreaPadding(.bottom, 120)
                }
            }
        }
        .navigationTitle(NSLocalizedString("caregiver.today.prn.screen.title", comment: "Caregiver PRN screen title"))
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if isDisabled {
                SchedulingRefreshOverlay()
            }
        }
        .alert(
            NSLocalizedString("caregiver.today.prn.confirm.title", comment: "Caregiver PRN confirm title"),
            isPresented: Binding(
                get: { selectedMedication != nil },
                set: { if !$0 { selectedMedication = nil } }
            ),
            presenting: selectedMedication
        ) { medication in
            Button(NSLocalizedString("caregiver.today.prn.confirm.action", comment: "Caregiver PRN confirm action")) {
                onRecordConfirmed(medication) {
                    dismiss()
                }
                selectedMedication = nil
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {
                selectedMedication = nil
            }
        } message: { medication in
            Text(confirmMessage(for: medication))
        }
    }

    private func confirmMessage(for medication: MedicationDTO) -> String {
        String(
            format: NSLocalizedString("caregiver.today.prn.confirm.message", comment: "Caregiver PRN confirm message"),
            patientDisplayName,
            medication.name
        )
    }

    private var patientDisplayName: String {
        let trimmed = patientName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty
            ? NSLocalizedString("caregiver.today.prn.patient.default", comment: "Default patient display name")
            : trimmed
    }
}

private struct CaregiverPrnMedicationCard: View {
    let medication: MedicationDTO
    let isDisabled: Bool
    let onRecord: () -> Void
    @State private var recordTrigger = 0
    @State private var isPressed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                MedicationSymbolView(tint: CaregiverUI.orange)
                    .frame(width: 48, height: 48)

                VStack(alignment: .leading, spacing: 8) {
                    Text(medicationDisplayName)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.82)
                    Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(medication.doseCountPerIntake)))
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.secondary)
                    if let noteText, !noteText.isEmpty {
                        Text(noteText)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if medication.isInsufficientForDose {
                        Text(NSLocalizedString("caregiver.today.prn.outOfStock", comment: "Caregiver PRN out of stock"))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.vertical, 4)
                            .padding(.horizontal, 10)
                            .background(CaregiverUI.red)
                            .clipShape(Capsule())
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
            }

            Button(action: handleRecord) {
                Label(recordButtonTitle, systemImage: recordButtonIcon)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 58)
                    .background(recordButtonColor, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isDisabled || medication.isInsufficientForDose)
            .opacity(isDisabled || medication.isInsufficientForDose ? 0.62 : 1)
            .accessibilityLabel(recordButtonTitle)
            .scaleEffect(isPressed ? 0.96 : 1.0)
            .animation(.easeInOut(duration: 0.18), value: isPressed)
            .sensoryFeedback(.success, trigger: recordTrigger)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(CaregiverUI.orange.opacity(0.38), lineWidth: 1.2)
        }
        .shadow(color: CaregiverUI.cardShadow, radius: 12, y: 5)
    }

    private var medicationDisplayName: String {
        let trimmed = medication.dosageText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "不明" {
            return medication.name
        }
        return "\(medication.name) \(trimmed)"
    }

    private var noteText: String? {
        let instruction = medication.prnInstructions?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !instruction.isEmpty {
            return instruction
        }
        let notes = medication.notes?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return notes.isEmpty ? nil : notes
    }

    private var recordButtonTitle: String {
        medication.isInsufficientForDose
            ? NSLocalizedString("caregiver.today.prn.outOfStock.button", comment: "Caregiver PRN out of stock button")
            : NSLocalizedString("caregiver.today.prn.record.button", comment: "Caregiver PRN record button")
    }

    private var recordButtonIcon: String {
        medication.isInsufficientForDose ? "exclamationmark.triangle.fill" : "checkmark.circle.fill"
    }

    private var recordButtonColor: Color {
        medication.isInsufficientForDose ? .gray : CaregiverUI.teal
    }

    private func handleRecord() {
        guard !isDisabled, !medication.isInsufficientForDose else { return }
        recordTrigger += 1
        withAnimation(.easeInOut(duration: 0.12)) {
            isPressed = true
        }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(180))
            isPressed = false
        }
        onRecord()
    }
}

private struct CaregiverTodayTimelineRow: View {
    let row: CaregiverTodayView.TimelineRow
    let isOutOfStock: Bool
    let onRecordSlot: () -> Void
    let onDeleteDose: (ScheduleDoseDTO) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: iconName)
                    .font(.system(size: 21, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(row.slotColor, in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(slotTitle)
                            .font(.headline.weight(.bold))
                            .foregroundStyle(row.slotColor)
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
        .background(CaregiverUI.cardBackground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(row.slotColor.opacity(0.38), lineWidth: 1)
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
        if row.statusColor == CaregiverUI.teal { return "checkmark" }
        if row.statusColor == CaregiverUI.orange { return "exclamationmark" }
        return nil
    }

    private func isOutOfStockDose(_ dose: ScheduleDoseDTO) -> Bool {
        isOutOfStock && dose.effectiveStatus != .taken && !row.recordableDoses.contains(where: { $0.id == dose.id })
    }
}

private struct CaregiverTodayDoseLine: View {
    let dose: ScheduleDoseDTO
    let isOutOfStock: Bool
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            MedicationSymbolView(tint: statusColor)
                .frame(width: 30, height: 30)

            VStack(alignment: .leading, spacing: 3) {
                Text(medicationDisplayName)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)

            Spacer(minLength: 0)

            statusIndicator

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

    @ViewBuilder
    private var statusIndicator: some View {
        Image(systemName: statusSymbolName)
            .font(.system(size: 17, weight: .bold))
            .foregroundStyle(indicatorColor)
            .frame(width: 34, height: 34)
            .background(indicatorColor.opacity(0.13), in: Circle())
            .accessibilityLabel(indicatorAccessibilityLabel)
    }

    private var medicationDisplayName: String {
        let trimmed = dose.medicationSnapshot.dosageText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "不明" {
            return dose.medicationSnapshot.name
        }
        return "\(dose.medicationSnapshot.name) \(trimmed)"
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

    private var indicatorColor: Color {
        isOutOfStock ? CaregiverUI.red : statusColor
    }

    private var statusSymbolName: String {
        if isOutOfStock { return "exclamationmark" }
        switch dose.effectiveStatus {
        case .taken:
            return "checkmark"
        case .missed:
            return "exclamationmark"
        case .pending, .none:
            return "clock"
        }
    }

    private var indicatorAccessibilityLabel: String {
        isOutOfStock ? NSLocalizedString("patient.today.outOfStock", comment: "Out of stock") : statusText
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
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
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
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
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
