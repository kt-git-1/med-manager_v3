import SwiftUI

struct PatientTodayView: View {
    private let sessionStore: SessionStore
    private let loadDataOnAppear: Bool
    @StateObject private var viewModel: PatientTodayViewModel
    @Binding private var deepLinkTarget: NotificationDeepLinkTarget?

    init(
        sessionStore: SessionStore? = nil,
        preferencesStore: NotificationPreferencesStore = NotificationPreferencesStore(),
        previewItems: [ScheduleDoseDTO]? = nil,
        nowProvider: @escaping () -> Date = Date.init,
        onScheduledDoseRecorded: @escaping @MainActor () async -> Void = {},
        deepLinkTarget: Binding<NotificationDeepLinkTarget?> = .constant(nil)
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.loadDataOnAppear = previewItems == nil
        _deepLinkTarget = deepLinkTarget
        let baseURL = SessionStore.resolveBaseURL()
        let apiClient = APIClient(baseURL: baseURL, sessionStore: store)
        let viewModel = PatientTodayViewModel(
            apiClient: apiClient,
            preferencesStore: preferencesStore,
            nowProvider: nowProvider,
            onScheduledDoseRecorded: onScheduledDoseRecorded
        )
        if let previewItems {
            viewModel.items = previewItems
        }
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    var body: some View {
        PatientTodayRootView(
            sessionStore: sessionStore,
            viewModel: viewModel,
            loadDataOnAppear: loadDataOnAppear,
            deepLinkTarget: $deepLinkTarget
        )
    }
}

struct PatientTodayV105DebugPreview: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        ZStack {
            PatientScreenBackground()
            PatientTodayView(
                sessionStore: sessionStore,
                previewItems: Self.previewItems,
                nowProvider: { Self.previewNow }
            )
        }
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 12) {
                previewTab("今日", systemImage: "calendar", selected: true)
                previewTab("履歴", systemImage: "clock", selected: false)
                previewTab("設定", systemImage: "gearshape", selected: false)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: 28).stroke(PatientUI.cardStroke) }
            .shadow(color: PatientUI.cardShadow, radius: 14, y: 5)
            .padding(.horizontal, 14)
            .padding(.bottom, 8)
        }
    }

    private func previewTab(_ title: String, systemImage: String, selected: Bool) -> some View {
        VStack(spacing: 5) {
            Image(systemName: systemImage).font(.title2.weight(.bold))
            Text(title).font(.caption.weight(.bold))
        }
        .foregroundStyle(selected ? PatientUI.teal : Color.secondary)
        .frame(maxWidth: .infinity)
    }

    private static var previewNow: Date {
        date(hour: 13, minute: 47)
    }

    private static var previewItems: [ScheduleDoseDTO] {
        [
            dose(key: "morning-1", medicationId: "morning-med", hour: 8, minute: 0, name: "整腸剤", dosage: "50 mg", status: .taken, takenAt: date(hour: 8, minute: 7)),
            dose(key: "noon-1", medicationId: "noon-blood", hour: 12, minute: 30, name: "血圧の薬", dosage: "5 mg", status: .missed),
            dose(key: "noon-2", medicationId: "noon-stomach", hour: 12, minute: 30, name: "胃薬", dosage: "", status: .missed),
            dose(key: "evening-1", medicationId: "evening-med", hour: 19, minute: 0, name: "夕食後の薬", dosage: "10 mg", status: .pending),
            dose(key: "evening-2", medicationId: "evening-stomach", hour: 19, minute: 0, name: "胃薬", dosage: "", status: .pending),
            dose(key: "bedtime-1", medicationId: "bedtime-med", hour: 23, minute: 0, name: "眠前薬", dosage: "1 mg", status: .pending)
        ]
    }

    private static func dose(
        key: String,
        medicationId: String,
        hour: Int,
        minute: Int,
        name: String,
        dosage: String,
        status: DoseStatusDTO,
        takenAt: Date? = nil
    ) -> ScheduleDoseDTO {
        ScheduleDoseDTO(
            key: key,
            patientId: "preview-patient",
            medicationId: medicationId,
            scheduledAt: date(hour: hour, minute: minute),
            takenAt: takenAt,
            effectiveStatus: status,
            recordedByType: status == .taken ? .patient : nil,
            medicationSnapshot: MedicationSnapshotDTO(
                name: name,
                dosageText: dosage,
                doseCountPerIntake: 1,
                dosageStrengthValue: 1,
                dosageStrengthUnit: "錠",
                notes: nil
            )
        )
    }

    private static func date(hour: Int, minute: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        return calendar.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }
}

private struct PatientTodayRootView: View {
    private static let todayCalendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        return calendar
    }()
    let sessionStore: SessionStore
    @ObservedObject var viewModel: PatientTodayViewModel
    let loadDataOnAppear: Bool
    @EnvironmentObject private var toastPresenter: ToastPresenter
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var showingConfirm = false
    @State private var showingPrnConfirm = false
    @State private var showingBulkConfirm = false
    @State private var pendingScrollTarget: String?
    @State private var selectedDose: ScheduleDoseDTO?
    @State private var detailMedication: MedicationDTO?
    @State private var isDetailLoading = false
    @State private var detailErrorMessage: String?

    var body: some View {
        PatientTodayBaseView(
            viewModel: viewModel,
            suppressErrorState: sessionStore.isPatientTutorialPreviewActive,
            pendingScrollTarget: $pendingScrollTarget,
            slotSections: slotSections,
            missedItems: missedItems,
            takenItems: takenItems,
            slotSummaries: viewModel.slotSummaries,
            prnMedications: viewModel.prnMedications,
            isPrnDisabled: viewModel.isUpdating || viewModel.isPrnSubmitting,
            onConfirmDose: { viewModel.confirmRecord(for: $0) },
            onBulkRecord: { viewModel.confirmBulkRecord(for: $0) },
            onPresentDetail: presentDetail,
            onConfirmPrn: { viewModel.confirmPrnRecord(for: $0) },
            onRecordPrnDose: { medication, onSuccess in
                viewModel.recordPrnDose(for: medication, onSuccess: onSuccess)
            },
            timeText: { viewModel.timeText(for: $0) },
            shouldHighlight: shouldHighlight,
            slotColor: slotColor,
            slotTitle: slotTitle,
            isOutOfStock: { viewModel.isMedicationInventoryInsufficient($0) }
        )
        .modifier(
            PatientTodayLifecycleModifier(
                viewModel: viewModel,
                loadDataOnAppear: loadDataOnAppear,
                onHandleDeepLink: handleDeepLinkIfNeeded
            )
        )
        .modifier(
            PatientTodayAlertModifier(
                showingConfirm: $showingConfirm,
                showingPrnConfirm: $showingPrnConfirm,
                confirmDose: viewModel.confirmDose,
                confirmPrnMedication: viewModel.confirmPrnMedication,
                confirmMessage: confirmMessage,
                confirmPrnMessage: confirmPrnMessage,
                onConfirmDose: { viewModel.recordConfirmedDose() },
                onConfirmPrn: { viewModel.recordConfirmedPrnDose() }
            )
        )
        .alert(
            bulkConfirmTitle,
            isPresented: $showingBulkConfirm
        ) {
            Button(NSLocalizedString("patient.today.slot.bulk.confirm.record", comment: "Record")) {
                viewModel.executeBulkRecord()
            }
            Button(NSLocalizedString("patient.today.slot.bulk.confirm.cancel", comment: "Cancel"), role: .cancel) {
                viewModel.confirmSlot = nil
            }
        } message: {
            Text(bulkConfirmMessage)
        }
        .onChange(of: viewModel.confirmSlot) { _, newValue in
            showingBulkConfirm = newValue != nil
        }
        .modifier(
            PatientTodayChangeModifier(
                showingConfirm: $showingConfirm,
                showingPrnConfirm: $showingPrnConfirm,
                confirmDose: viewModel.confirmDose,
                confirmPrnMedication: viewModel.confirmPrnMedication,
                deepLinkTarget: $deepLinkTarget,
                items: viewModel.items,
                onHandleDeepLink: handleDeepLinkIfNeeded
            )
        )
        .sheet(item: $selectedDose, onDismiss: resetDetailState) { dose in
            PatientTodayDoseDetailView(
                dose: dose,
                medication: detailMedication,
                isLoading: isDetailLoading,
                errorMessage: detailErrorMessage,
                onRetry: { Task { await loadDetail(for: dose) } }
            )
            .task(id: dose.id) {
                await loadDetail(for: dose)
            }
        }
        .onAppear {
            viewModel.toastPresenter = toastPresenter
        }
        .accessibilityIdentifier("PatientTodayView")
        .environmentObject(sessionStore)
    }

    private var slotSections: [SlotSection] {
        let orderedSlots: [NotificationSlot] = [.morning, .noon, .evening, .bedtime]
        var sections: [SlotSection] = []
        for slotValue in orderedSlots {
            let items = viewModel.items.filter { slot(for: $0) == slotValue }
            if !items.isEmpty {
                sections.append(SlotSection(id: slotValue.rawValue, slot: slotValue, items: items))
            }
        }
        let otherItems = viewModel.items.filter { slot(for: $0) == nil }
        if !otherItems.isEmpty {
            sections.append(SlotSection(id: "other", slot: nil, items: otherItems))
        }
        return sections
    }

    private var missedItems: [ScheduleDoseDTO] {
        // Missed items are now shown within slot cards; this remains for backward compat
        []
    }

    private var takenItems: [ScheduleDoseDTO] {
        let now = Date()
        return viewModel.items.filter { dose in
            dose.effectiveStatus == .taken
                && Self.todayCalendar.isDate(dose.scheduledAt, inSameDayAs: now)
        }
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

    private func slotColor(for slot: NotificationSlot?) -> Color {
        AppConstants.slotColor(for: slot)
    }

    private func confirmMessage(for dose: ScheduleDoseDTO) -> String {
        let timeText = viewModel.timeText(for: dose.scheduledAt)
        return String(
            format: NSLocalizedString("patient.today.confirm.message", comment: "Confirm message"),
            dose.medicationSnapshot.name,
            timeText
        )
    }

    private func confirmPrnMessage(for medication: MedicationDTO) -> String {
        String(
            format: NSLocalizedString("patient.today.prn.confirm.message", comment: "PRN confirm message"),
            medication.name
        )
    }

    private var bulkConfirmTitle: String {
        guard let slot = viewModel.confirmSlot else {
            return NSLocalizedString("patient.today.slot.bulk.confirm.record", comment: "Record")
        }
        return String(
            format: NSLocalizedString("patient.today.slot.bulk.confirm.title", comment: "Bulk confirm title"),
            slotTitle(for: slot)
        )
    }

    private var bulkConfirmMessage: String {
        guard let slot = viewModel.confirmSlot else { return "" }
        let summary = viewModel.slotSummaries[slot]
        if let summary, summary.isLate {
            return String(
                format: NSLocalizedString("patient.today.slot.bulk.confirm.late.message", comment: "Late dose confirmation"),
                viewModel.delayText(for: summary.delaySeconds)
            )
        }
        return String(
            format: NSLocalizedString("patient.today.slot.bulk.confirm.message", comment: "Bulk confirm message"),
            slotTitle(for: slot),
            summary?.slotTime ?? "",
            "\(summary?.medCount ?? 0)",
            AppConstants.formatDecimal(summary?.totalPills ?? 0)
        )
    }

    private func slot(for dose: ScheduleDoseDTO) -> NotificationSlot? {
        NotificationSlot.from(
            date: dose.scheduledAt,
            slotTimes: viewModel.preferencesStore.slotTimesMap()
        )
    }

    private func shouldHighlight(dose: ScheduleDoseDTO) -> Bool {
        guard isRecordableNow(dose: dose) else { return false }
        return viewModel.highlightedSlot == slot(for: dose)
    }

    private func isRecordableNow(dose: ScheduleDoseDTO) -> Bool {
        switch dose.effectiveStatus {
        case .pending, .none:
            return Date() >= dose.scheduledAt.addingTimeInterval(-30 * 60)
        case .taken, .missed:
            return false
        }
    }

    private func presentDetail(for dose: ScheduleDoseDTO) {
        selectedDose = dose
    }

    private func resetDetailState() {
        detailMedication = nil
        detailErrorMessage = nil
        isDetailLoading = false
    }

    private func loadDetail(for dose: ScheduleDoseDTO) async {
        isDetailLoading = true
        detailErrorMessage = nil
        detailMedication = nil
        defer { isDetailLoading = false }
        do {
            detailMedication = try await viewModel.fetchMedicationDetail(medicationId: dose.medicationId)
        } catch {
            detailErrorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
        }
    }

    private func handleDeepLinkIfNeeded() {
        guard let target = deepLinkTarget else { return }
        guard !viewModel.isLoading else { return }
        if viewModel.handleDeepLink(target) != nil {
            pendingScrollTarget = scrollTarget(for: target)
        }
        deepLinkTarget = nil
    }

    private func scrollTarget(for target: NotificationDeepLinkTarget) -> String {
        if nextSlotSection?.slot == target.slot {
            return PatientTodayScrollTarget.nextSlot(target.slot)
        }
        return target.slot.rawValue
    }

    private var nextSlotSection: SlotSection? {
        let candidates = slotSections.compactMap { section -> PatientTodayNextSlotSelector.Candidate? in
            guard
                let slot = section.slot,
                let scheduledAt = section.items.map(\.scheduledAt).min(),
                let summary = viewModel.slotSummaries[slot]
            else {
                return nil
            }
            return PatientTodayNextSlotSelector.Candidate(
                slot: slot,
                scheduledAt: scheduledAt,
                remainingCount: summary.remainingCount,
                isWithinRecordingWindow: summary.isWithinRecordingWindow,
                isLate: summary.isLate,
                hasRecordableInventory: summary.hasRecordableInventory
            )
        }
        let selectionNow = viewModel.slotSummaries.values.first?.currentTime ?? Date()
        guard let nextSlot = PatientTodayNextSlotSelector.selectSlot(from: candidates, now: selectionNow) else {
            return nil
        }
        return slotSections.first { $0.slot == nextSlot }
    }
}

private enum PatientTodayScrollTarget {
    static let top = "PatientTodayScrollTop"

    static func nextSlot(_ slot: NotificationSlot) -> String {
        "nextSlot-\(slot.rawValue)"
    }
}

private struct PatientTodayBaseView: View {
    @ObservedObject var viewModel: PatientTodayViewModel
    let suppressErrorState: Bool
    @Binding var pendingScrollTarget: String?
    let slotSections: [SlotSection]
    let missedItems: [ScheduleDoseDTO]
    let takenItems: [ScheduleDoseDTO]
    let slotSummaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let prnMedications: [MedicationDTO]
    let isPrnDisabled: Bool
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onBulkRecord: (NotificationSlot) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onConfirmPrn: (MedicationDTO) -> Void
    let onRecordPrnDose: (MedicationDTO, @escaping () -> Void) -> Void
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String
    let isOutOfStock: (String) -> Bool

    var body: some View {
        baseView
    }

    private var baseView: some View {
        ZStack(alignment: .top) {
            content
            updatingOverlay
        }
    }

    @ViewBuilder
    private var updatingOverlay: some View {
        if viewModel.isUpdating {
            SchedulingRefreshOverlay()
        }
    }

    private var content: some View {
        Group {
            if viewModel.isLoading {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage, !suppressErrorState {
                ErrorStateView(message: errorMessage)
            } else if !hasScheduledContent {
                EmptyStateView(
                    title: NSLocalizedString("patient.today.empty.title", comment: "Empty title"),
                    message: NSLocalizedString("patient.today.empty.message", comment: "Empty message")
                )
            } else {
                ScrollViewReader { proxy in
                    PatientTodayListView(
                        viewModel: viewModel,
                        slotSections: slotSections,
                        missedItems: missedItems,
                        takenItems: takenItems,
                        slotSummaries: slotSummaries,
                        prnMedications: prnMedications,
                        isPrnDisabled: isPrnDisabled,
                        onConfirmDose: onConfirmDose,
                        onBulkRecord: onBulkRecord,
                        onPresentDetail: onPresentDetail,
                        onConfirmPrn: onConfirmPrn,
                        onRecordPrnDose: onRecordPrnDose,
                        timeText: timeText,
                        shouldHighlight: shouldHighlight,
                        slotColor: slotColor,
                        slotTitle: slotTitle,
                        isOutOfStock: isOutOfStock
                    )
                    .onChange(of: pendingScrollTarget) { _, target in
                        guard let target else { return }
                        withAnimation(.easeInOut) {
                            proxy.scrollTo(target, anchor: .center)
                        }
                        pendingScrollTarget = nil
                    }
                    .onChange(of: viewModel.scrollToTopRequest) { previousValue, newValue in
                        guard newValue > previousValue else { return }
                        Task { @MainActor in
                            await Task.yield()
                            withAnimation(.easeOut(duration: 0.25)) {
                                proxy.scrollTo(PatientTodayScrollTarget.top, anchor: .top)
                            }
                        }
                    }
                }
            }
        }
        .safeAreaPadding(.top)
    }

    private var hasScheduledContent: Bool {
        !slotSections.isEmpty || !missedItems.isEmpty || !takenItems.isEmpty
    }
}

private struct PatientTodayLifecycleModifier: ViewModifier {
    @ObservedObject var viewModel: PatientTodayViewModel
    let loadDataOnAppear: Bool
    let onHandleDeepLink: () -> Void
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                if loadDataOnAppear {
                    viewModel.handleAppear()
                }
                onHandleDeepLink()
            }
            .onDisappear {
                if loadDataOnAppear {
                    viewModel.handleDisappear()
                }
            }
            .onChange(of: scenePhase) { _, newValue in
                guard loadDataOnAppear, newValue == .active else { return }
                viewModel.load(showLoading: false)
            }
    }
}

private struct PatientTodayAlertModifier: ViewModifier {
    @Binding var showingConfirm: Bool
    @Binding var showingPrnConfirm: Bool
    let confirmDose: ScheduleDoseDTO?
    let confirmPrnMedication: MedicationDTO?
    let confirmMessage: (ScheduleDoseDTO) -> String
    let confirmPrnMessage: (MedicationDTO) -> String
    let onConfirmDose: () -> Void
    let onConfirmPrn: () -> Void

    func body(content: Content) -> some View {
        let doseAlert = content.alert(
            NSLocalizedString("patient.today.confirm.title", comment: "Confirm title"),
            isPresented: $showingConfirm,
            presenting: confirmDose
        ) { _ in
            Button(NSLocalizedString("patient.today.confirm.action", comment: "Confirm action")) {
                onConfirmDose()
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
        } message: { dose in
            Text(confirmMessage(dose))
        }
        let prnAlert = doseAlert.alert(
            NSLocalizedString("patient.today.prn.confirm.title", comment: "PRN confirm title"),
            isPresented: $showingPrnConfirm,
            presenting: confirmPrnMedication
        ) { _ in
            Button(NSLocalizedString("patient.today.prn.confirm.action", comment: "PRN confirm action")) {
                onConfirmPrn()
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
        } message: { medication in
            Text(confirmPrnMessage(medication))
        }
        return prnAlert
    }
}

private struct PatientTodayChangeModifier: ViewModifier {
    @Binding var showingConfirm: Bool
    @Binding var showingPrnConfirm: Bool
    let confirmDose: ScheduleDoseDTO?
    let confirmPrnMedication: MedicationDTO?
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    let items: [ScheduleDoseDTO]
    let onHandleDeepLink: () -> Void

    func body(content: Content) -> some View {
        let confirmLayer = content.onChange(of: confirmDose) { _, newValue in
            showingConfirm = newValue != nil
        }
        let prnLayer = confirmLayer.onChange(of: confirmPrnMedication?.id) { _, newValue in
            showingPrnConfirm = newValue != nil
        }
        let deepLinkLayer = prnLayer.onChange(of: deepLinkTarget) { _, _ in
            onHandleDeepLink()
        }
        let itemsLayer = deepLinkLayer.onChange(of: items) { _, _ in
            onHandleDeepLink()
        }
        return itemsLayer
    }
}

private struct SlotSection: Identifiable {
    let id: String
    let slot: NotificationSlot?
    let items: [ScheduleDoseDTO]
}

private struct PatientTodayListView: View {
    private static let weekdayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.japaneseLocale
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = AppConstants.defaultTimeZone
        formatter.dateFormat = "M月d日（E）"
        return formatter
    }()

    let viewModel: PatientTodayViewModel
    let slotSections: [SlotSection]
    let missedItems: [ScheduleDoseDTO]
    let takenItems: [ScheduleDoseDTO]
    let slotSummaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let prnMedications: [MedicationDTO]
    let isPrnDisabled: Bool
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onBulkRecord: (NotificationSlot) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onConfirmPrn: (MedicationDTO) -> Void
    let onRecordPrnDose: (MedicationDTO, @escaping () -> Void) -> Void
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String
    let isOutOfStock: (String) -> Bool

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                Color.clear
                    .frame(height: 1)
                    .id(PatientTodayScrollTarget.top)

                PatientHeader(
                    title: NSLocalizedString("patient.readonly.today.title", comment: "Today title"),
                    subtitle: Self.weekdayFormatter.string(from: Date()),
                    systemImage: "calendar"
                )

                if let inventoryWarning {
                    PatientInventoryWarningCard(warning: inventoryWarning)
                }

                nextExpectedSlotBanner

                PatientDayProgressStrip(
                    summaries: slotSummaries,
                    slotTitle: slotTitle,
                    activeSlot: nextSlotSection?.slot,
                    slotColor: slotColor,
                    timeText: timeText
                )

                nextDoseHeroCard

                if !prnMedications.isEmpty {
                    prnEntryCard
                }

                PatientTodayCompactSummary(
                    summaries: slotSummaries,
                    slotSections: slotSections,
                    activeSlot: nextSlotSection?.slot,
                    slotTitle: slotTitle,
                    timeText: timeText,
                    delayText: viewModel.delayText,
                    isUpdating: viewModel.isUpdating,
                    isOutOfStock: isOutOfStock,
                    onPresentDetail: onPresentDetail,
                    onBulkRecord: onBulkRecord
                )
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 132)
        }
        .refreshable {
            viewModel.load(showLoading: false)
        }
    }

    @ViewBuilder
    private var nextExpectedSlotBanner: some View {
        if let nextSlotSection,
           let slot = nextSlotSection.slot,
           let summary = slotSummaries[slot] {
            let activeColor = slotColor(slot)
            HStack(spacing: 12) {
                Image(systemName: "clock.fill")
                    .font(.title3.weight(.bold))

                Text(String(
                    format: NSLocalizedString("patient.today.summary.next", comment: "Next medication slot"),
                    slotTitle(slot),
                    summary.slotTime
                ))
                .font(.title3.weight(.bold))

                Spacer(minLength: 0)
            }
            .foregroundStyle(activeColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .background(activeColor.opacity(0.11), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(activeColor.opacity(0.45), lineWidth: 1.5)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("PatientTodayNextExpectedSlot")
        }
    }

    @ViewBuilder
    private var nextDoseHeroCard: some View {
        if let nextSlotSection, let slot = nextSlotSection.slot, let summary = slotSummaries[slot] {
            PatientCard(accent: summary.isLate ? PatientUI.orange : slotColor(slot)) {
                VStack(alignment: .leading, spacing: 12) {
                    Text(NSLocalizedString("patient.today.next.header", comment: "Next medications header"))
                        .font(.title2.weight(.bold))

                    HStack(alignment: .center, spacing: 14) {
                        Image(systemName: "clock.fill")
                            .font(.system(size: 30, weight: .bold))
                            .foregroundStyle(PatientUI.tealDark)
                            .frame(width: 58, height: 58)
                            .background(PatientUI.teal.opacity(0.12), in: Circle())
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(slotTitle(slot))のお薬")
                                .font(.system(size: 29, weight: .bold, design: .rounded))
                                .foregroundStyle(PatientUI.tealDark)
                                .lineLimit(1)
                                .minimumScaleFactor(0.72)
                            Text(String(format: NSLocalizedString("patient.today.schedule.format", comment: "Scheduled time"), summary.slotTime))
                                .font(.headline.weight(.bold))
                                .foregroundStyle(Color.readableSecondaryText)
                        }
                        Spacer(minLength: 0)
                    }

                    Text(String(
                        format: NSLocalizedString("patient.today.slot.bulk.summary", comment: "Summary"),
                        AppConstants.formatDecimal(summary.totalPills),
                        "\(summary.medCount)"
                    ))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)

                    if summary.isLate && summary.aggregateStatus != .taken {
                        Label(viewModel.delayText(for: summary.delaySeconds), systemImage: "clock.badge.exclamationmark")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(PatientUI.orange)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 12)
                            .background(PatientUI.orange.opacity(0.12), in: Capsule())
                    }

                    VStack(spacing: 10) {
                        ForEach(nextSlotSection.items) { dose in
                            SlotMedicationRow(
                                dose: dose,
                                isInventoryInsufficient: isOutOfStock(dose.medicationId)
                            )
                                .onTapGesture { onPresentDetail(dose) }
                        }
                    }

                    Button {
                        onBulkRecord(slot)
                    } label: {
                        Label(String(format: NSLocalizedString("patient.today.slot.bulk.button.actual", comment: "Record now"), timeText(summary.currentTime)), systemImage: "checkmark.circle.fill")
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 64)
                            .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(summary.remainingCount == 0 || viewModel.isUpdating || !summary.isWithinRecordingWindow || !summary.hasRecordableInventory)
                    .opacity(summary.remainingCount == 0 || viewModel.isUpdating || !summary.isWithinRecordingWindow || !summary.hasRecordableInventory ? 0.55 : 1)
                    .accessibilityIdentifier("PatientTodayPrimaryBulkRecordButton")
                }
            }
            .id(PatientTodayScrollTarget.nextSlot(slot))
        } else if hasLateUnrecordedSlot {
            PatientCard(accent: PatientUI.orange) {
                HStack(spacing: 16) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(PatientUI.orange)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("patient.today.next.overdue.title", comment: "No upcoming dose title"))
                            .font(.title2.weight(.bold))
                        Text(NSLocalizedString("patient.today.next.overdue.message", comment: "Record late doses below"))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        } else {
            PatientCard(accent: PatientUI.teal) {
                HStack(spacing: 16) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(PatientUI.teal)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("patient.today.next.done.title", comment: "All done title"))
                            .font(.title2.weight(.bold))
                        Text(NSLocalizedString("patient.today.next.done.message", comment: "All done message"))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var nextSlotSection: SlotSection? {
        let candidates = slotSections.compactMap { section -> PatientTodayNextSlotSelector.Candidate? in
            guard
                let slot = section.slot,
                let scheduledAt = section.items.map(\.scheduledAt).min(),
                let summary = slotSummaries[slot]
            else {
                return nil
            }
            return PatientTodayNextSlotSelector.Candidate(
                slot: slot,
                scheduledAt: scheduledAt,
                remainingCount: summary.remainingCount,
                isWithinRecordingWindow: summary.isWithinRecordingWindow,
                isLate: summary.isLate,
                hasRecordableInventory: summary.hasRecordableInventory
            )
        }
        let selectionNow = slotSummaries.values.first?.currentTime ?? Date()
        guard let nextSlot = PatientTodayNextSlotSelector.selectSlot(from: candidates, now: selectionNow) else {
            return nil
        }
        return slotSections.first { $0.slot == nextSlot }
    }

    private var hasLateUnrecordedSlot: Bool {
        slotSummaries.values.contains { summary in
            summary.remainingCount > 0
                && summary.isLate
                && summary.isWithinRecordingWindow
        }
    }

    private var inventoryWarning: PatientInventoryWarning? {
        let affectedDoses = viewModel.items.filter { dose in
            dose.effectiveStatus != .taken && isOutOfStock(dose.medicationId)
        }
        var seenMedicationIds = Set<String>()
        let medicationNames = affectedDoses.compactMap { dose -> String? in
            guard seenMedicationIds.insert(dose.medicationId).inserted else {
                return nil
            }
            return medicationDisplayName(for: dose)
        }
        guard let firstMedicationName = medicationNames.first else {
            return nil
        }
        return PatientInventoryWarning(
            firstMedicationName: firstMedicationName,
            medicationCount: medicationNames.count
        )
    }

    private func medicationDisplayName(for dose: ScheduleDoseDTO) -> String {
        let trimmedDosage = dose.medicationSnapshot.dosageText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedDosage.isEmpty || trimmedDosage == "不明" {
            return dose.medicationSnapshot.name
        }
        return "\(dose.medicationSnapshot.name) \(trimmedDosage)"
    }

    private var progressText: String {
        let total = viewModel.items.count
        let taken = viewModel.items.filter { $0.effectiveStatus == .taken }.count
        return "\(taken)/\(total)"
    }

    private var prnEntryCard: some View {
        NavigationLink {
            PrnMedicationListView(
                medications: prnMedications,
                isDisabled: isPrnDisabled,
                onRecordConfirmed: onRecordPrnDose
            )
        } label: {
            PatientCard(accent: PatientUI.orange) {
                HStack(alignment: .center, spacing: 16) {
                    Image(systemName: "cross.case.fill")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(PatientUI.orange)
                        .frame(width: 64, height: 64)
                        .background(PatientUI.orange.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 7) {
                        Text(NSLocalizedString("patient.today.prn.entry.title", comment: "PRN entry title"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.82)
                        Text(String(format: NSLocalizedString("patient.today.prn.entry.message", comment: "PRN entry message"), prnMedications.count))
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
        .accessibilityIdentifier("PatientTodayPrnEntryCard")
    }
}

private struct PatientInventoryWarning {
    let firstMedicationName: String
    let medicationCount: Int

    var message: String {
        if medicationCount <= 1 {
            return String(
                format: NSLocalizedString("patient.today.inventory.warning.single", comment: "Single out-of-stock medication warning"),
                firstMedicationName
            )
        }
        return String(
            format: NSLocalizedString("patient.today.inventory.warning.multiple", comment: "Multiple out-of-stock medications warning"),
            medicationCount
        )
    }
}

private struct PatientDayProgressStrip: View {
    private let slots: [NotificationSlot] = [.morning, .noon, .evening, .bedtime]
    let summaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let slotTitle: (NotificationSlot?) -> String
    let activeSlot: NotificationSlot?
    let slotColor: (NotificationSlot?) -> Color
    let timeText: (Date) -> String

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                let connectorWidth = max(0, geometry.size.width - 70)
                let completedWidth = connectorWidth * CGFloat(completedConnectorCount) / CGFloat(slots.count - 1)

                Capsule()
                    .fill(Color.secondary.opacity(0.38))
                    .frame(width: connectorWidth, height: 8)
                    .position(x: geometry.size.width / 2, y: 72)

                if completedWidth > 0 {
                    Capsule()
                        .fill(PatientUI.teal)
                        .frame(width: completedWidth, height: 8)
                        .position(x: 35 + completedWidth / 2, y: 72)
                }
            }

            HStack(spacing: 10) {
                ForEach(slots, id: \.rawValue) { slot in
                    VStack(spacing: 8) {
                        Text(slotTitle(slot))
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(accentColor(for: slot))
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)

                        ZStack {
                            Circle()
                                .fill(iconBackgroundColor(for: slot))
                                .frame(width: 52, height: 52)
                            Image(systemName: symbol(for: slot))
                                .font(.system(size: 24, weight: .bold))
                                .foregroundStyle(.white)
                        }

                        Text(detail(for: slot))
                            .font(.system(size: 19, weight: .bold, design: .rounded))
                            .foregroundStyle(accentColor(for: slot))
                            .lineLimit(1)
                            .minimumScaleFactor(0.68)
                    }
                    .padding(.vertical, 11)
                    .frame(maxWidth: .infinity)
                    .frame(height: 142)
                    .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .stroke(borderColor(for: slot), lineWidth: 2)
                    }
                }
            }
        }
        .frame(height: 142)
        .accessibilityIdentifier("PatientTodayProgressStrip")
    }

    private var completedConnectorCount: Int {
        min(
            slots.prefix { summaries[$0]?.aggregateStatus == .taken }.count,
            slots.count - 1
        )
    }

    private func symbol(for slot: NotificationSlot) -> String {
        guard let summary = summaries[slot] else { return "minus" }
        if summary.aggregateStatus == .taken { return "checkmark" }
        if slot == .bedtime { return "moon.fill" }
        return "clock"
    }

    private func accentColor(for slot: NotificationSlot) -> Color {
        guard let summary = summaries[slot] else { return .secondary }
        if summary.aggregateStatus == .taken { return PatientUI.teal }
        if slot == activeSlot { return slotColor(slot) }
        return .secondary
    }

    private func iconBackgroundColor(for slot: NotificationSlot) -> Color {
        accentColor(for: slot).opacity(slot == activeSlot || summaries[slot]?.aggregateStatus == .taken ? 1 : 0.82)
    }

    private func borderColor(for slot: NotificationSlot) -> Color {
        if summaries[slot]?.aggregateStatus == .taken { return PatientUI.teal }
        if slot == activeSlot { return slotColor(slot) }
        return Color.secondary.opacity(0.26)
    }

    private func detail(for slot: NotificationSlot) -> String {
        guard let summary = summaries[slot] else { return "—" }
        if summary.aggregateStatus == .taken, let takenAt = summary.takenAt {
            return timeText(takenAt)
        }
        return summary.slotTime
    }
}

private struct PatientTodayCompactSummary: View {
    private let slots: [NotificationSlot] = [.morning, .noon, .evening, .bedtime]
    let summaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let slotSections: [SlotSection]
    let activeSlot: NotificationSlot?
    let slotTitle: (NotificationSlot?) -> String
    let timeText: (Date) -> String
    let delayText: (TimeInterval) -> String
    let isUpdating: Bool
    let isOutOfStock: (String) -> Bool
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onBulkRecord: (NotificationSlot) -> Void
    @State private var expandedSlots = Set<NotificationSlot>()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(NSLocalizedString("patient.today.summary.section.title", comment: "Today status section title"))
                .font(.title2.weight(.bold))

            if completedSlots.isEmpty && lateSlots.isEmpty {
                PatientCard(accent: PatientUI.blue) {
                    HStack(alignment: .center, spacing: 14) {
                        Image(systemName: "clock.badge.questionmark.fill")
                            .font(.system(size: 27, weight: .bold))
                            .foregroundStyle(PatientUI.blue)
                            .frame(width: 52, height: 52)
                            .background(PatientUI.blue.opacity(0.12), in: Circle())

                        VStack(alignment: .leading, spacing: 5) {
                            Text(NSLocalizedString("patient.today.summary.empty.title", comment: "No dose records title"))
                                .font(.title3.weight(.bold))
                                .foregroundStyle(.primary)
                            Text(NSLocalizedString("patient.today.summary.empty.message", comment: "No dose records message"))
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Color.readableSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .accessibilityIdentifier("PatientTodayEmptyRecordState")
            }

            ForEach(lateSlots, id: \.rawValue) { slot in
                if let summary = summaries[slot] {
                    lateRecordCard(slot: slot, summary: summary)
                        .id(slot.rawValue)
                }
            }

            ForEach(completedSlots, id: \.rawValue) { slot in
                if let summary = summaries[slot] {
                    expandableCard(
                        slot: slot,
                        icon: "checkmark.circle.fill",
                        color: summary.isLate ? PatientUI.orange : PatientUI.teal,
                        title: summary.isLate
                            ? NSLocalizedString("patient.today.summary.late", comment: "Late dose")
                            : NSLocalizedString("patient.today.summary.taken", comment: "Taken"),
                        detail: completedDetail(slot: slot, summary: summary)
                    )
                    .id(slot.rawValue)
                }
            }
        }
    }

    private var completedSlots: [NotificationSlot] {
        slots.filter { summaries[$0]?.aggregateStatus == .taken }
    }

    private var lateSlots: [NotificationSlot] {
        slots.filter { slot in
            guard slot != activeSlot, let summary = summaries[slot] else { return false }
            return summary.aggregateStatus != .taken
                && summary.remainingCount > 0
                && summary.isLate
                && summary.isWithinRecordingWindow
        }
    }

    private func completedDetail(slot: NotificationSlot, summary: PatientTodayViewModel.SlotSummary) -> String {
        let actual = summary.takenAt.map(timeText) ?? summary.slotTime
        return String(format: NSLocalizedString("patient.today.summary.completed.detail", comment: "Completed detail"), slotTitle(slot), actual)
    }

    private func doses(for slot: NotificationSlot) -> [ScheduleDoseDTO] {
        slotSections.first { $0.slot == slot }?.items ?? []
    }

    private func lateRecordCard(
        slot: NotificationSlot,
        summary: PatientTodayViewModel.SlotSummary
    ) -> some View {
        PatientCard(accent: PatientUI.orange) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    Image(systemName: "clock.badge.exclamationmark.fill")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 54, height: 54)
                        .background(PatientUI.orange, in: Circle())

                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("patient.today.summary.late.unrecorded", comment: "Late unrecorded dose"))
                            .font(.title3.weight(.bold))
                            .foregroundStyle(PatientUI.orange)
                        Text(String(
                            format: NSLocalizedString("patient.today.summary.late.detail", comment: "Late slot detail"),
                            slotTitle(slot),
                            summary.slotTime,
                            delayText(summary.delaySeconds)
                        ))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.readableSecondaryText)
                    }
                    Spacer(minLength: 0)
                }

                Text(NSLocalizedString("patient.today.summary.late.guide", comment: "Late recording guide"))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: 10) {
                    ForEach(unrecordedDoses(for: slot)) { dose in
                        SlotMedicationRow(
                            dose: dose,
                            isInventoryInsufficient: isOutOfStock(dose.medicationId)
                        )
                        .contentShape(Rectangle())
                        .onTapGesture { onPresentDetail(dose) }
                    }
                }

                Button {
                    onBulkRecord(slot)
                } label: {
                    Label(
                        String(
                            format: NSLocalizedString("patient.today.slot.bulk.button.actual", comment: "Record now"),
                            timeText(summary.currentTime)
                        ),
                        systemImage: "checkmark.circle.fill"
                    )
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 62)
                    .background(PatientUI.orange, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isUpdating || !summary.isWithinRecordingWindow || !summary.hasRecordableInventory)
                .opacity(isUpdating || !summary.isWithinRecordingWindow || !summary.hasRecordableInventory ? 0.55 : 1)
                .accessibilityIdentifier("PatientTodayLateRecordButton-\(slot.rawValue)")
            }
        }
    }

    private func unrecordedDoses(for slot: NotificationSlot) -> [ScheduleDoseDTO] {
        doses(for: slot).filter { $0.effectiveStatus != .taken }
    }

    private func expandableCard(
        slot: NotificationSlot,
        icon: String,
        color: Color,
        title: String,
        detail: String
    ) -> some View {
        let isExpanded = expandedSlots.contains(slot)

        return PatientCard(accent: color) {
            VStack(spacing: 12) {
                Button {
                    withAnimation(.easeInOut(duration: 0.22)) {
                        if isExpanded {
                            expandedSlots.remove(slot)
                        } else {
                            expandedSlots.insert(slot)
                        }
                    }
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: icon)
                            .font(.system(size: 28, weight: .bold))
                            .foregroundStyle(color)
                            .frame(width: 52, height: 52)
                            .background(color.opacity(0.12), in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text(title)
                                .font(.title3.weight(.bold))
                                .foregroundStyle(Color.primary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.78)
                            Text(detail)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(Color.readableSecondaryText)
                            Text(NSLocalizedString(
                                isExpanded ? "patient.today.summary.hide" : "patient.today.summary.show",
                                comment: "Toggle medication list"
                            ))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(color)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        Image(systemName: "chevron.down")
                            .font(.headline.weight(.bold))
                            .rotationEffect(.degrees(isExpanded ? 180 : 0))
                        .foregroundStyle(color)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("PatientTodaySummaryToggle-\(slot.rawValue)")

                if isExpanded {
                    Divider()

                    VStack(spacing: 10) {
                        ForEach(doses(for: slot)) { dose in
                            SlotMedicationRow(
                                dose: dose,
                                isInventoryInsufficient: dose.effectiveStatus != .taken && isOutOfStock(dose.medicationId)
                            )
                            .contentShape(Rectangle())
                            .onTapGesture { onPresentDetail(dose) }
                        }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        }
    }
}

private struct PatientInventoryWarningCard: View {
    let warning: PatientInventoryWarning

    var body: some View {
        PatientCard(accent: PatientUI.red) {
            HStack(alignment: .center, spacing: 14) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(PatientUI.red, in: Circle())

                VStack(alignment: .leading, spacing: 6) {
                    Text(NSLocalizedString("patient.today.inventory.warning.title", comment: "Inventory warning title"))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(PatientUI.red)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(warning.message)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("PatientTodayInventoryWarningCard")
    }
}

private struct PrnMedicationListView: View {
    let medications: [MedicationDTO]
    let isDisabled: Bool
    let onRecordConfirmed: (MedicationDTO, @escaping () -> Void) -> Void
    @State private var showingConfirm = false
    @State private var selectedMedication: MedicationDTO?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            PatientScreenBackground()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    Text(NSLocalizedString("patient.today.prn.list.title", comment: "PRN list title"))
                        .font(.title2.weight(.bold))
                        .padding(.top, 4)

                    ForEach(medications) { medication in
                        PrnMedicationCard(
                            medication: medication,
                            isDisabled: isDisabled,
                            onRecord: {
                                selectedMedication = medication
                                showingConfirm = true
                            }
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 132)
            }
        }
        .navigationTitle(NSLocalizedString("patient.today.prn.screen.title", comment: "PRN screen title"))
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if isDisabled {
                SchedulingRefreshOverlay()
            }
        }
        .alert(
            NSLocalizedString("patient.today.prn.confirm.title", comment: "PRN confirm title"),
            isPresented: $showingConfirm,
            presenting: selectedMedication
        ) { medication in
            Button(NSLocalizedString("patient.today.prn.confirm.action", comment: "PRN confirm action")) {
                onRecordConfirmed(medication) {
                    dismiss()
                }
                selectedMedication = nil
            }
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {
                selectedMedication = nil
            }
        } message: { medication in
            Text(
                String(
                    format: NSLocalizedString("patient.today.prn.confirm.message", comment: "PRN confirm message"),
                    medication.name
                )
            )
        }
    }
}

private struct PlannedSectionsView: View {
    let slotSections: [SlotSection]
    let slotSummaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onBulkRecord: (NotificationSlot) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let isOutOfStock: (String) -> Bool

    var body: some View {
        ForEach(slotSections) { section in
            Section {
                if let slot = section.slot, let summary = slotSummaries[slot] {
                    SlotCardView(
                        slot: slot,
                        doses: section.items,
                        summary: summary,
                        slotColor: slotColor(slot),
                        slotTitle: slotTitle(slot),
                        isUpdating: false,
                        isOutOfStock: isOutOfStock,
                        onRecord: { onBulkRecord(slot) },
                        onPresentDetail: onPresentDetail
                    )
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                } else {
                    // Fallback for "other" slot — keep per-dose rows
                    ForEach(section.items) { dose in
                        PatientTodayRow(
                            dose: dose,
                            timeText: timeText(dose.scheduledAt),
                            onRecord: { onConfirmDose(dose) },
                            isHighlighted: shouldHighlight(dose),
                            slotColor: slotColor(section.slot),
                            isOutOfStock: isOutOfStock(dose.medicationId)
                        )
                        .id(dose.key)
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                        .onTapGesture { onPresentDetail(dose) }
                    }
                }
            } header: {
                SlotHeaderView(
                    slot: section.slot,
                    slotColor: slotColor,
                    slotTitle: slotTitle
                )
            }
        }
    }
}

// MARK: - Slot Card View (Bulk Recording)

private struct SlotCardView: View {
    let slot: NotificationSlot
    let doses: [ScheduleDoseDTO]
    let summary: PatientTodayViewModel.SlotSummary
    let slotColor: Color
    let slotTitle: String
    let isUpdating: Bool
    let isOutOfStock: (String) -> Bool
    let onRecord: () -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(slotColor)
                    .frame(width: 16, height: 16)
                    .padding(.top, 7)
                VStack(alignment: .leading, spacing: 4) {
                    Text(slotTitle)
                        .font(.title2.weight(.bold))
                    Text(summary.slotTime)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 8) {
                    statusBadge
                    if summary.hasInsufficientInventory {
                        Text(NSLocalizedString("patient.today.inventory.insufficient.badge", comment: "Insufficient inventory badge"))
                            .font(.subheadline.weight(.bold))
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)
                            .background(PatientUI.red.opacity(0.16))
                            .foregroundStyle(PatientUI.red)
                            .clipShape(Capsule())
                    }
                    if summary.remainingCount > 0 {
                        Text(String(format: NSLocalizedString("patient.today.slot.bulk.remaining", comment: "Remaining"), summary.remainingCount))
                            .font(.subheadline.weight(.bold))
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)
                            .background(Color.orange.opacity(0.16))
                            .clipShape(Capsule())
                    }
                }
            }

            ForEach(doses) { dose in
                SlotMedicationRow(
                    dose: dose,
                    isInventoryInsufficient: isDoseInventoryInsufficient(dose)
                )
                    .onTapGesture { onPresentDetail(dose) }
            }

            Text(String(
                format: NSLocalizedString("patient.today.slot.bulk.summary", comment: "Summary"),
                AppConstants.formatDecimal(summary.totalPills),
                "\(summary.medCount)"
            ))
            .font(.body.weight(.semibold))
            .foregroundStyle(.secondary)

            Button(action: onRecord) {
                Label(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record"), systemImage: "checkmark.circle.fill")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 70)
                    .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(summary.remainingCount == 0 || isUpdating || !summary.isWithinRecordingWindow || !summary.hasRecordableInventory)
            .opacity(summary.remainingCount == 0 || isUpdating || !summary.isWithinRecordingWindow || !summary.hasRecordableInventory ? 0.55 : 1)
            .accessibilityIdentifier("SlotBulkRecordButton")
            .accessibilityLabel(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record"))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 3)
                .fill(slotColor)
                .frame(width: 6)
                .padding(.vertical, 14)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(slotColor.opacity(0.45), lineWidth: 1.5)
        }
        .shadow(color: PatientUI.cardShadow, radius: 12, y: 5)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(slotTitle) \(summary.medCount)種類")
    }

    private var statusBadge: some View {
        let (text, bgColor) = statusBadgeValues
        return Text(text)
            .font(.subheadline.weight(.bold))
            .padding(.vertical, 6)
            .padding(.horizontal, 10)
            .background(bgColor)
            .clipShape(Capsule())
    }

    private var statusBadgeValues: (String, Color) {
        switch summary.aggregateStatus {
        case .taken:
            return (NSLocalizedString("patient.today.status.taken", comment: "Taken"), PatientUI.teal.opacity(0.15))
        case .missed:
            return (NSLocalizedString("patient.today.status.missed", comment: "Missed"), PatientUI.red.opacity(0.15))
        case .pending:
            return (NSLocalizedString("patient.today.status.pending", comment: "Pending"), Color.primary.opacity(0.06))
        }
    }

    private func isDoseInventoryInsufficient(_ dose: ScheduleDoseDTO) -> Bool {
        guard dose.effectiveStatus != .taken else { return false }
        return isOutOfStock(dose.medicationId)
    }
}

private struct SlotMedicationRow: View {
    let dose: ScheduleDoseDTO
    var isInventoryInsufficient = false

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(medicationDisplayName)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(shouldHighlightAsProblem ? Color.red : Color.primary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                Text(String(
                    format: NSLocalizedString("patient.today.slot.bulk.perDose", comment: "Per dose"),
                    AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)
                ))
                .font(.body)
                .foregroundStyle(.secondary)

                if isInventoryInsufficient {
                    Text(NSLocalizedString("patient.today.inventory.insufficient.badge", comment: "Insufficient inventory badge"))
                        .font(.subheadline.weight(.bold))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 10)
                        .background(PatientUI.red.opacity(0.16))
                        .foregroundStyle(PatientUI.red)
                        .clipShape(Capsule())
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)
            Spacer()
            if isInventoryInsufficient {
                Image(systemName: "exclamationmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(PatientUI.red)
            } else if let status = dose.effectiveStatus {
                doseStatusIndicator(status)
            }
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var shouldHighlightAsProblem: Bool {
        isInventoryInsufficient
    }

    private var medicationDisplayName: String {
        let trimmed = dose.medicationSnapshot.dosageText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "不明" {
            return dose.medicationSnapshot.name
        }
        return "\(dose.medicationSnapshot.name) \(trimmed)"
    }

    @ViewBuilder
    private func doseStatusIndicator(_ status: DoseStatusDTO) -> some View {
        switch status {
        case .taken:
            Image(systemName: "checkmark.circle.fill")
                .font(.title2)
                .foregroundStyle(.green)
        case .missed:
            Image(systemName: "exclamationmark.circle.fill")
                .font(.title2)
                .foregroundStyle(PatientUI.orange)
        case .pending:
            Image(systemName: "circle")
                .font(.title2)
                .foregroundStyle(.secondary)
        }
    }
}

private struct DoseStatusSectionView: View {
    let titleKey: String
    let items: [ScheduleDoseDTO]
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let isOutOfStock: (String) -> Bool

    var body: some View {
        if items.isEmpty {
            EmptyView()
        } else {
            Section {
                ForEach(items) { dose in
                    PatientTodayRow(
                        dose: dose,
                        timeText: timeText(dose.scheduledAt),
                        onRecord: { onConfirmDose(dose) },
                        isHighlighted: shouldHighlight(dose),
                        slotColor: nil,
                        isOutOfStock: isOutOfStock(dose.medicationId)
                    )
                    .id(dose.key)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                    .onTapGesture { onPresentDetail(dose) }
                }
            } header: {
                Text(NSLocalizedString(titleKey, comment: "Dose section title"))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
                    .textCase(nil)
            }
        }
    }
}

private struct SlotHeaderView: View {
    let slot: NotificationSlot?
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(slotColor(slot))
                .frame(width: 14, height: 14)
            Text(slotTitle(slot))
                .font(.title3.weight(.semibold))
                .foregroundStyle(.primary)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 16)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(slotColor(slot).opacity(0.18))
        )
        .textCase(nil)
    }
}

private struct PatientTodayDoseDetailView: View {
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.japaneseLocale
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    let dose: ScheduleDoseDTO
    let medication: MedicationDTO?
    let isLoading: Bool
    let errorMessage: String?
    let onRetry: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    headerCard
                    notesCard
                    intakeCard

                    if let errorMessage {
                        ErrorStateView(message: errorMessage)
                        Button(NSLocalizedString("common.retry", comment: "Retry")) {
                            onRetry()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
                .padding(16)
            }
            .navigationTitle(dose.medicationSnapshot.name)
            .navigationBarTitleDisplayMode(.inline)
            .overlay {
                if isLoading {
                    ZStack {
                        Color.black.opacity(AppConstants.overlayOpacity)
                            .ignoresSafeArea()
                        LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                            .padding(16)
                            .glassEffect(.regular, in: .rect(cornerRadius: 16))
                    }
                }
            }
        }
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(dose.medicationSnapshot.name)
                .font(.title.weight(.bold))
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
            Text(dose.medicationSnapshot.dosageText)
                .font(.title3)
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Image(systemName: "clock")
                    .foregroundStyle(.secondary)
                Text(Self.dateFormatter.string(from: dose.scheduledAt))
                    .font(.subheadline.weight(.semibold))
            }
            if let statusText = statusText {
                Text(statusText)
                    .font(.caption.weight(.semibold))
                    .padding(.vertical, 4)
                    .padding(.horizontal, 8)
                    .background(statusBackground)
                    .clipShape(Capsule())
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }

    private var notesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("medication.form.section.notes", comment: "Notes section title"))
                .font(.headline)
            Text(notesText)
                .font(.body)
                .foregroundStyle(notesForeground)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.primary.opacity(0.04))
                )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }

    private var intakeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("patient.today.doseCount.label", comment: "Dose count label"))
                .font(.headline)
            Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
                .font(.title2.weight(.bold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }

    private var notesText: String {
        let trimmed = medication?.notes?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmed.isEmpty {
            return NSLocalizedString("medication.detail.notes.empty", comment: "Empty notes")
        }
        return trimmed
    }

    private var notesForeground: Color {
        let trimmed = medication?.notes?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? .secondary : .primary
    }

    private var statusText: String? {
        switch dose.effectiveStatus {
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

    private var statusBackground: Color {
        switch dose.effectiveStatus {
        case .missed:
            return Color.red.opacity(0.15)
        case .taken:
            return Color.green.opacity(0.15)
        case .pending, .none:
            return Color.primary.opacity(0.06)
        }
    }
}

private struct PatientTodayRow: View {
    let dose: ScheduleDoseDTO
    let timeText: String
    let onRecord: () -> Void
    let isHighlighted: Bool
    let slotColor: Color?
    var isOutOfStock: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(timeText)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                    Text(medicationDisplayName)
                        .font(.title.weight(.bold))
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                    if shouldShowDoseCount {
                        Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }
                    if let noteText, !noteText.isEmpty {
                        Text(noteText)
                            .font(.body)
                            .foregroundStyle(.secondary)
                    }
                    if isOutOfStock {
                        Text(NSLocalizedString("patient.today.outOfStock", comment: "Out of stock"))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.vertical, 4)
                            .padding(.horizontal, 10)
                            .background(Color.red)
                            .clipShape(Capsule())
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
                Spacer()
                statusMarker(for: dose.effectiveStatus)
            }

            if shouldShowRecordButton(for: dose.effectiveStatus) {
                Button(action: onRecord) {
                    Label(NSLocalizedString("patient.today.taken.button", comment: "Taken"), systemImage: "checkmark.circle.fill")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 66)
                        .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isOutOfStock)
                .opacity(isOutOfStock ? 0.55 : 1)
                .accessibilityLabel(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
            }
        }
        .padding(18)
        .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(alignment: .leading) {
            if let slotColor {
                RoundedRectangle(cornerRadius: 3)
                    .fill(slotColor)
                    .frame(width: 6)
                    .padding(.vertical, 14)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(isMissed ? PatientUI.red.opacity(0.35) : PatientUI.cardStroke, lineWidth: 1)
        )
        .shadow(color: PatientUI.cardShadow, radius: 10, y: 4)
        .todaySlotHighlight(isHighlighted)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private var isMissed: Bool {
        dose.effectiveStatus == .missed
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
        if shouldShowDoseCount {
            parts.append(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)))
        }
        if let statusText = statusText(for: dose.effectiveStatus) {
            parts.append(statusText)
        }
        if let noteText, !noteText.isEmpty {
            parts.append(noteText)
        }
        return parts.joined(separator: ", ")
    }

    private var shouldShowDoseCount: Bool {
        return true
    }

    private var noteText: String? {
        let trimmed = dose.medicationSnapshot.notes?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
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
    private func statusMarker(for status: DoseStatusDTO?) -> some View {
        switch status {
        case .taken:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(PatientUI.teal)
                .frame(width: 44, height: 44)
                .background(PatientUI.teal.opacity(0.12), in: Circle())
                .accessibilityLabel(NSLocalizedString("patient.today.status.taken", comment: "Taken"))
        case .pending, .missed:
            if let statusText = statusText(for: status) {
                Text(statusText)
                    .font(.body.weight(.bold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.vertical, 7)
                    .padding(.horizontal, 12)
                    .background(statusBackground(for: status))
                    .foregroundStyle(statusForeground(for: status))
                    .clipShape(Capsule())
            }
        case .none:
            EmptyView()
        }
    }

    private func statusForeground(for status: DoseStatusDTO?) -> Color {
        switch status {
        case .missed:
            return Color.red
        case .taken, .pending, .none:
            return Color.primary
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

    private func shouldShowRecordButton(for status: DoseStatusDTO?) -> Bool {
        switch status {
        case .pending, .none:
            return Date() >= recordAvailableFrom
        case .taken, .missed:
            return false
        }
    }

    private var recordAvailableFrom: Date {
        dose.scheduledAt.addingTimeInterval(-30 * 60)
    }
}

private struct PrnMedicationCard: View {
    let medication: MedicationDTO
    let isDisabled: Bool
    let onRecord: () -> Void
    @State private var recordTrigger = 0
    @State private var isPressed = false

    private var isInventoryInsufficient: Bool {
        medication.isInsufficientForDose
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                MedicationSymbolView(tint: PatientUI.orange)
                    .frame(width: 50, height: 50)
                VStack(alignment: .leading, spacing: 8) {
                    Text(prnMedicationDisplayName)
                        .font(.title.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.82)
                    Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(medication.doseCountPerIntake)))
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.secondary)
                    if let noteText, !noteText.isEmpty {
                        Text(noteText)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if isInventoryInsufficient {
                        Text(NSLocalizedString("patient.today.outOfStock", comment: "Out of stock"))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.vertical, 4)
                            .padding(.horizontal, 10)
                            .background(PatientUI.red)
                            .clipShape(Capsule())
                    }
                }
            }

            Button(action: handleRecord) {
                Label(NSLocalizedString("patient.today.prn.record.button", comment: "PRN record button"), systemImage: "checkmark.circle.fill")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 72)
                    .background(PatientUI.teal, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isDisabled || isInventoryInsufficient)
            .opacity(isDisabled || isInventoryInsufficient ? 0.55 : 1)
            .accessibilityLabel(NSLocalizedString("patient.today.prn.record.button", comment: "PRN record button"))
            .scaleEffect(isPressed ? 0.96 : 1.0)
            .animation(.easeInOut(duration: 0.18), value: isPressed)
            .sensoryFeedback(.success, trigger: recordTrigger)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PatientUI.cardBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(PatientUI.orange.opacity(0.32), lineWidth: 1.2)
        }
        .shadow(color: PatientUI.cardShadow, radius: 12, y: 5)
    }

    private var prnMedicationDisplayName: String {
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

    private func handleRecord() {
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
