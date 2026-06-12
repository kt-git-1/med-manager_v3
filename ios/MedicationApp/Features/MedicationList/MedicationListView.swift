import SwiftUI

struct MedicationListItem: Identifiable {
    let medication: MedicationDTO
    let name: String
    let scheduleText: String?
    let doseText: String

    var id: String { medication.id }
}

private enum MedicationListFilter: String, CaseIterable, Identifiable {
    case all
    case scheduled
    case prn
    case ended

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all:
            return NSLocalizedString("medication.list.filter.all", comment: "All medications filter")
        case .scheduled:
            return NSLocalizedString("medication.list.filter.scheduled", comment: "Scheduled medications filter")
        case .prn:
            return NSLocalizedString("medication.list.filter.prn", comment: "PRN medications filter")
        case .ended:
            return NSLocalizedString("medication.list.filter.ended", comment: "Ended medications filter")
        }
    }

    var systemImage: String {
        switch self {
        case .all:
            return "list.bullet"
        case .scheduled:
            return "clock.fill"
        case .prn:
            return "cross.case.fill"
        case .ended:
            return "calendar.badge.clock"
        }
    }
}

@MainActor
final class MedicationListViewModel: ObservableObject {
    @Published var items: [MedicationListItem] = []
    @Published var isLoading = false
    @Published var isUpdating = false
    @Published var errorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore
    private let preferencesStore: NotificationPreferencesStore

    init(apiClient: APIClient, sessionStore: SessionStore, preferencesStore: NotificationPreferencesStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.preferencesStore = preferencesStore
    }

    convenience init() {
        let sessionStore = SessionStore()
        let baseURL = SessionStore.resolveBaseURL()
        let prefs = NotificationPreferencesStore()
        self.init(
            apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
            sessionStore: sessionStore,
            preferencesStore: prefs
        )
    }

    func load(showLoading: Bool = true) {
        guard !isLoading, !isUpdating else { return }
        isLoading = showLoading
        isUpdating = !showLoading
        errorMessage = nil
        Task {
            defer {
                isLoading = false
                isUpdating = false
            }
            do {
                let patientId: String?
                if sessionStore.mode == .caregiver {
                    guard let selectedPatientId = currentPatientId() else {
                        items = []
                        errorMessage = nil
                        return
                    }
                    patientId = selectedPatientId
                } else {
                    patientId = nil
                }
                let medications = try await apiClient.fetchMedications(patientId: patientId)
                items = medications.map { medication in
                    MedicationListItem(
                        medication: medication,
                        name: medication.name,
                        scheduleText: buildScheduleText(medication),
                        doseText: buildDoseText(medication)
                    )
                }
            } catch {
                items = []
                if sessionStore.mode == .caregiver {
                    errorMessage = NSLocalizedString("caregiver.dataUnavailable.message", comment: "Caregiver data unavailable message")
                } else {
                    errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
                }
            }
        }
    }

    // MARK: - Dose text

    private func buildDoseText(_ medication: MedicationDTO) -> String {
        let count = AppConstants.formatDecimal(medication.doseCountPerIntake)
        return String(
            format: NSLocalizedString("medication.list.dose.format", comment: "Dose per intake"),
            count
        )
    }

    // MARK: - Schedule text

    private func buildScheduleText(_ medication: MedicationDTO) -> String? {
        if medication.isPrn {
            return nil
        }
        guard let times = medication.regimenTimes, !times.isEmpty else {
            return nil
        }
        let slotLabels = times.compactMap { slotLabel(for: $0) }
        let timePart = slotLabels.isEmpty ? times.joined(separator: "・") : slotLabels.joined(separator: "・")

        let daysOfWeek = medication.regimenDaysOfWeek ?? []
        if daysOfWeek.isEmpty {
            return String(
                format: NSLocalizedString("medication.list.schedule.daily.format", comment: "Daily schedule"),
                timePart
            )
        } else {
            let dayLabels = ScheduleDay.allCases
                .filter { daysOfWeek.contains($0.rawValue) }
                .map(\.shortLabel)
            let dayPart = dayLabels.joined(separator: "・")
            return String(
                format: NSLocalizedString("medication.list.schedule.weekly.format", comment: "Weekly schedule"),
                dayPart,
                timePart
            )
        }
    }

    private func slotLabel(for timeString: String) -> String? {
        let normalized = timeString.trimmingCharacters(in: .whitespacesAndNewlines)
        if let slot = ScheduleTimeSlot(rawValue: normalized) {
            return slot.label
        }
        for slot in ScheduleTimeSlot.allCases {
            let time = preferencesStore.slotTime(for: slot.notificationSlot)
            let configured = String(format: "%02d:%02d", time.hour, time.minute)
            if configured == normalized {
                return slot.label
            }
        }
        return nil
    }

    private func currentPatientId() -> String? {
        switch sessionStore.mode {
        case .caregiver:
            return sessionStore.currentPatientId
        case .patient:
            return nil
        case .none:
            return nil
        }
    }
}

struct MedicationListView: View {
    private static let listCalendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        return calendar
    }()

    private let sessionStore: SessionStore
    private let onOpenPatients: (() -> Void)?
    private let headerView: AnyView?
    private let patientName: String?
    @StateObject private var viewModel: MedicationListViewModel
    @EnvironmentObject private var toastPresenter: ToastPresenter
    @State private var showingCreate = false
    @State private var selectedMedication: MedicationDTO?
    @State private var selectedFilter: MedicationListFilter = .all

    init(
        sessionStore: SessionStore? = nil,
        onOpenPatients: (() -> Void)? = nil,
        headerView: AnyView? = nil,
        patientName: String? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.onOpenPatients = onOpenPatients
        self.headerView = headerView
        self.patientName = patientName
        let baseURL = SessionStore.resolveBaseURL()
        let prefs = NotificationPreferencesStore()
        if store.mode == .caregiver, let patientId = store.currentPatientId {
            prefs.switchPatient(patientId)
        }
        _viewModel = StateObject(
            wrappedValue: MedicationListViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: store),
                sessionStore: store,
                preferencesStore: prefs
            )
        )
    }

    var body: some View {
        ZStack {
            Group {
                if viewModel.isLoading {
                    centeredLoadingState
                } else if let errorMessage = viewModel.errorMessage {
                    if sessionStore.mode == .caregiver {
                        CaregiverDataUnavailableView(
                            message: errorMessage,
                            onRetry: { viewModel.load() },
                            onReturnToLogin: { sessionStore.returnToCaregiverLogin() }
                        )
                    } else {
                        ErrorStateView(message: errorMessage)
                    }
                } else if viewModel.items.isEmpty {
                    if sessionStore.mode == .caregiver {
                        CaregiverScreenBackground {
                            ScrollView {
                                LazyVStack(spacing: 16) {
                                    if let headerView {
                                        headerView
                                    }
                                    emptyMedicationHeader
                                    emptyMedicationCard
                                }
                                .padding(.horizontal, 20)
                                .padding(.top, headerView == nil ? 16 : 0)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                            .safeAreaPadding(.bottom, 120)
                        }
                    } else {
                        ZStack {
                            Color.clear
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                            VStack {
                                Spacer(minLength: 0)
                                VStack(spacing: 16) {
                                    Image(systemName: "pills")
                                        .font(.system(size: 44))
                                        .foregroundStyle(.secondary)
                                    EmptyStateView(
                                        title: NSLocalizedString("medication.list.empty.title", comment: "Empty list title"),
                                        message: NSLocalizedString("medication.list.empty.message", comment: "Empty list message")
                                    )
                                }
                                .padding(24)
                                .frame(maxWidth: .infinity)
                                .glassEffect(.regular, in: .rect(cornerRadius: 20))
                                .padding(.horizontal, 24)
                                Spacer(minLength: 120)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                } else {
                    let baseList = List {
                        if let headerView {
                            headerView
                                .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                        }
                        if sessionStore.mode == .caregiver {
                            medicationHeaderRow
                            medicationOverviewRow
                            medicationFilterRow
                        }
                        if sessionStore.mode == .caregiver {
                            if !displayScheduledItems.isEmpty {
                                Section {
                                    ForEach(displayScheduledItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    sectionHeader("medication.list.section.scheduled")
                                }
                                .listRowSeparator(.hidden)
                            }

                            if !displayPrnItems.isEmpty {
                                Section {
                                    ForEach(displayPrnItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    sectionHeader("medication.list.section.prn")
                                }
                                .listRowSeparator(.hidden)
                            }

                            if !displayExpiredScheduledItems.isEmpty {
                                Section {
                                    ForEach(displayExpiredScheduledItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    sectionHeader("medication.list.section.expired.scheduled")
                                }
                                .listRowSeparator(.hidden)
                            }

                            if !displayExpiredPrnItems.isEmpty {
                                Section {
                                    ForEach(displayExpiredPrnItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    sectionHeader("medication.list.section.expired.prn")
                                }
                                .listRowSeparator(.hidden)
                            }
                        } else {
                            Section {
                                ForEach(viewModel.items) { item in
                                    medicationRow(item)
                                }
                            } header: {
                                Text(NSLocalizedString("medication.list.section.title", comment: "Medication list section"))
                                    .font(.headline)
                                    .foregroundStyle(.secondary)
                                    .textCase(nil)
                            }
                            .listRowSeparator(.hidden)
                        }
                    }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
                    .background(CaregiverUI.background)
                    .safeAreaPadding(.bottom, 120)
                    .refreshable {
                        viewModel.load(showLoading: false)
                    }
                    let listWithInsets = headerView == nil ? AnyView(baseList.safeAreaPadding(.top)) : AnyView(baseList)
                    listWithInsets
                }
            }

            if viewModel.isUpdating {
                SchedulingRefreshOverlay()
            }
        }
        .onAppear {
            viewModel.load(showLoading: viewModel.items.isEmpty)
        }
        .onReceive(NotificationCenter.default.publisher(for: .presetTimesUpdated)) { _ in
            viewModel.load(showLoading: viewModel.items.isEmpty)
        }
        .sheet(isPresented: $showingCreate) {
            MedicationFormView(sessionStore: sessionStore, onSuccess: showToast)
                .environmentObject(sessionStore)
        }
        .sheet(item: $selectedMedication) { medication in
            MedicationFormView(sessionStore: sessionStore, medication: medication, onSuccess: showToast)
                .environmentObject(sessionStore)
        }
        .onChange(of: showingCreate) { _, isPresented in
            if !isPresented {
                viewModel.load(showLoading: viewModel.items.isEmpty)
            }
        }
        .onChange(of: selectedMedication?.id) { _, medicationId in
            if medicationId == nil {
                viewModel.load(showLoading: viewModel.items.isEmpty)
            }
        }
        .accessibilityIdentifier("MedicationListView")
    }

    private var centeredLoadingState: some View {
        GeometryReader { proxy in
            LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func showToast(_ message: String) {
        toastPresenter.show(message)
    }

    private var emptyMedicationHeader: some View {
        HStack(alignment: .center, spacing: 14) {
            CaregiverAvatar(name: patientName, systemImage: "pills.fill")
            VStack(alignment: .leading, spacing: 4) {
                Text(NSLocalizedString("caregiver.medications.title", comment: "Medications title"))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(patientNameLine)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            Spacer(minLength: 0)
        }
        .padding(.top, 4)
    }

    private var emptyMedicationCard: some View {
        CaregiverCard(accent: CaregiverUI.teal) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top, spacing: 14) {
                    MedicationSymbolView(tint: CaregiverUI.teal)
                        .frame(width: 64, height: 64)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(NSLocalizedString("medication.list.empty.title", comment: "Empty list title"))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(NSLocalizedString("medication.list.empty.message", comment: "Empty list message"))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                VStack(spacing: 10) {
                    CaregiverOnboardingStepRow(
                        number: 1,
                        title: NSLocalizedString("medication.list.empty.step.name", comment: "Medication empty name step"),
                        systemImage: "textformat",
                        tint: CaregiverUI.teal
                    )
                    CaregiverOnboardingStepRow(
                        number: 2,
                        title: NSLocalizedString("medication.list.empty.step.schedule", comment: "Medication empty schedule step"),
                        systemImage: "clock.fill",
                        tint: CaregiverUI.blue
                    )
                    CaregiverOnboardingStepRow(
                        number: 3,
                        title: NSLocalizedString("medication.list.empty.step.inventory", comment: "Medication empty inventory step"),
                        systemImage: "shippingbox.fill",
                        tint: CaregiverUI.orange
                    )
                }

                CaregiverPrimaryButton(
                    title: NSLocalizedString("medication.list.empty.action", comment: "Add medication action"),
                    systemImage: "plus"
                ) {
                    showingCreate = true
                }
            }
        }
        .accessibilityIdentifier("MedicationListEmptyCard")
    }

    private var medicationHeaderRow: some View {
        HStack(alignment: .center, spacing: 14) {
            CaregiverAvatar(name: patientName, systemImage: "pills.fill")
            VStack(alignment: .leading, spacing: 4) {
                Text(NSLocalizedString("caregiver.medications.title", comment: "Medications title"))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(patientNameLine)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            Spacer(minLength: 0)
            Button {
                showingCreate = true
            } label: {
                Label(NSLocalizedString("medication.list.add", comment: "Add medication"), systemImage: "plus")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .frame(height: 44)
                    .background(CaregiverUI.teal, in: Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(NSLocalizedString("medication.list.add", comment: "Add medication"))
        }
        .padding(.top, 12)
        .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 4, trailing: 20))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var medicationOverviewRow: some View {
        let metricColumns = [
            GridItem(.flexible(), spacing: 10),
            GridItem(.flexible(), spacing: 10)
        ]
        return VStack(alignment: .leading, spacing: 10) {
            LazyVGrid(columns: metricColumns, spacing: 10) {
                MedicationMetricTile(
                    title: NSLocalizedString("medication.list.metric.total", comment: "Total medications"),
                    value: "\(activeItems.count)",
                    tint: CaregiverUI.teal,
                    systemImage: "pills.fill"
                )
                MedicationMetricTile(
                    title: NSLocalizedString("medication.list.metric.today", comment: "Scheduled medications"),
                    value: "\(activeScheduledItems.count)",
                    tint: CaregiverUI.blue,
                    systemImage: "clock.fill"
                )
                MedicationMetricTile(
                    title: NSLocalizedString("medication.list.metric.prn", comment: "PRN medications"),
                    value: "\(activePrnItems.count)",
                    tint: CaregiverUI.orange,
                    systemImage: "cross.case.fill"
                )
                MedicationMetricTile(
                    title: NSLocalizedString("medication.list.metric.ended", comment: "Ended medications"),
                    value: "\(expiredItems.count)",
                    tint: .gray,
                    systemImage: "calendar.badge.clock"
                )
            }
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var medicationFilterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(MedicationListFilter.allCases) { filter in
                    Button {
                        withAnimation(.snappy) {
                            selectedFilter = filter
                        }
                    } label: {
                        Label(filter.title, systemImage: filter.systemImage)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(selectedFilter == filter ? .white : filterTint(filter))
                            .padding(.horizontal, 12)
                            .frame(height: 38)
                            .background(selectedFilter == filter ? filterTint(filter) : Color.white, in: Capsule())
                            .overlay {
                                Capsule()
                                    .stroke(filterTint(filter).opacity(0.22), lineWidth: 1)
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 8, trailing: 0))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
    }

    private var activeItems: [MedicationListItem] {
        viewModel.items.filter { !isExpired($0) }
    }

    private var expiredItems: [MedicationListItem] {
        viewModel.items.filter(isExpired)
    }

    private var activeScheduledItems: [MedicationListItem] {
        activeItems.filter { !$0.medication.isPrn }
    }

    private var activePrnItems: [MedicationListItem] {
        activeItems.filter { $0.medication.isPrn }
    }

    private var expiredScheduledItems: [MedicationListItem] {
        expiredItems.filter { !$0.medication.isPrn }
    }

    private var expiredPrnItems: [MedicationListItem] {
        expiredItems.filter { $0.medication.isPrn }
    }

    private var displayScheduledItems: [MedicationListItem] {
        switch selectedFilter {
        case .all:
            return activeScheduledItems
        case .scheduled:
            return activeScheduledItems
        default:
            return []
        }
    }

    private var displayPrnItems: [MedicationListItem] {
        switch selectedFilter {
        case .all:
            return activePrnItems
        case .prn:
            return activePrnItems
        default:
            return []
        }
    }

    private var displayExpiredScheduledItems: [MedicationListItem] {
        selectedFilter == .all || selectedFilter == .ended ? expiredScheduledItems : []
    }

    private var displayExpiredPrnItems: [MedicationListItem] {
        selectedFilter == .all || selectedFilter == .ended ? expiredPrnItems : []
    }

    private var patientNameLine: String {
        guard let patientName, !patientName.isEmpty else {
            return NSLocalizedString("caregiver.common.patient.none", comment: "No patient selected")
        }
        return String(format: NSLocalizedString("caregiver.common.patient.format", comment: "Patient name format"), patientName)
    }

    private func isExpired(_ item: MedicationListItem) -> Bool {
        guard let endDate = item.medication.endDate else { return false }
        let todayStart = Self.listCalendar.startOfDay(for: Date())
        return endDate < todayStart
    }

    private func inventoryStatusText(for item: MedicationListItem) -> String? {
        guard item.medication.inventoryEnabled else { return nil }
        let quantity = AppConstants.formatDecimal(item.medication.inventoryQuantity)
        let unit = item.medication.inventoryUnit ?? NSLocalizedString("caregiver.inventory.unit", comment: "Inventory unit")
        if item.medication.inventoryOut {
            return NSLocalizedString("medication.list.inventory.out", comment: "Out of stock")
        }
        return String(format: NSLocalizedString("medication.list.inventory.remaining.format", comment: "Remaining inventory format"), quantity, unit)
    }

    private func sectionHeader(_ key: String) -> some View {
        Text(NSLocalizedString(key, comment: "Medication section"))
            .font(.headline)
            .foregroundStyle(.secondary)
            .textCase(nil)
    }

    private func filterTint(_ filter: MedicationListFilter) -> Color {
        switch filter {
        case .all:
            return CaregiverUI.teal
        case .scheduled:
            return CaregiverUI.blue
        case .prn:
            return CaregiverUI.orange
        case .ended:
            return .gray
        }
    }

    @ViewBuilder
    private func medicationRow(_ item: MedicationListItem) -> some View {
        let rowContent = HStack(alignment: .top, spacing: 14) {
            MedicationSymbolView(
                tint: medicationAccentColor(for: item),
                systemImage: item.medication.isPrn ? "cross.case.fill" : "pills.fill"
            )
            .frame(width: 62, height: 62)

            VStack(alignment: .leading, spacing: 9) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(item.name)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(3)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityLabel("薬名 \(item.name)")
                    if sessionStore.mode == .caregiver {
                        medicationTypeBadge(isPrn: item.medication.isPrn)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                VStack(alignment: .leading, spacing: 7) {
                    medicationDetailLine(item.doseText, systemImage: "pills.fill")
                    if let schedule = item.scheduleText {
                        medicationDetailLine(schedule, systemImage: "clock.fill", lineLimit: 3)
                    } else if item.medication.isPrn {
                        medicationDetailLine(
                            NSLocalizedString("medication.list.prn.whenNeeded", comment: "PRN when needed"),
                            systemImage: "cross.case.fill"
                        )
                    }
                }

                HStack(spacing: 8) {
                    if let inventoryText = inventoryStatusText(for: item) {
                        CaregiverStatusPill(
                            text: inventoryText,
                            color: Color.secondary,
                            systemImage: "shippingbox.fill"
                        )
                    }
                }
            }
            .layoutPriority(1)
            Spacer()
            if sessionStore.mode == .caregiver {
                Image(systemName: "pencil")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(CaregiverUI.tealDark)
                    .frame(width: 42, height: 42)
                    .background(CaregiverUI.teal.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        if sessionStore.mode == .caregiver {
            Button(action: { selectedMedication = item.medication }) {
                rowContent
                    .padding(18)
                    .frame(maxWidth: .infinity)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(medicationAccentColor(for: item).opacity(0.20), lineWidth: 1.2)
                    )
                    .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
            }
            .buttonStyle(.plain)
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        } else {
            rowContent
                .padding(18)
                .frame(maxWidth: .infinity)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(Color.black.opacity(0.10), lineWidth: 1)
                )
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
        }
    }

    private func medicationTypeBadge(isPrn: Bool) -> some View {
        let text = isPrn
            ? NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
            : NSLocalizedString("medication.list.badge.scheduled", comment: "Scheduled badge")
        let color: Color = isPrn ? CaregiverUI.orange : CaregiverUI.teal
        return Text(text)
            .font(.caption.weight(.bold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.15), in: Capsule())
            .accessibilityLabel(text)
    }

    private func medicationDetailLine(
        _ text: String,
        systemImage: String,
        lineLimit: Int = 2
    ) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: systemImage)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(CaregiverUI.tealDark)
                .frame(width: 20, alignment: .center)
            Text(text)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(lineLimit)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func medicationAccentColor(for item: MedicationListItem) -> Color {
        item.medication.isPrn ? CaregiverUI.orange : CaregiverUI.teal
    }
}

private struct MedicationMetricTile: View {
    let title: String
    let value: String
    let tint: Color
    let systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: systemImage)
                .font(.headline.weight(.bold))
                .foregroundStyle(tint)
                .frame(width: 30, height: 30)
                .background(tint.opacity(0.13), in: Circle())
            Text(value)
                .font(.title.weight(.bold))
                .foregroundStyle(.primary)
                .monospacedDigit()
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 124)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(tint.opacity(0.18), lineWidth: 1)
        }
        .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
    }
}
