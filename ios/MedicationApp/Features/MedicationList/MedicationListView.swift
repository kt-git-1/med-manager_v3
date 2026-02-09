import SwiftUI

struct MedicationListItem: Identifiable {
    let medication: MedicationDTO
    let name: String
    let scheduleText: String?
    let doseText: String

    var id: String { medication.id }
}

@MainActor
final class MedicationListViewModel: ObservableObject {
    @Published var items: [MedicationListItem] = []
    @Published var isLoading = false
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

    func load() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
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
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
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
    @StateObject private var viewModel: MedicationListViewModel
    @State private var showingCreate = false
    @State private var selectedMedication: MedicationDTO?
    @State private var toastMessage: String?

    init(
        sessionStore: SessionStore? = nil,
        onOpenPatients: (() -> Void)? = nil,
        headerView: AnyView? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.onOpenPatients = onOpenPatients
        self.headerView = headerView
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
                    Color.clear
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let errorMessage = viewModel.errorMessage {
                    ErrorStateView(message: errorMessage)
                } else if viewModel.items.isEmpty {
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
                                if sessionStore.mode == .caregiver {
                                    Button {
                                        showingCreate = true
                                    } label: {
                                        Text(NSLocalizedString("medication.list.empty.action", comment: "Add medication action"))
                                            .font(.headline)
                                            .foregroundStyle(.white)
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 50)
                                            .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                                    }
                                }
                            }
                            .padding(24)
                            .frame(maxWidth: .infinity)
                            .glassEffect(.regular, in: .rect(cornerRadius: 20))
                            .padding(.horizontal, 24)
                            Spacer(minLength: 120)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    let baseList = List {
                        if let headerView {
                            headerView
                                .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                        }
                        if sessionStore.mode == .caregiver {
                            if !activeScheduledItems.isEmpty {
                                Section {
                                    ForEach(activeScheduledItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    Text(NSLocalizedString("medication.list.section.scheduled", comment: "Scheduled section"))
                                        .font(.headline)
                                        .foregroundStyle(.secondary)
                                        .textCase(nil)
                                }
                                .listRowSeparator(.hidden)
                            }

                            if !activePrnItems.isEmpty {
                                Section {
                                    ForEach(activePrnItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    Text(NSLocalizedString("medication.list.section.prn", comment: "PRN section"))
                                        .font(.headline)
                                        .foregroundStyle(.secondary)
                                        .textCase(nil)
                                }
                                .listRowSeparator(.hidden)
                            }

                            if !expiredScheduledItems.isEmpty {
                                Section {
                                    ForEach(expiredScheduledItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    Text(NSLocalizedString("medication.list.section.expired.scheduled", comment: "Expired scheduled section"))
                                        .font(.headline)
                                        .foregroundStyle(.secondary)
                                        .textCase(nil)
                                }
                                .listRowSeparator(.hidden)
                            }

                            if !expiredPrnItems.isEmpty {
                                Section {
                                    ForEach(expiredPrnItems) { item in
                                        medicationRow(item)
                                    }
                                } header: {
                                    Text(NSLocalizedString("medication.list.section.expired.prn", comment: "Expired PRN section"))
                                        .font(.headline)
                                        .foregroundStyle(.secondary)
                                        .textCase(nil)
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
                    .safeAreaPadding(.bottom, 120)
                    .refreshable {
                        viewModel.load()
                    }
                    let listWithInsets = headerView == nil ? AnyView(baseList.safeAreaPadding(.top)) : AnyView(baseList)
                    listWithInsets
                        .overlay(alignment: .top) {
                            if let toastMessage {
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
                }
            }

            if viewModel.isLoading {
                SchedulingRefreshOverlay()
            }
        }
        .onAppear {
            viewModel.load()
        }
        .onReceive(NotificationCenter.default.publisher(for: .presetTimesUpdated)) { _ in
            viewModel.load()
        }
        .toolbar {
            if sessionStore.mode == .caregiver {
                Button(NSLocalizedString("medication.list.add", comment: "Add medication")) {
                    showingCreate = true
                }
            }
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
                viewModel.load()
            }
        }
        .onChange(of: selectedMedication?.id) { _, medicationId in
            if medicationId == nil {
                viewModel.load()
            }
        }
        .sensoryFeedback(.success, trigger: toastMessage)
        .accessibilityIdentifier("MedicationListView")
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task {
            try? await Task.sleep(for: .seconds(AppConstants.toastDuration))
            await MainActor.run {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
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

    private func isExpired(_ item: MedicationListItem) -> Bool {
        guard let endDate = item.medication.endDate else { return false }
        let todayStart = Self.listCalendar.startOfDay(for: Date())
        return endDate < todayStart
    }

    @ViewBuilder
    private func medicationRow(_ item: MedicationListItem) -> some View {
        let rowContent = HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(item.name)
                        .font(.title3.weight(.semibold))
                        .accessibilityLabel("薬名 \(item.name)")
                    if sessionStore.mode == .caregiver {
                        medicationTypeBadge(isPrn: item.medication.isPrn)
                    }
                }
                HStack(spacing: 6) {
                    if let schedule = item.scheduleText {
                        Text(schedule)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .accessibilityLabel(NSLocalizedString("a11y.medication.schedule", comment: "Schedule") + " " + schedule)
                        Text("·")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Text(item.doseText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(item.doseText)
                }
            }
            Spacer()
            if sessionStore.mode == .caregiver {
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
                    .padding(.top, 2)
            }
        }
        if sessionStore.mode == .caregiver {
            Button(action: { selectedMedication = item.medication }) {
                rowContent
                    .padding(16)
                    .frame(maxWidth: .infinity)
                    .glassEffect(.regular, in: .rect(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        } else {
            rowContent
                .padding(16)
                .frame(maxWidth: .infinity)
                .glassEffect(.regular, in: .rect(cornerRadius: 16))
                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
        }
    }

    private func medicationTypeBadge(isPrn: Bool) -> some View {
        let text = isPrn
            ? NSLocalizedString("medication.list.badge.prn", comment: "PRN badge")
            : NSLocalizedString("medication.list.badge.scheduled", comment: "Scheduled badge")
        let color: Color = isPrn ? .purple : .blue
        return Text(text)
            .font(.caption.weight(.bold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.15), in: Capsule())
            .accessibilityLabel(text)
    }
}
