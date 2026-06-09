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
                if patients.count == 1, selectedPatientId == nil {
                    setSelectedPatientId(patients[0].id)
                }
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
            setSelectedPatientId(created.id)
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
    @EnvironmentObject private var toastPresenter: ToastPresenter
    @StateObject private var viewModel: PatientManagementViewModel
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @State private var showingCreate = false
    @State private var revokeTarget: PatientDTO?
    @State private var deleteTarget: PatientDTO?
    @State private var showingLogoutConfirm = false
    @State private var draftTimes: [NotificationSlot: Date] = [:]
    @State private var isSavingDetail = false
    @State private var inventoryThresholdText = ""
    @State private var inventoryItems: [InventoryItemDTO] = []
    @State private var showingTimePresetSheet = false
    @State private var showingInventoryThresholdSheet = false
    @StateObject private var pushSettingsViewModel: CaregiverPushSettingsViewModel
    private let timeZone = AppConstants.defaultTimeZone
    private let apiClient: APIClient
    private static let thresholdKeyPrefix = "inventory.threshold."
    var entitlementStore: EntitlementStore?
    private var shouldOpenCreate: Binding<Bool>

    init(
        sessionStore: SessionStore? = nil,
        entitlementStore: EntitlementStore? = nil,
        shouldOpenCreate: Binding<Bool> = .constant(false)
    ) {
        let store = sessionStore ?? SessionStore()
        _viewModel = StateObject(wrappedValue: PatientManagementViewModel(sessionStore: store))
        let client = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: store)
        self.apiClient = client
        _pushSettingsViewModel = StateObject(wrappedValue: CaregiverPushSettingsViewModel(
            apiClientFactory: {
                APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: store)
            }
        ))
        self.entitlementStore = entitlementStore
        self.shouldOpenCreate = shouldOpenCreate
    }

    var body: some View {
        NavigationStack {
            contentView
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showingCreate) {
                PatientCreateView(
                    onSave: { displayName in
                        await viewModel.createPatient(displayName: displayName)
                    },
                    onSuccess: { showToast($0) }
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
                                    showToast(
                                        success
                                            ? NSLocalizedString("caregiver.timePreset.toast.updated", comment: "Time preset updated toast")
                                            : NSLocalizedString("common.error.save", comment: "Save error"),
                                        kind: success ? .success : .error
                                    )
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
                                    showToast(
                                        success
                                            ? NSLocalizedString("caregiver.inventory.toast.saved", comment: "Inventory saved")
                                            : NSLocalizedString("common.error.save", comment: "Save error"),
                                        kind: success ? .success : .error
                                    )
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
            openCreateIfRequested()
        }
        .onChange(of: shouldOpenCreate.wrappedValue) { _, _ in
            openCreateIfRequested()
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
        .overlay {
            if isSavingDetail || pushSettingsViewModel.isUpdating {
                SchedulingRefreshOverlay()
            }
        }
        .onChange(of: viewModel.isPatientLimitExceeded) { _, exceeded in
            if exceeded {
                showingCreate = false
                showToast(NSLocalizedString("caregiver.patients.limit.initialRelease", comment: "Initial release patient limit"), kind: .warning)
                viewModel.isPatientLimitExceeded = false
            }
        }
        .accessibilityIdentifier("PatientManagementView")
    }

    @ViewBuilder
    private var contentView: some View {
        if viewModel.isLoading {
            LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let errorMessage = viewModel.errorMessage {
            ErrorStateView(message: errorMessage)
        } else if viewModel.patients.isEmpty {
            CaregiverNoPatientEmptyStateView {
                showingCreate = true
            }
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CaregiverPatientHeader(
                        title: NSLocalizedString("caregiver.settings.title", comment: "Caregiver settings title"),
                        patientName: selectedPatient?.displayName,
                        systemImage: "person.2.badge.gearshape.fill",
                        subtitle: selectedPatient == nil ? NSLocalizedString("caregiver.common.patient.none", comment: "No patient") : nil
                    )
                    selectionCard
                    selectedPatientSection
                    detailSettingsSection
                    pushSettingsSection
                    logoutSection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .padding(.bottom, 64)
            }
            .background(CaregiverUI.background)
        }
    }

    private func openCreateIfRequested() {
        guard shouldOpenCreate.wrappedValue else { return }
        shouldOpenCreate.wrappedValue = false
        showingCreate = true
    }

    private var selectionCard: some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.badge.checkmark")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(CaregiverUI.teal)
                        .frame(width: 34, height: 34)
                        .background(CaregiverUI.teal.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("caregiver.settings.patient.title", comment: "Settings patient title"))
                            .font(.headline.weight(.bold))
                        Text(viewModel.patients.count > 1
                            ? NSLocalizedString("caregiver.patients.select.help", comment: "Select help text")
                            : NSLocalizedString("caregiver.patients.single.help", comment: "Single patient help text"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            if viewModel.patients.count > 1 {
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
                .tint(CaregiverUI.tealDark)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(CaregiverUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            } else if let patient = viewModel.patients.first {
                HStack {
                    Text(patient.displayName)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(CaregiverUI.tealDark)
                    Spacer(minLength: 0)
                    Image(systemName: "checkmark.circle.fill")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(CaregiverUI.teal)
                }
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(CaregiverUI.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        }
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
                    Text(viewModel.patients.count > 1
                        ? NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label")
                        : NSLocalizedString("caregiver.patients.single.status", comment: "Single patient status"))
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
            .padding(18)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(CaregiverUI.teal.opacity(0.24), lineWidth: 1))
            .shadow(color: CaregiverUI.cardShadow, radius: 10, y: 4)
            .accessibilityLabel("\(selectedPatient.displayName) \(selectedPatientStatusText)")
        } else {
            EmptyStateView(
                title: NSLocalizedString("caregiver.patients.select.empty.title", comment: "No selection title"),
                message: NSLocalizedString("caregiver.patients.select.empty.message", comment: "No selection message")
            )
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(CaregiverUI.cardStroke, lineWidth: 1))
        }
    }

    @ViewBuilder
    private var detailSettingsSection: some View {
        if viewModel.selectedPatientId != nil {
            CaregiverCard {
                VStack(alignment: .leading, spacing: 14) {
                    settingsGroupHeader(
                        title: NSLocalizedString("caregiver.settings.section.detail", comment: "Detail settings header"),
                        message: NSLocalizedString("caregiver.settings.detail.message", comment: "Detail settings message"),
                        systemImage: "slider.horizontal.3"
                    )
                    settingsActionRow(
                        title: NSLocalizedString("patient.settings.notifications.detail.item", comment: "Detail settings item"),
                        message: NSLocalizedString("patient.settings.notifications.detail.note", comment: "Detail settings note"),
                        systemImage: "clock.fill",
                        tint: CaregiverUI.blue
                    ) {
                        draftTimes = buildDraftTimes()
                        showingTimePresetSheet = true
                    }
                    Divider()
                    settingsActionRow(
                        title: NSLocalizedString("caregiver.inventory.settings.item.threshold", comment: "Inventory threshold item"),
                        message: NSLocalizedString("caregiver.inventory.settings.note", comment: "Inventory settings note"),
                        systemImage: "archivebox.fill",
                        tint: CaregiverUI.orange
                    ) {
                        showingInventoryThresholdSheet = true
                    }
                }
            }
        }
    }

    private var selectedPatient: PatientDTO? {
        viewModel.patients.first { $0.id == viewModel.selectedPatientId }
    }

    private var selectedPatientStatusText: String {
        viewModel.patients.count > 1
            ? NSLocalizedString("caregiver.patients.select.selected", comment: "Selected label")
            : NSLocalizedString("caregiver.patients.single.status", comment: "Single patient status")
    }

    private func settingsGroupHeader(title: String, message: String, systemImage: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .font(.headline.weight(.bold))
                .foregroundStyle(CaregiverUI.teal)
                .frame(width: 34, height: 34)
                .background(CaregiverUI.teal.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.primary)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func settingsActionRow(
        title: String,
        message: String,
        systemImage: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: systemImage)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(tint)
                    .frame(width: 32, height: 32)
                    .background(tint.opacity(0.12), in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.primary)
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var logoutSection: some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 14) {
                settingsGroupHeader(
                    title: NSLocalizedString("caregiver.settings.account.title", comment: "Account settings title"),
                    message: NSLocalizedString("caregiver.settings.account.message", comment: "Account settings message"),
                    systemImage: "person.crop.circle.fill"
                )
            Button {
                sessionStore.resetMode()
            } label: {
                Label(
                    NSLocalizedString("settings.changeMode", comment: "Change app mode"),
                    systemImage: "arrow.left.arrow.right.circle"
                )
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.accentColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("caregiver.settings.changeMode")

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
        }
    }

    // MARK: - Push Notification Settings (012-push-foundation)

    private var pushSettingsSection: some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 12) {
                settingsGroupHeader(
                    title: NSLocalizedString("caregiver.settings.push.section.title", comment: "Push section title"),
                    message: pushSettingsViewModel.isPushEnabled
                        ? NSLocalizedString("caregiver.settings.push.enabled", comment: "Push enabled")
                        : NSLocalizedString("caregiver.settings.push.disabled", comment: "Push disabled"),
                    systemImage: "bell.fill"
                )
                Toggle(
                    NSLocalizedString("caregiver.settings.push.toggle", comment: "Push toggle"),
                    isOn: Binding(
                        get: { pushSettingsViewModel.isPushEnabled },
                        set: { newValue in
                            Task {
                                await pushSettingsViewModel.togglePush(enabled: newValue)
                            }
                        }
                    )
                )
                .accessibilityIdentifier("PushNotificationToggle")
                .disabled(pushSettingsViewModel.isUpdating)
            }
        }
        .alert(
            NSLocalizedString("caregiver.settings.push.error", comment: "Error alert title"),
            isPresented: Binding(
                get: { pushSettingsViewModel.errorMessage != nil },
                set: { if !$0 { pushSettingsViewModel.errorMessage = nil } }
            ),
            actions: {
                Button(NSLocalizedString("common.ok", comment: "OK"), role: .cancel) {
                    pushSettingsViewModel.errorMessage = nil
                }
            },
            message: {
                if let message = pushSettingsViewModel.errorMessage {
                    Text(message)
                }
            }
        )
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
            try await migrateRegimensToPresetSlots(oldSlotTimes: oldSlotTimes)
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

    private func migrateRegimensToPresetSlots(oldSlotTimes: [NotificationSlot: (hour: Int, minute: Int)]) async throws {
        guard sessionStore.mode == .caregiver, let patientId = sessionStore.currentPatientId else {
            return
        }
        let medications = try await apiClient.fetchMedications(patientId: patientId)
        let mapping = buildLegacyTimeSlotMapping(oldSlotTimes: oldSlotTimes)
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

    private func buildLegacyTimeSlotMapping(oldSlotTimes: [NotificationSlot: (hour: Int, minute: Int)]) -> [String: String] {
        var mapping: [String: String] = [:]
        for slot in NotificationSlot.allCases {
            let configured = preferencesStore.slotTime(for: slot)
            let configuredString = String(format: "%02d:%02d", configured.hour, configured.minute)
            if let oldTime = oldSlotTimes[slot] {
                let oldString = String(format: "%02d:%02d", oldTime.hour, oldTime.minute)
                mapping[oldString] = configuredString
            }
            let defaultTime = slot.hourMinute
            let defaultString = String(format: "%02d:%02d", defaultTime.hour, defaultTime.minute)
            mapping[defaultString] = configuredString
            let scheduleDefault = defaultScheduleTimeString(for: slot)
            mapping[scheduleDefault] = configuredString
            mapping[slot.rawValue] = configuredString
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
            showToast(NSLocalizedString("common.error.generic", comment: "Generic error"), kind: .error)
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

    private func showToast(_ message: String, kind: ToastKind = .success) {
        toastPresenter.show(message, kind: kind)
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
