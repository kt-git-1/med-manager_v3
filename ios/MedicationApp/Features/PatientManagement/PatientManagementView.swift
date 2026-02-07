import SwiftUI

@MainActor
final class PatientManagementViewModel: ObservableObject {
    @Published var patients: [PatientDTO] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var issuedCode: LinkingCodeDTO?

    private let apiClient: APIClient
    private let sessionStore: SessionStore

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    convenience init(sessionStore: SessionStore) {
        let baseURL = SessionStore.resolveBaseURL()
        self.init(
            apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
            sessionStore: sessionStore
        )
    }

    var selectedPatientId: String? {
        sessionStore.currentPatientId
    }

    func toggleSelection(patientId: String) {
        if sessionStore.currentPatientId == patientId {
            sessionStore.clearCurrentPatientId()
        } else {
            sessionStore.setCurrentPatientId(patientId)
        }
    }

    func setSelectedPatientId(_ patientId: String?) {
        if let patientId, !patientId.isEmpty {
            sessionStore.setCurrentPatientId(patientId)
        } else {
            sessionStore.clearCurrentPatientId()
        }
    }

    func load() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                patients = try await apiClient.listPatients()
            } catch {
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    func createPatient(displayName: String) async -> Bool {
        do {
            let created = try await apiClient.createPatient(displayName: displayName)
            patients.insert(created, at: 0)
            return true
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
        }
    }

    func issueLinkingCode(patientId: String) async {
        do {
            issuedCode = try await apiClient.issueLinkingCode(patientId: patientId)
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
        }
    }

    func revokePatient(patientId: String) async {
        do {
            try await apiClient.revokePatient(patientId: patientId)
            patients.removeAll { $0.id == patientId }
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
        }
    }
}

struct PatientManagementView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @StateObject private var viewModel: PatientManagementViewModel
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @State private var showingCreate = false
    @State private var revokeTarget: PatientDTO?
    @State private var toastMessage: String?
    @State private var draftTimes: [NotificationSlot: Date] = [:]
    @State private var isSavingDetail = false
    private let timeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current
    private let apiClient: APIClient

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        _viewModel = StateObject(wrappedValue: PatientManagementViewModel(sessionStore: store))
        self.apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: store)
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                } else if let errorMessage = viewModel.errorMessage {
                    ErrorStateView(message: errorMessage)
                } else if viewModel.patients.isEmpty {
                    EmptyStateView(
                        title: NSLocalizedString("caregiver.patients.empty.title", comment: "Empty patients title"),
                        message: NSLocalizedString("caregiver.patients.empty.message", comment: "Empty patients message")
                    )
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                    .padding(.horizontal, 24)
                } else {
                    let selectedPatient = viewModel.patients.first { $0.id == viewModel.selectedPatientId }
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(NSLocalizedString("caregiver.patients.select.label", comment: "Select label"))
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                Picker(
                                    NSLocalizedString("caregiver.patients.select.label", comment: "Select label"),
                                    selection: Binding(
                                        get: { viewModel.selectedPatientId ?? "" },
                                        set: { newValue in
                                            viewModel.setSelectedPatientId(newValue.isEmpty ? nil : newValue)
                                        }
                                    )
                                ) {
                                    Text(NSLocalizedString("caregiver.patients.select.placeholder", comment: "Select placeholder"))
                                        .tag("")
                                    ForEach(viewModel.patients) { patient in
                                        Text(patient.displayName).tag(patient.id)
                                    }
                                }
                                .pickerStyle(.menu)
                                Text(NSLocalizedString("caregiver.patients.select.help", comment: "Select help text"))
                                    .font(.body)
                                    .foregroundColor(.secondary)
                            }
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)

                            if let selectedPatient {
                                VStack(alignment: .leading, spacing: 12) {
                                    HStack {
                                        Text(selectedPatient.displayName)
                                            .font(.title3.weight(.semibold))
                                        Spacer()
                                        Text(NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label"))
                                            .font(.subheadline.weight(.semibold))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(Color.accentColor.opacity(0.2))
                                            .foregroundColor(.accentColor)
                                            .clipShape(Capsule())
                                    }
                                    HStack {
                                        Button(NSLocalizedString("caregiver.patients.issueCode", comment: "Issue code")) {
                                            Task { await viewModel.issueLinkingCode(patientId: selectedPatient.id) }
                                        }
                                        .buttonStyle(.bordered)
                                        .font(.headline)
                                        Button(NSLocalizedString("caregiver.patients.revoke", comment: "Revoke")) {
                                            revokeTarget = selectedPatient
                                        }
                                        .buttonStyle(.bordered)
                                        .font(.headline)
                                        .tint(.red)
                                    }
                                }
                                .padding(16)
                                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                                .accessibilityLabel("\(selectedPatient.displayName) \(NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label"))")
                            } else {
                                EmptyStateView(
                                    title: NSLocalizedString("caregiver.patients.select.empty.title", comment: "No selection title"),
                                    message: NSLocalizedString("caregiver.patients.select.empty.message", comment: "No selection message")
                                )
                                .padding(16)
                                .frame(maxWidth: .infinity)
                                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                            }

                            if viewModel.selectedPatientId != nil {
                                settingsSection
                            }
                            logoutSection
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 16)
                        .padding(.bottom, 64)
                    }
                }
            }
            .navigationTitle(NSLocalizedString("caregiver.patients.title", comment: "Patients title"))
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button(NSLocalizedString("caregiver.patients.add", comment: "Add patient")) {
                        showingCreate = true
                    }
                }
            }
            .sheet(isPresented: $showingCreate) {
                PatientCreateView { displayName in
                    Task {
                        let created = await viewModel.createPatient(displayName: displayName)
                        if created {
                            showingCreate = false
                        }
                    }
                }
            }
            .sheet(item: $viewModel.issuedCode) { code in
                PatientLinkCodeView(code: code)
            }
            .sheet(item: $revokeTarget) { patient in
                PatientRevokeView(
                    patient: patient,
                    onConfirm: {
                        Task { await viewModel.revokePatient(patientId: patient.id) }
                        revokeTarget = nil
                    },
                    onCancel: {
                        revokeTarget = nil
                    }
                )
            }
        }
        .onAppear {
            viewModel.load()
            if draftTimes.isEmpty {
                draftTimes = buildDraftTimes()
            }
        }
        .overlay(alignment: .top) {
            if let toastMessage {
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
        .overlay {
            if isSavingDetail {
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
        .accessibilityIdentifier("PatientManagementView")
    }

    private var settingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(NSLocalizedString("patient.settings.notifications.detail.title", comment: "Detail settings title"))
                .font(.headline)
            timePickerRow(
                title: NSLocalizedString("patient.settings.notifications.slot.morning", comment: "Morning"),
                slot: .morning
            )
            timePickerRow(
                title: NSLocalizedString("patient.settings.notifications.slot.noon", comment: "Noon"),
                slot: .noon
            )
            timePickerRow(
                title: NSLocalizedString("patient.settings.notifications.slot.evening", comment: "Evening"),
                slot: .evening
            )
            timePickerRow(
                title: NSLocalizedString("patient.settings.notifications.slot.bedtime", comment: "Bedtime"),
                slot: .bedtime
            )
            Button(NSLocalizedString("common.save", comment: "Save")) {
                Task { await saveDetailSettings() }
            }
            .buttonStyle(.borderedProminent)
            .font(.headline)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
    }

    private var logoutSection: some View {
        Button(NSLocalizedString("common.logout", comment: "Logout")) {
            sessionStore.clearCaregiverToken()
        }
        .buttonStyle(.borderedProminent)
        .tint(.red)
        .font(.headline)
        .padding(.top, 4)
        .padding(.bottom, 48)
    }

    private func timePickerRow(title: String, slot: NotificationSlot) -> some View {
        HStack {
            Text(title)
            Spacer()
            DatePicker(
                "",
                selection: timeBinding(for: slot),
                displayedComponents: .hourAndMinute
            )
            .labelsHidden()
        }
    }

    private func timeBinding(for slot: NotificationSlot) -> Binding<Date> {
        Binding(
            get: {
                draftTimes[slot] ?? buildDate(hourMinute: preferencesStore.slotTime(for: slot))
            },
            set: { newDate in
                draftTimes[slot] = newDate
            }
        )
    }

    private func buildDraftTimes() -> [NotificationSlot: Date] {
        var result: [NotificationSlot: Date] = [:]
        for slot in NotificationSlot.allCases {
            let time = preferencesStore.slotTime(for: slot)
            result[slot] = buildDate(hourMinute: time)
        }
        return result
    }

    private func buildDate(hourMinute: (hour: Int, minute: Int)) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        var components = calendar.dateComponents([.year, .month, .day], from: Date())
        components.hour = hourMinute.hour
        components.minute = hourMinute.minute
        components.second = 0
        return calendar.date(from: components) ?? Date()
    }

    private func saveDetailSettings() async {
        guard !isSavingDetail else { return }
        isSavingDetail = true
        defer { isSavingDetail = false }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let oldSlotTimes = preferencesStore.slotTimesMap()
        for slot in NotificationSlot.allCases {
            if let date = draftTimes[slot] {
                let components = calendar.dateComponents([.hour, .minute], from: date)
                preferencesStore.setSlotTime(
                    slot,
                    hour: components.hour ?? 0,
                    minute: components.minute ?? 0
                )
            }
        }
        do {
            let newSlotTimes = preferencesStore.slotTimesMap()
            try await updateRegimensForPresetChange(
                oldSlotTimes: oldSlotTimes,
                newSlotTimes: newSlotTimes
            )
            await rescheduleIfNeeded()
            showToast(NSLocalizedString("common.toast.updated", comment: "Updated toast"))
            NotificationCenter.default.post(name: .presetTimesUpdated, object: nil)
        } catch {
            showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
        }
    }

    private func rescheduleIfNeeded() async {
        guard preferencesStore.masterEnabled else { return }
        guard sessionStore.mode == .caregiver, let patientId = sessionStore.currentPatientId else {
            return
        }
        await schedulingCoordinator.refresh(
            apiClient: apiClient,
            includeSecondary: preferencesStore.rereminderEnabled,
            enabledSlots: preferencesStore.enabledSlots(),
            slotTimes: preferencesStore.slotTimesMap(),
            caregiverPatientId: patientId,
            trigger: .settingsChange
        )
    }

    private func updateRegimensForPresetChange(
        oldSlotTimes: [NotificationSlot: (hour: Int, minute: Int)],
        newSlotTimes: [NotificationSlot: (hour: Int, minute: Int)]
    ) async throws {
        guard sessionStore.mode == .caregiver, let patientId = sessionStore.currentPatientId else {
            return
        }
        let medications = try await apiClient.fetchMedications(patientId: patientId)
        let mapping = buildPresetMapping(oldSlotTimes: oldSlotTimes, newSlotTimes: newSlotTimes)
        guard !mapping.isEmpty else { return }
        for medication in medications {
            let regimens = try await apiClient.fetchRegimens(medicationId: medication.id)
            for regimen in regimens {
                let updatedTimes = regimen.times.map { time in
                    mapping[time] ?? time
                }
                if updatedTimes != regimen.times {
                    let input = RegimenUpdateRequestDTO(
                        timezone: nil,
                        startDate: nil,
                        endDate: nil,
                        times: updatedTimes,
                        daysOfWeek: nil,
                        enabled: regimen.enabled
                    )
                    _ = try await apiClient.updateRegimen(id: regimen.id, input: input)
                }
            }
        }
    }

    private func buildPresetMapping(
        oldSlotTimes: [NotificationSlot: (hour: Int, minute: Int)],
        newSlotTimes: [NotificationSlot: (hour: Int, minute: Int)]
    ) -> [String: String] {
        var mapping: [String: String] = [:]
        for slot in NotificationSlot.allCases {
            guard
                let oldTime = oldSlotTimes[slot],
                let newTime = newSlotTimes[slot]
            else {
                continue
            }
            let oldString = String(format: "%02d:%02d", oldTime.hour, oldTime.minute)
            let newString = String(format: "%02d:%02d", newTime.hour, newTime.minute)
            if oldString != newString {
                mapping[oldString] = newString
            }
            let defaultTime = slot.hourMinute
            let defaultString = String(format: "%02d:%02d", defaultTime.hour, defaultTime.minute)
            if defaultString != newString {
                mapping[defaultString] = newString
            }
            let scheduleDefault = defaultScheduleTimeString(for: slot)
            if scheduleDefault != newString {
                mapping[scheduleDefault] = newString
            }
        }
        return mapping
    }

    private func defaultScheduleTimeString(for slot: NotificationSlot) -> String {
        switch slot {
        case .morning:
            return ScheduleTimeSlot.morning.timeValue
        case .noon:
            return ScheduleTimeSlot.noon.timeValue
        case .evening:
            return ScheduleTimeSlot.evening.timeValue
        case .bedtime:
            return ScheduleTimeSlot.bedtime.timeValue
        }
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task {
            try? await Task.sleep(for: .seconds(1))
            await MainActor.run {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
    }
}
