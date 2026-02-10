import SwiftUI

@MainActor
final class PatientManagementViewModel: ObservableObject {
    @Published var patients: [PatientDTO] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var issuedCode: LinkingCodeDTO?
    @Published var isPatientLimitExceeded = false

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
        isPatientLimitExceeded = false
        do {
            let created = try await apiClient.createPatient(displayName: displayName)
            patients.insert(created, at: 0)
            return true
        } catch let error as APIError {
            if case .patientLimitExceeded = error {
                isPatientLimitExceeded = true
                return false
            }
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
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

    func revokePatient(patientId: String) async -> Bool {
        do {
            try await apiClient.revokePatient(patientId: patientId)
            patients.removeAll { $0.id == patientId }
            return true
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
        }
    }

    func deletePatient(patientId: String) async -> Bool {
        do {
            try await apiClient.deletePatient(patientId: patientId)
            patients.removeAll { $0.id == patientId }
            return true
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
        }
    }
}

struct PatientManagementView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var globalBannerPresenter: GlobalBannerPresenter
    @StateObject private var viewModel: PatientManagementViewModel
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @State private var showingCreate = false
    @State private var revokeTarget: PatientDTO?
    @State private var deleteTarget: PatientDTO?
    @State private var showingLogoutConfirm = false
    @State private var showingPaywall = false
    @State private var toastMessage: String?
    @State private var draftTimes: [NotificationSlot: Date] = [:]
    @State private var isSavingDetail = false
    @State private var inventoryThresholdText = ""
    @State private var inventoryItems: [InventoryItemDTO] = []
    @State private var showingTimePresetSheet = false
    @State private var showingInventoryThresholdSheet = false
    private let timeZone = AppConstants.defaultTimeZone
    private let apiClient: APIClient
    private static let thresholdKeyPrefix = "inventory.threshold."
    var entitlementStore: EntitlementStore?

    init(sessionStore: SessionStore? = nil, entitlementStore: EntitlementStore? = nil) {
        let store = sessionStore ?? SessionStore()
        _viewModel = StateObject(wrappedValue: PatientManagementViewModel(sessionStore: store))
        self.apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: store)
        self.entitlementStore = entitlementStore
    }

    var body: some View {
        NavigationStack {
            contentView
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                NavigationHeaderView(
                    icon: "person.2.circle.fill",
                    title: NSLocalizedString("caregiver.patients.title", comment: "Patients title")
                )
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button(NSLocalizedString("caregiver.patients.add", comment: "Add patient")) {
                        Task { await handleAddPatientTapped() }
                    }
                    .accessibilityIdentifier("caregiver.patients.add")
                }
            }
            .sheet(isPresented: $showingCreate) {
                PatientCreateView(
                    onSave: { displayName in
                        await viewModel.createPatient(displayName: displayName)
                    },
                    onSuccess: showToast
                )
            }
            .sheet(item: $viewModel.issuedCode) { code in
                PatientLinkCodeView(code: code)
            }
            .sheet(item: $revokeTarget) { patient in
                PatientRevokeView(
                    patient: patient,
                    onConfirm: {
                        await viewModel.revokePatient(patientId: patient.id)
                    },
                    onSuccess: { message in
                        globalBannerPresenter.show(message: message, duration: 2)
                    },
                    onCancel: {
                        revokeTarget = nil
                    }
                )
            }
            .sheet(item: $deleteTarget) { patient in
                PatientDeleteView(
                    patient: patient,
                    onConfirm: {
                        await viewModel.deletePatient(patientId: patient.id)
                    },
                    onSuccess: { message in
                        globalBannerPresenter.show(message: message, duration: 2)
                    },
                    onCancel: {
                        deleteTarget = nil
                    }
                )
            }
        .sheet(isPresented: $showingTimePresetSheet) {
            NavigationStack {
                timePresetDetailSheet
                    .navigationTitle(
                        NSLocalizedString("patient.settings.notifications.detail.item", comment: "Detail settings item")
                    )
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button(NSLocalizedString("common.save", comment: "Save")) {
                                Task {
                                    let success = await saveDetailSettings()
                                    showingTimePresetSheet = false
                                    showToast(success
                                        ? NSLocalizedString("caregiver.timePreset.toast.updated", comment: "Time preset updated toast")
                                        : NSLocalizedString("common.error.save", comment: "Save error"))
                                }
                            }
                            .disabled(isSavingDetail)
                        }
                        ToolbarItem(placement: .topBarLeading) {
                            Button(NSLocalizedString("common.close", comment: "Close")) {
                                showingTimePresetSheet = false
                            }
                            .disabled(isSavingDetail)
                        }
                    }
            }
        }
        .sheet(isPresented: $showingInventoryThresholdSheet) {
            NavigationStack {
                inventoryThresholdDetailSheet
                    .navigationTitle(
                        NSLocalizedString("caregiver.inventory.settings.section.global", comment: "Inventory global settings title")
                    )
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button(NSLocalizedString("common.save", comment: "Save")) {
                                Task {
                                    let success = await saveInventoryThreshold()
                                    showingInventoryThresholdSheet = false
                                    showToast(success
                                        ? NSLocalizedString("caregiver.inventory.toast.saved", comment: "Inventory saved")
                                        : NSLocalizedString("common.error.save", comment: "Save error"))
                                }
                            }
                            .disabled(isSavingDetail)
                        }
                        ToolbarItem(placement: .topBarLeading) {
                            Button(NSLocalizedString("common.close", comment: "Close")) {
                                showingInventoryThresholdSheet = false
                            }
                            .disabled(isSavingDetail)
                        }
                    }
            }
        }
        }
        .onAppear {
            viewModel.load()
            preferencesStore.switchPatient(viewModel.selectedPatientId)
            draftTimes = buildDraftTimes()
            Task { await loadInventoryThreshold() }
        }
        .onChange(of: viewModel.selectedPatientId) { _, newPatientId in
            preferencesStore.switchPatient(newPatientId)
            draftTimes = buildDraftTimes()
            if let patientId = newPatientId, let saved = Self.loadSavedThreshold(for: patientId) {
                inventoryThresholdText = String(saved)
            } else {
                inventoryThresholdText = ""
            }
            Task { await loadInventoryThreshold() }
        }
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
        .overlay {
            if viewModel.isLoading || isSavingDetail {
                SchedulingRefreshOverlay()
            }
        }
        .overlay {
            if entitlementStore?.isRefreshing == true {
                SchedulingRefreshOverlay()
                    .accessibilityIdentifier("SchedulingRefreshOverlay")
            }
        }
        .sheet(isPresented: $showingPaywall) {
            if let entitlementStore {
                PaywallView(entitlementStore: entitlementStore)
            }
        }
        .onChange(of: viewModel.isPatientLimitExceeded) { _, exceeded in
            if exceeded {
                showingCreate = false
                Task {
                    try? await Task.sleep(for: .milliseconds(300))
                    showingPaywall = true
                    viewModel.isPatientLimitExceeded = false
                }
            }
        }
        .accessibilityIdentifier("PatientManagementView")
    }

    @ViewBuilder
    private var contentView: some View {
        if viewModel.isLoading {
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let errorMessage = viewModel.errorMessage {
            ErrorStateView(message: errorMessage)
        } else if viewModel.patients.isEmpty {
            VStack(spacing: 16) {
                Image(systemName: "person.crop.circle.badge.plus")
                    .font(.system(size: 44))
                    .foregroundStyle(.secondary)
                EmptyStateView(
                    title: NSLocalizedString("caregiver.patients.empty.title", comment: "Empty patients title"),
                    message: NSLocalizedString("caregiver.patients.empty.message", comment: "Empty patients message")
                )
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
            .padding(.horizontal, 24)
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    linkHeader
                    selectionCard
                    selectedPatientSection
                    detailSettingsSection
                    premiumSection
                    logoutSection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .padding(.bottom, 64)
            }
        }
    }

    private var linkHeader: some View {
        Text(NSLocalizedString("caregiver.settings.section.link", comment: "Linking header"))
            .font(.headline)
            .foregroundStyle(.secondary)
    }

    private var selectionCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("caregiver.patients.select.label", comment: "Select label"))
                .font(.headline)
                .foregroundStyle(.secondary)
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
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }

    @ViewBuilder
    private var selectedPatientSection: some View {
        if let selectedPatient {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    HStack(spacing: 10) {
                        Image(systemName: "person.circle.fill")
                            .font(.title2)
                            .foregroundStyle(.tint)
                        Text(selectedPatient.displayName)
                            .font(.title3.weight(.semibold))
                    }
                    Spacer()
                    Text(NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label"))
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.accentColor.opacity(0.2))
                        .foregroundStyle(Color.accentColor)
                        .clipShape(Capsule())
                }
                HStack(spacing: 12) {
                    Button {
                        Task { await viewModel.issueLinkingCode(patientId: selectedPatient.id) }
                    } label: {
                        Label(NSLocalizedString("caregiver.patients.issueCode", comment: "Issue code"), systemImage: "link.badge.plus")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color.accentColor.opacity(0.12))
                            .foregroundStyle(.tint)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    Button {
                        revokeTarget = selectedPatient
                    } label: {
                        Label(NSLocalizedString("caregiver.patients.revoke", comment: "Revoke"), systemImage: "person.crop.circle.badge.minus")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color.red.opacity(0.15))
                            .foregroundStyle(.red)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }
                Button {
                    deleteTarget = selectedPatient
                } label: {
                    Label(NSLocalizedString("caregiver.patients.delete", comment: "Delete patient"), systemImage: "trash")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color.red.opacity(0.15))
                        .foregroundStyle(.red)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .padding(16)
            .glassEffect(.regular, in: .rect(cornerRadius: 16))
            .accessibilityLabel("\(selectedPatient.displayName) \(NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label"))")
        } else {
            EmptyStateView(
                title: NSLocalizedString("caregiver.patients.select.empty.title", comment: "No selection title"),
                message: NSLocalizedString("caregiver.patients.select.empty.message", comment: "No selection message")
            )
            .padding(16)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 16))
        }
    }

    @ViewBuilder
    private var detailSettingsSection: some View {
        if viewModel.selectedPatientId != nil {
            Text(NSLocalizedString("caregiver.settings.section.detail", comment: "Detail settings header"))
                .font(.headline)
                .foregroundStyle(.secondary)
                .padding(.top, 16)
            settingsSection
            inventorySettingsSection
        }
    }

    private var selectedPatient: PatientDTO? {
        viewModel.patients.first { $0.id == viewModel.selectedPatientId }
    }

    private var settingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                showingTimePresetSheet = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "clock.fill")
                        .foregroundStyle(.tint)
                        .frame(width: 20)
                    Text(NSLocalizedString("patient.settings.notifications.detail.item", comment: "Detail settings item"))
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }

    @ViewBuilder
    private var premiumSection: some View {
        if let entitlementStore {
            Text(NSLocalizedString("billing.premium.section.header", comment: "Premium header"))
                .font(.headline)
                .foregroundStyle(.secondary)
                .padding(.top, 16)

            VStack(alignment: .leading, spacing: 12) {
                // Status label
                Text(entitlementStore.isPremium
                    ? NSLocalizedString("billing.premium.status.active", comment: "Premium active")
                    : NSLocalizedString("billing.premium.status.inactive", comment: "Premium inactive")
                )
                .font(.subheadline)
                .foregroundStyle(entitlementStore.isPremium ? .green : .secondary)

                // Upgrade button (hidden when already premium)
                if !entitlementStore.isPremium {
                    Button {
                        showingPaywall = true
                    } label: {
                        HStack {
                            Image(systemName: "crown.fill")
                                .foregroundStyle(.yellow)
                                .frame(width: 20)
                            Text(NSLocalizedString("billing.premium.upgrade", comment: "Upgrade"))
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 16)
                    }
                    .accessibilityIdentifier("billing.premium.upgrade")
                }

                // Restore button (always visible)
                Button {
                    Task { await entitlementStore.restore() }
                } label: {
                    HStack {
                        Image(systemName: "arrow.clockwise")
                            .foregroundStyle(.tint)
                            .frame(width: 20)
                        Text(NSLocalizedString("billing.premium.restore", comment: "Restore"))
                        Spacer()
                    }
                    .padding(.vertical, 12)
                    .padding(.horizontal, 16)
                }
                .accessibilityIdentifier("billing.premium.restore")
            }
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
    }

    private var logoutSection: some View {
        Button {
            showingLogoutConfirm = true
        } label: {
            Text(NSLocalizedString("common.logout", comment: "Logout"))
                .font(.headline)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.red, in: RoundedRectangle(cornerRadius: 14))
        }
        .padding(.top, 48)
        .padding(.bottom, 48)
        .alert(
            NSLocalizedString("caregiver.logout.confirm.title", comment: "Logout confirm title"),
            isPresented: $showingLogoutConfirm
        ) {
            Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
            Button(NSLocalizedString("caregiver.logout.confirm.action", comment: "Logout confirm action"), role: .destructive) {
                sessionStore.clearCaregiverToken()
                globalBannerPresenter.show(
                    message: NSLocalizedString("caregiver.logout.toast", comment: "Logout toast"),
                    duration: 2
                )
            }
        } message: {
            Text(NSLocalizedString("caregiver.logout.confirm.message", comment: "Logout confirm message"))
        }
    }

    private var inventorySettingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                showingInventoryThresholdSheet = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "archivebox.fill")
                        .foregroundStyle(.tint)
                        .frame(width: 20)
                    Text(NSLocalizedString("caregiver.inventory.settings.item.threshold", comment: "Inventory threshold item"))
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }

    private var timePresetDetailSheet: some View {
        Form {
            Section {
                VStack(spacing: 10) {
                    Image(systemName: "clock.circle.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                    Text(NSLocalizedString("patient.settings.notifications.detail.item", comment: "Detail settings item"))
                        .font(.title3.weight(.bold))
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }

            Section {
                timePickerRow(
                    title: NSLocalizedString("patient.settings.notifications.slot.morning", comment: "Morning"),
                    icon: "sunrise.fill",
                    iconColor: .orange,
                    slot: .morning
                )
                timePickerRow(
                    title: NSLocalizedString("patient.settings.notifications.slot.noon", comment: "Noon"),
                    icon: "sun.max.fill",
                    iconColor: .yellow,
                    slot: .noon
                )
                timePickerRow(
                    title: NSLocalizedString("patient.settings.notifications.slot.evening", comment: "Evening"),
                    icon: "sunset.fill",
                    iconColor: .orange,
                    slot: .evening
                )
                timePickerRow(
                    title: NSLocalizedString("patient.settings.notifications.slot.bedtime", comment: "Bedtime"),
                    icon: "moon.fill",
                    iconColor: .indigo,
                    slot: .bedtime
                )
            } header: {
                HStack(spacing: 6) {
                    Image(systemName: "clock.fill")
                        .font(.subheadline)
                        .foregroundStyle(.tint)
                    Text(NSLocalizedString("patient.settings.notifications.detail.note", comment: "Detail settings note"))
                }
                .font(.subheadline)
                .textCase(nil)
            }
        }
        .overlay { savingOverlay }
    }

    private var inventoryThresholdDetailSheet: some View {
        Form {
            Section {
                VStack(spacing: 10) {
                    Image(systemName: "archivebox.circle.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                    Text(NSLocalizedString("caregiver.inventory.settings.section.global", comment: "Inventory global settings title"))
                        .font(.title3.weight(.bold))
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }

            Section {
                HStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                        .frame(width: 20)
                    Text(NSLocalizedString("caregiver.inventory.detail.threshold", comment: "Inventory threshold"))
                    Spacer()
                    TextField("0", text: $inventoryThresholdText)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 80)
                        .accessibilityIdentifier("InventoryGlobalThresholdField")
                    Text(NSLocalizedString("common.days.unit", comment: "Days unit"))
                        .foregroundStyle(.secondary)
                }
            } header: {
                HStack(spacing: 6) {
                    Image(systemName: "archivebox.fill")
                        .font(.subheadline)
                        .foregroundStyle(.tint)
                    Text(NSLocalizedString("caregiver.inventory.settings.note", comment: "Inventory settings note"))
                }
                .font(.subheadline)
                .textCase(nil)
            }
        }
        .overlay { savingOverlay }
    }

    @ViewBuilder
    private var savingOverlay: some View {
        if isSavingDetail {
            SchedulingRefreshOverlay()
        }
    }

    private func timePickerRow(title: String, icon: String = "clock", iconColor: Color = .blue, slot: NotificationSlot) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(iconColor)
                .frame(width: 20)
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

    private func saveDetailSettings() async -> Bool {
        guard !isSavingDetail else { return false }
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
            NotificationCenter.default.post(name: .presetTimesUpdated, object: nil)
            return true
        } catch {
            return false
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

    private func loadInventoryThreshold() async {
        guard sessionStore.mode == .caregiver, let patientId = sessionStore.currentPatientId else {
            inventoryItems = []
            inventoryThresholdText = ""
            return
        }
        do {
            let items = try await apiClient.fetchInventory(patientId: patientId)
            inventoryItems = items
            if let first = items.first(where: { $0.inventoryEnabled }) {
                inventoryThresholdText = String(first.inventoryLowThreshold)
                Self.saveThresholdLocally(first.inventoryLowThreshold, for: patientId)
            } else if let saved = Self.loadSavedThreshold(for: patientId) {
                inventoryThresholdText = String(saved)
            } else {
                inventoryThresholdText = ""
            }
        } catch {
            if let saved = Self.loadSavedThreshold(for: patientId) {
                inventoryThresholdText = String(saved)
            }
            showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
        }
    }

    private func saveInventoryThreshold() async -> Bool {
        guard sessionStore.mode == .caregiver, let patientId = sessionStore.currentPatientId else {
            return false
        }
        let trimmed = inventoryThresholdText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let threshold = Int(trimmed), threshold >= 0 else {
            return false
        }
        isSavingDetail = true
        defer { isSavingDetail = false }
        do {
            let items = inventoryItems.isEmpty ? try await apiClient.fetchInventory(patientId: patientId) : inventoryItems
            for item in items {
                _ = try await apiClient.updateInventory(
                    patientId: patientId,
                    medicationId: item.medicationId,
                    input: InventoryUpdateRequestDTO(
                        inventoryEnabled: nil,
                        inventoryQuantity: nil,
                        inventoryLowThreshold: threshold
                    )
                )
            }
            inventoryItems = items.map { item in
                InventoryItemDTO(
                    medicationId: item.medicationId,
                    name: item.name,
                    isPrn: item.isPrn,
                    doseCountPerIntake: item.doseCountPerIntake,
                    inventoryEnabled: item.inventoryEnabled,
                    inventoryQuantity: item.inventoryQuantity,
                    inventoryLowThreshold: threshold,
                    periodEnded: item.periodEnded,
                    low: item.low,
                    out: item.out,
                    dailyPlannedUnits: item.dailyPlannedUnits,
                    daysRemaining: item.daysRemaining,
                    refillDueDate: item.refillDueDate
                )
            }
            Self.saveThresholdLocally(threshold, for: patientId)
            return true
        } catch {
            return false
        }
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

    // MARK: - Patient Limit Gate (009-free-limit-gates)

    /// Pre-flight gate for the "add patient" button.
    /// Checks entitlement state and patient count before allowing creation.
    private func handleAddPatientTapped() async {
        guard let entitlementStore else {
            // No entitlement store provided — allow creation (should not happen for caregivers)
            showingCreate = true
            return
        }

        // If entitlement state is unknown, refresh first (overlay shown via isRefreshing)
        if entitlementStore.state == .unknown {
            await entitlementStore.refresh()
        }

        // Check the gate
        if FeatureGate.canAddPatient(
            entitlementState: entitlementStore.state,
            currentPatientCount: viewModel.patients.count
        ) {
            showingCreate = true
        } else {
            showingPaywall = true
        }
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

    // MARK: - Inventory Threshold Local Persistence

    private static func saveThresholdLocally(_ threshold: Int, for patientId: String) {
        UserDefaults.standard.set(threshold, forKey: "\(thresholdKeyPrefix)\(patientId)")
    }

    private static func loadSavedThreshold(for patientId: String) -> Int? {
        let value = UserDefaults.standard.object(forKey: "\(thresholdKeyPrefix)\(patientId)")
        return value as? Int
    }
}
