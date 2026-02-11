import SwiftUI

struct PatientTodayView: View {
    private let sessionStore: SessionStore
    @StateObject private var viewModel: PatientTodayViewModel
    @Binding private var deepLinkTarget: NotificationDeepLinkTarget?

    init(
        sessionStore: SessionStore? = nil,
        deepLinkTarget: Binding<NotificationDeepLinkTarget?> = .constant(nil)
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        _deepLinkTarget = deepLinkTarget
        let baseURL = SessionStore.resolveBaseURL()
        let apiClient = APIClient(baseURL: baseURL, sessionStore: store)
        let viewModel = PatientTodayViewModel(apiClient: apiClient)
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    var body: some View {
        PatientTodayRootView(
            sessionStore: sessionStore,
            viewModel: viewModel,
            deepLinkTarget: $deepLinkTarget
        )
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
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var showingConfirm = false
    @State private var showingPrnConfirm = false
    @State private var showingBulkConfirm = false
    @State private var pendingScrollTarget: String?
    @State private var selectedDose: ScheduleDoseDTO?
    @State private var detailMedication: MedicationDTO?
    @State private var isDetailLoading = false
    @State private var detailErrorMessage: String?

    private let preferencesStore = NotificationPreferencesStore()

    var body: some View {
        PatientTodayBaseView(
            viewModel: viewModel,
            pendingScrollTarget: $pendingScrollTarget,
            slotSections: slotSections,
            missedItems: missedItems,
            takenItems: takenItems,
            slotSummaries: viewModel.slotSummaries,
            onConfirmDose: { viewModel.confirmRecord(for: $0) },
            onBulkRecord: { viewModel.confirmBulkRecord(for: $0) },
            onPresentDetail: presentDetail,
            onConfirmPrn: { viewModel.confirmPrnRecord(for: $0) },
            timeText: { viewModel.timeText(for: $0) },
            shouldHighlight: shouldHighlight,
            slotColor: slotColor,
            slotTitle: slotTitle,
            isOutOfStock: { viewModel.isMedicationOutOfStock($0) }
        )
        .modifier(
            PatientTodayLifecycleModifier(
                viewModel: viewModel,
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
            isPresented: $showingBulkConfirm,
            presenting: viewModel.confirmSlot
        ) { _ in
            Button(NSLocalizedString("patient.today.slot.bulk.confirm.record", comment: "Record")) {
                viewModel.executeBulkRecord()
            }
            Button(NSLocalizedString("patient.today.slot.bulk.confirm.cancel", comment: "Cancel"), role: .cancel) {
                viewModel.confirmSlot = nil
            }
        } message: { slot in
            Text(bulkConfirmMessage(for: slot))
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
        .toolbar {
            if !viewModel.prnMedications.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(
                        destination: PrnMedicationListView(
                            medications: viewModel.prnMedications,
                            isDisabled: viewModel.isUpdating || viewModel.isPrnSubmitting,
                            onRecordConfirmed: { medication, onSuccess in
                                viewModel.recordPrnDose(for: medication, onSuccess: onSuccess)
                            }
                        )
                    ) {
                        Text(NSLocalizedString("patient.today.prn.section.title", comment: "PRN section"))
                            .font(.title3.weight(.semibold))
                            .padding(.horizontal, 20)
                            .padding(.vertical, 14)
                    }
                    .accessibilityIdentifier("PatientTodayPrnListButton")
                }
            }
        }
        .sensoryFeedback(.success, trigger: viewModel.toastMessage)
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

    private func bulkConfirmMessage(for slot: NotificationSlot) -> String {
        let summary = viewModel.slotSummaries[slot]
        return String(
            format: NSLocalizedString("patient.today.slot.bulk.confirm.message", comment: "Bulk confirm message"),
            slotTitle(for: slot),
            summary?.slotTime ?? "",
            "\(summary?.medCount ?? 0)",
            AppConstants.formatDecimal(summary?.totalPills ?? 0)
        )
    }

    private func slot(for dose: ScheduleDoseDTO) -> NotificationSlot? {
        NotificationSlot.from(date: dose.scheduledAt, slotTimes: preferencesStore.slotTimesMap())
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
        pendingScrollTarget = viewModel.handleDeepLink(target)
        deepLinkTarget = nil
    }
}

private struct PatientTodayBaseView: View {
    @ObservedObject var viewModel: PatientTodayViewModel
    @Binding var pendingScrollTarget: String?
    let slotSections: [SlotSection]
    let missedItems: [ScheduleDoseDTO]
    let takenItems: [ScheduleDoseDTO]
    let slotSummaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onBulkRecord: (NotificationSlot) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onConfirmPrn: (MedicationDTO) -> Void
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
            toastView
            updatingOverlay
        }
    }

    @ViewBuilder
    private var toastView: some View {
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
            } else if let errorMessage = viewModel.errorMessage {
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
                        onConfirmDose: onConfirmDose,
                        onBulkRecord: onBulkRecord,
                        onPresentDetail: onPresentDetail,
                        onConfirmPrn: onConfirmPrn,
                        timeText: timeText,
                        shouldHighlight: shouldHighlight,
                        slotColor: slotColor,
                        slotTitle: slotTitle,
                        isOutOfStock: isOutOfStock
                    )
                    .onChange(of: pendingScrollTarget) { _, target in
                        guard let target else { return }
                        withAnimation(.easeInOut) {
                            proxy.scrollTo(target, anchor: .top)
                        }
                        pendingScrollTarget = nil
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
    let onHandleDeepLink: () -> Void

    func body(content: Content) -> some View {
        content
            .onAppear {
                viewModel.handleAppear()
                onHandleDeepLink()
            }
            .onDisappear {
                viewModel.handleDisappear()
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
    let viewModel: PatientTodayViewModel
    let slotSections: [SlotSection]
    let missedItems: [ScheduleDoseDTO]
    let takenItems: [ScheduleDoseDTO]
    let slotSummaries: [NotificationSlot: PatientTodayViewModel.SlotSummary]
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onBulkRecord: (NotificationSlot) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onConfirmPrn: (MedicationDTO) -> Void
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String
    let isOutOfStock: (String) -> Bool

    var body: some View {
        List {
            PlannedSectionsView(
                slotSections: slotSections,
                slotSummaries: slotSummaries,
                timeText: timeText,
                shouldHighlight: shouldHighlight,
                slotColor: slotColor,
                slotTitle: slotTitle,
                onConfirmDose: onConfirmDose,
                onBulkRecord: onBulkRecord,
                onPresentDetail: onPresentDetail,
                isOutOfStock: isOutOfStock
            )
            DoseStatusSectionView(
                titleKey: "patient.today.section.taken",
                items: takenItems,
                timeText: timeText,
                shouldHighlight: shouldHighlight,
                onConfirmDose: onConfirmDose,
                onPresentDetail: onPresentDetail,
                isOutOfStock: isOutOfStock
            )
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .refreshable {
            viewModel.load(showLoading: false)
        }
        .safeAreaPadding(.bottom, 120)
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
        List {
            ForEach(medications) { medication in
                PrnMedicationCard(
                    medication: medication,
                    isDisabled: isDisabled,
                    onRecord: {
                        selectedMedication = medication
                        showingConfirm = true
                    }
                )
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle(NSLocalizedString("patient.today.prn.section.title", comment: "PRN section"))
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
                        onRecord: { onBulkRecord(slot) },
                        onPresentDetail: onPresentDetail
                    )
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
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
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
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
    let onRecord: () -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header: slot time + status badge + remaining
            HStack(spacing: 10) {
                Circle()
                    .fill(slotColor)
                    .frame(width: 14, height: 14)
                Text(slotTitle)
                    .font(.title3.weight(.semibold))
                Text(summary.slotTime)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                statusBadge
                if summary.remainingCount > 0 {
                    Text(String(format: NSLocalizedString("patient.today.slot.bulk.remaining", comment: "Remaining"), summary.remainingCount))
                        .font(.caption.weight(.semibold))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(Color.orange.opacity(0.15))
                        .clipShape(Capsule())
                }
            }

            // Medication rows
            ForEach(doses) { dose in
                SlotMedicationRow(dose: dose)
                    .onTapGesture { onPresentDetail(dose) }
            }

            // Summary line
            Text(String(
                format: NSLocalizedString("patient.today.slot.bulk.summary", comment: "Summary"),
                AppConstants.formatDecimal(summary.totalPills),
                "\(summary.medCount)"
            ))
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.secondary)

            // Bulk record button
            Button(action: onRecord) {
                Text(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record"))
                    .font(.title2.weight(.bold))
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 56)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(summary.remainingCount == 0 || isUpdating)
            .accessibilityIdentifier("SlotBulkRecordButton")
            .accessibilityLabel(NSLocalizedString("patient.today.slot.bulk.button", comment: "Bulk record"))
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 3)
                .fill(slotColor)
                .frame(width: 6)
                .padding(.vertical, 12)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(slotTitle) \(summary.medCount)種類")
    }

    @ViewBuilder
    private var statusBadge: some View {
        let text: String
        let bgColor: Color
        switch summary.aggregateStatus {
        case .taken:
            text = NSLocalizedString("patient.today.status.taken", comment: "Taken")
            bgColor = Color.green.opacity(0.15)
        case .missed:
            text = NSLocalizedString("patient.today.status.missed", comment: "Missed")
            bgColor = Color.red.opacity(0.15)
        case .pending:
            text = NSLocalizedString("patient.today.status.pending", comment: "Pending")
            bgColor = Color.primary.opacity(0.06)
        }
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.vertical, 4)
            .padding(.horizontal, 8)
            .background(bgColor)
            .clipShape(Capsule())
    }
}

private struct SlotMedicationRow: View {
    let dose: ScheduleDoseDTO

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text(medicationDisplayName)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(dose.effectiveStatus == .missed ? Color.red : Color.primary)
                Text(String(
                    format: NSLocalizedString("patient.today.slot.bulk.perDose", comment: "Per dose"),
                    AppConstants.formatDecimal(dose.medicationSnapshot.doseCountPerIntake)
                ))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            Spacer()
            if let status = dose.effectiveStatus {
                doseStatusIndicator(status)
            }
        }
        .padding(.vertical, 4)
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
                .foregroundStyle(.green)
        case .missed:
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundStyle(.red)
        case .pending:
            Image(systemName: "circle")
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
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .onTapGesture { onPresentDetail(dose) }
                }
            } header: {
                Text(NSLocalizedString(titleKey, comment: "Dose section title"))
                    .font(.title3.weight(.semibold))
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
        .padding(.vertical, 8)
        .padding(.horizontal, 14)
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
                VStack(alignment: .leading, spacing: 6) {
                    Text(timeText)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                    Text(medicationDisplayName)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
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
                Spacer()
                if let statusText = statusText(for: dose.effectiveStatus) {
                    Text(statusText)
                        .font(.subheadline.weight(.bold))
                        .padding(.vertical, 6)
                        .padding(.horizontal, 10)
                        .background(statusBackground(for: dose.effectiveStatus))
                        .foregroundStyle(statusForeground(for: dose.effectiveStatus))
                        .clipShape(Capsule())
                }
            }

            if shouldShowRecordButton(for: dose.effectiveStatus) {
                Button(action: onRecord) {
                    Text(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
                        .font(.title2.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 56)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(isOutOfStock)
                .accessibilityLabel(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
            }
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
        .overlay(alignment: .leading) {
            if let slotColor {
                RoundedRectangle(cornerRadius: 3)
                    .fill(slotColor)
                    .frame(width: 6)
                    .padding(.vertical, 12)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(isMissed ? Color.red.opacity(0.35) : Color.clear, lineWidth: 1)
        )
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

    private var isOutOfStock: Bool {
        medication.isOutOfStock
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text(prnMedicationDisplayName)
                    .font(.title2.weight(.bold))
                Text(String(format: NSLocalizedString("patient.today.doseCount.format", comment: "Dose count format"), AppConstants.formatDecimal(medication.doseCountPerIntake)))
                    .font(.title3)
                    .foregroundStyle(.secondary)
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

            Button(action: handleRecord) {
                Text(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
                    .font(.title2.weight(.bold))
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 56)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(isDisabled || isOutOfStock)
            .accessibilityLabel(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
            .scaleEffect(isPressed ? 0.96 : 1.0)
            .animation(.easeInOut(duration: 0.18), value: isPressed)
            .sensoryFeedback(.success, trigger: recordTrigger)
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
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

