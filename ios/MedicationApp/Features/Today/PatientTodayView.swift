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
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current
        return calendar
    }()
    let sessionStore: SessionStore
    @ObservedObject var viewModel: PatientTodayViewModel
    @Binding var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var showingConfirm = false
    @State private var showingPrnConfirm = false
    @State private var pendingScrollTarget: String?
    @State private var selectedDose: ScheduleDoseDTO?
    @State private var detailMedication: MedicationDTO?
    @State private var isDetailLoading = false
    @State private var detailErrorMessage: String?

    var body: some View {
        PatientTodayBaseView(
            viewModel: viewModel,
            pendingScrollTarget: $pendingScrollTarget,
            slotSections: slotSections,
            missedItems: missedItems,
            takenItems: takenItems,
            onConfirmDose: { viewModel.confirmRecord(for: $0) },
            onPresentDetail: presentDetail,
            onConfirmPrn: { viewModel.confirmPrnRecord(for: $0) },
            timeText: { viewModel.timeText(for: $0) },
            shouldHighlight: shouldHighlight,
            slotColor: slotColor,
            slotTitle: slotTitle
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
        switch slot {
        case .morning:
            return Color.orange
        case .noon:
            return Color.blue
        case .evening:
            return Color.purple
        case .bedtime:
            return Color.indigo
        case .none:
            return Color.gray
        }
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

    private func slot(for dose: ScheduleDoseDTO) -> NotificationSlot? {
        NotificationSlot.from(date: dose.scheduledAt)
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
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onConfirmPrn: (MedicationDTO) -> Void
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String

    var body: some View {
        baseView
    }

    private var baseView: some View {
        ZStack(alignment: .top) {
            Color.white
                .ignoresSafeArea()
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
                .background(.ultraThinMaterial)
                .clipShape(Capsule())
                .shadow(radius: 4)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
                .accessibilityLabel(toastMessage)
        }
    }

    @ViewBuilder
    private var updatingOverlay: some View {
        if viewModel.isUpdating {
            ZStack {
                Color.black.opacity(0.2)
                    .ignoresSafeArea()
                VStack {
                    Spacer()
                    LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                        .padding(16)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .shadow(radius: 6)
                    Spacer()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
    }

    private var content: some View {
        Group {
            if viewModel.isLoading {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else if viewModel.items.isEmpty && viewModel.prnMedications.isEmpty {
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
                        onConfirmDose: onConfirmDose,
                        onPresentDetail: onPresentDetail,
                        onConfirmPrn: onConfirmPrn,
                        timeText: timeText,
                        shouldHighlight: shouldHighlight,
                        slotColor: slotColor,
                        slotTitle: slotTitle
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
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void
    let onConfirmPrn: (MedicationDTO) -> Void
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String

    var body: some View {
        List {
            PlannedSectionsView(
                slotSections: slotSections,
                timeText: timeText,
                shouldHighlight: shouldHighlight,
                slotColor: slotColor,
                slotTitle: slotTitle,
                onConfirmDose: onConfirmDose,
                onPresentDetail: onPresentDetail
            )
            DoseStatusSectionView(
                titleKey: "patient.today.section.missed",
                items: missedItems,
                timeText: timeText,
                shouldHighlight: shouldHighlight,
                onConfirmDose: onConfirmDose,
                onPresentDetail: onPresentDetail
            )
            DoseStatusSectionView(
                titleKey: "patient.today.section.taken",
                items: takenItems,
                timeText: timeText,
                shouldHighlight: shouldHighlight,
                onConfirmDose: onConfirmDose,
                onPresentDetail: onPresentDetail
            )
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.white)
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
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.white)
        .navigationTitle(NSLocalizedString("patient.today.prn.section.title", comment: "PRN section"))
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if isDisabled {
                ZStack {
                    Color.black.opacity(0.2)
                        .ignoresSafeArea()
                    VStack {
                        Spacer()
                        LoadingStateView(message: NSLocalizedString("common.updating", comment: "Updating"))
                            .padding(16)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .shadow(radius: 6)
                        Spacer()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
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
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let slotColor: (NotificationSlot?) -> Color
    let slotTitle: (NotificationSlot?) -> String
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void

    var body: some View {
        ForEach(slotSections) { section in
            Section {
                ForEach(section.items) { dose in
                    PatientTodayRow(
                        dose: dose,
                        timeText: timeText(dose.scheduledAt),
                        onRecord: { onConfirmDose(dose) },
                        isHighlighted: shouldHighlight(dose),
                        slotColor: slotColor(section.slot)
                    )
                    .id(dose.key)
                    .listRowSeparator(.hidden)
                    .onTapGesture { onPresentDetail(dose) }
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

private struct DoseStatusSectionView: View {
    let titleKey: String
    let items: [ScheduleDoseDTO]
    let timeText: (Date) -> String
    let shouldHighlight: (ScheduleDoseDTO) -> Bool
    let onConfirmDose: (ScheduleDoseDTO) -> Void
    let onPresentDetail: (ScheduleDoseDTO) -> Void

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
                        slotColor: nil
                    )
                    .id(dose.key)
                    .listRowSeparator(.hidden)
                    .onTapGesture { onPresentDetail(dose) }
                }
            } header: {
                Text(NSLocalizedString(titleKey, comment: "Dose section title"))
                    .font(.headline)
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
        HStack(spacing: 8) {
            Circle()
                .fill(slotColor(slot))
                .frame(width: 10, height: 10)
            Text(slotTitle(slot))
                .font(.headline)
                .foregroundStyle(.primary)
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(slotColor(slot).opacity(0.18))
        )
        .textCase(nil)
    }
}

private struct PatientTodayDoseDetailView: View {
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ja_JP")
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
                        Color.black.opacity(0.2)
                            .ignoresSafeArea()
                        LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                            .padding(16)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .shadow(radius: 6)
                    }
                }
            }
        }
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(dose.medicationSnapshot.name)
                .font(.title2.weight(.semibold))
            Text(dose.medicationSnapshot.dosageText)
                .font(.body)
                .foregroundColor(.secondary)
            HStack(spacing: 8) {
                Image(systemName: "clock")
                    .foregroundColor(.secondary)
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
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
    }

    private var notesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("medication.form.section.notes", comment: "Notes section title"))
                .font(.headline)
            Text(notesText)
                .font(.body)
                .foregroundColor(notesForeground)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.white)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Color.black.opacity(0.08), lineWidth: 1)
                )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
    }

    private var intakeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("服用数/回")
                .font(.headline)
            Text("1回\(dose.medicationSnapshot.doseCountPerIntake)錠")
                .font(.title2.weight(.bold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
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
            return Color.green.opacity(0.12)
        case .pending, .none:
            return Color(.secondarySystemBackground)
        }
    }
}

private struct PatientTodayRow: View {
    let dose: ScheduleDoseDTO
    let timeText: String
    let onRecord: () -> Void
    let isHighlighted: Bool
    let slotColor: Color?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(timeText)
                        .font(.headline)
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                    Text(dose.medicationSnapshot.name)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(isMissed ? Color.red : Color.primary)
                    Text("1回\(dose.medicationSnapshot.doseCountPerIntake)錠")
                        .font(.body)
                        .foregroundColor(.secondary)
                    if let noteText, !noteText.isEmpty {
                        Text(noteText)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if let statusText = statusText(for: dose.effectiveStatus) {
                    Text(statusText)
                        .font(.caption.weight(.semibold))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(statusBackground(for: dose.effectiveStatus))
                        .foregroundStyle(statusForeground(for: dose.effectiveStatus))
                        .clipShape(Capsule())
                }
            }

            if shouldShowRecordButton(for: dose.effectiveStatus) {
                Button(action: onRecord) {
                    Text(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
                        .font(.title3.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .accessibilityLabel(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(backgroundColor(for: dose.effectiveStatus))
        )
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
        .shadow(color: Color.black.opacity(0.06), radius: 8, y: 3)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private var isMissed: Bool {
        dose.effectiveStatus == .missed
    }

    private var accessibilitySummary: String {
        var parts = [timeText, dose.medicationSnapshot.name, "1回\(dose.medicationSnapshot.doseCountPerIntake)錠"]
        if let statusText = statusText(for: dose.effectiveStatus) {
            parts.append(statusText)
        }
        if let noteText, !noteText.isEmpty {
            parts.append(noteText)
        }
        return parts.joined(separator: ", ")
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
            return Color.green.opacity(0.12)
        case .pending:
            return Color(.secondarySystemBackground)
        case .none:
            return Color(.secondarySystemBackground)
        }
    }

    private func backgroundColor(for status: DoseStatusDTO?) -> Color {
        switch status {
        case .missed:
            return Color.red.opacity(0.08)
        case .taken:
            return Color.green.opacity(0.06)
        case .pending, .none:
            return Color(.systemBackground)
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

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text(medication.name)
                    .font(.title3.weight(.semibold))
                Text("1回\(medication.doseCountPerIntake)錠")
                    .font(.body)
                    .foregroundColor(.secondary)
                if let noteText, !noteText.isEmpty {
                    Text(noteText)
                        .font(.callout)
                        .foregroundColor(.secondary)
                }
            }

            Button(action: handleRecord) {
                Text(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
                    .font(.title3.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(isDisabled)
            .accessibilityLabel(NSLocalizedString("patient.today.taken.button", comment: "Taken"))
            .scaleEffect(isPressed ? 0.96 : 1.0)
            .animation(.easeInOut(duration: 0.18), value: isPressed)
            .sensoryFeedback(.success, trigger: recordTrigger)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.systemBackground))
        )
        .shadow(color: Color.black.opacity(0.06), radius: 8, y: 3)
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

