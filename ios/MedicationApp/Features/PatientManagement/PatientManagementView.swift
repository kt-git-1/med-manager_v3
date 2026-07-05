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
                errorMessage = NSLocalizedString(
                    "caregiver.dataUnavailable.message",
                    comment: "Caregiver data unavailable message"
                )
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

    func deleteCaregiverAccount() async -> Bool {
        do {
            try await apiClient.deleteCaregiverAccount()
            patients.removeAll()
            return true
        } catch {
            print("PatientManagementViewModel: delete caregiver account failed: \(error.localizedDescription)")
            return false
        }
    }

    func updateSlotTimes(patientId: String, slotTimes: PatientSlotTimesDTO) {
        patients = patients.map { patient in
            guard patient.id == patientId else { return patient }
            return PatientDTO(id: patient.id, displayName: patient.displayName, slotTimes: slotTimes)
        }
    }
}

struct PatientManagementView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var globalBannerPresenter: GlobalBannerPresenter
    @EnvironmentObject private var toastPresenter: ToastPresenter
    @Environment(\.openURL) private var openURL
    @StateObject private var viewModel: PatientManagementViewModel
    @StateObject private var preferencesStore = NotificationPreferencesStore()
    @StateObject private var schedulingCoordinator = SchedulingRefreshCoordinator()
    @State private var showingCreate = false
    @State private var deleteTarget: PatientDTO?
    @State private var showingLogoutConfirm = false
    @State private var showingAccountDeleteConfirm = false
    @State private var isDeletingAccount = false
    @State private var draftTimes: [NotificationSlot: Date] = [:]
    @State private var isSavingDetail = false
    @State private var showingTimePresetSheet = false
    @State private var shouldShowPostCreateCodeGuide = false
    @StateObject private var pushSettingsViewModel: CaregiverPushSettingsViewModel
    private let timeZone = AppConstants.defaultTimeZone
    private let apiClient: APIClient
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
                    onSuccess: { message in
                        shouldShowPostCreateCodeGuide = true
                        showToast(message)
                    }
                )
            }
            .sheet(item: $viewModel.issuedCode) { code in
                PatientLinkCodeView(code: code)
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
        }
        .onAppear {
            viewModel.load()
            preferencesStore.switchPatient(viewModel.selectedPatientId)
            applySelectedPatientSlotTimes()
            draftTimes = buildDraftTimes()
            openCreateIfRequested()
        }
        .onReceive(viewModel.$patients) { _ in
            applySelectedPatientSlotTimes()
            draftTimes = buildDraftTimes()
        }
        .onChange(of: shouldOpenCreate.wrappedValue) { _, _ in
            openCreateIfRequested()
        }
        .onChange(of: viewModel.selectedPatientId) { _, newPatientId in
            preferencesStore.switchPatient(newPatientId)
            applySelectedPatientSlotTimes()
            draftTimes = buildDraftTimes()
        }
        .overlay {
            if isSavingDetail || pushSettingsViewModel.isUpdating || isDeletingAccount {
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
            CaregiverDataUnavailableView(
                message: errorMessage,
                onRetry: { viewModel.load() },
                onReturnToLogin: { sessionStore.returnToCaregiverLogin() }
            )
        } else if viewModel.patients.isEmpty {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CaregiverPatientHeader(
                        title: NSLocalizedString("caregiver.settings.title", comment: "Caregiver settings title"),
                        patientName: nil,
                        systemImage: "person.2.badge.gearshape.fill",
                        subtitle: NSLocalizedString("caregiver.common.patient.none", comment: "No patient")
                    )
                    CaregiverNoPatientEmptyStateView {
                        showingCreate = true
                    }
                    legalSupportSection
                    logoutSection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .padding(.bottom, 128)
            }
            .background(CaregiverUI.background)
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
                    postCreateCodeGuide
                    selectedPatientSection
                    detailSettingsSection
                    pushSettingsSection
                    legalSupportSection
                    logoutSection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .padding(.bottom, 128)
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
    private var postCreateCodeGuide: some View {
        if shouldShowPostCreateCodeGuide, let selectedPatient {
            CaregiverCard(accent: CaregiverUI.orange) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "link.badge.plus")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(CaregiverUI.orange)
                            .frame(width: 38, height: 38)
                            .background(CaregiverUI.orange.opacity(0.12), in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text(NSLocalizedString("caregiver.patients.postCreateCodeGuide.title", comment: "Post create code guide title"))
                                .font(.headline.weight(.bold))
                            Text(NSLocalizedString("caregiver.patients.postCreateCodeGuide.message", comment: "Post create code guide message"))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    HStack(spacing: 12) {
                        Button {
                            shouldShowPostCreateCodeGuide = false
                            Task { await viewModel.issueLinkingCode(patientId: selectedPatient.id) }
                        } label: {
                            Label(NSLocalizedString("caregiver.patients.issueCode", comment: "Issue code"), systemImage: "link.badge.plus")
                                .font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .background(CaregiverUI.orange, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)

                        Button {
                            shouldShowPostCreateCodeGuide = false
                        } label: {
                            Text(NSLocalizedString("common.close", comment: "Close"))
                                .font(.subheadline.weight(.bold))
                                .frame(width: 82, height: 44)
                                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .transition(.opacity.combined(with: .move(edge: .top)))
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

                Button {
                    showingAccountDeleteConfirm = true
                } label: {
                    Label(NSLocalizedString("caregiver.account.delete", comment: "Delete caregiver account"), systemImage: "trash")
                        .font(.headline)
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Color.red.opacity(0.35), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(isDeletingAccount)
                .alert(
                    NSLocalizedString("caregiver.account.delete.confirm.title", comment: "Delete account title"),
                    isPresented: $showingAccountDeleteConfirm
                ) {
                    Button(NSLocalizedString("common.cancel", comment: "Cancel"), role: .cancel) {}
                    Button(NSLocalizedString("caregiver.account.delete.confirm.action", comment: "Delete account action"), role: .destructive) {
                        Task { await deleteAccount() }
                    }
                } message: {
                    Text(NSLocalizedString("caregiver.account.delete.confirm.message", comment: "Delete account message"))
                }
            }
        }
    }

    private var legalSupportSection: some View {
        CaregiverCard {
            VStack(alignment: .leading, spacing: 14) {
                settingsGroupHeader(
                    title: NSLocalizedString("legal.section.title", comment: "Legal and support section title"),
                    message: NSLocalizedString("legal.section.message", comment: "Legal and support section message"),
                    systemImage: "doc.text.magnifyingglass"
                )

                settingsActionRow(
                    title: NSLocalizedString("legal.privacy.title", comment: "Privacy policy title"),
                    message: NSLocalizedString("legal.privacy.message", comment: "Privacy policy message"),
                    systemImage: "hand.raised.fill",
                    tint: CaregiverUI.teal
                ) {
                    openURL(AppConstants.privacyPolicyURL)
                }

                settingsActionRow(
                    title: NSLocalizedString("legal.terms.title", comment: "Terms title"),
                    message: NSLocalizedString("legal.terms.message", comment: "Terms message"),
                    systemImage: "doc.text.fill",
                    tint: CaregiverUI.blue
                ) {
                    openURL(AppConstants.termsURL)
                }

                settingsActionRow(
                    title: NSLocalizedString("legal.support.title", comment: "Support title"),
                    message: NSLocalizedString("legal.support.message", comment: "Support message"),
                    systemImage: "questionmark.circle.fill",
                    tint: CaregiverUI.orange
                ) {
                    openURL(AppConstants.supportURL)
                }
            }
        }
    }

    private func deleteAccount() async {
        guard !isDeletingAccount else { return }
        isDeletingAccount = true
        let success = await viewModel.deleteCaregiverAccount()
        isDeletingAccount = false
        if success {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                sessionStore.resetAfterAccountDeletion()
            }
            toastPresenter.show(
                NSLocalizedString("caregiver.account.delete.toast", comment: "Account deleted toast"),
                kind: .success,
                duration: 3
            )
        } else {
            showToast(NSLocalizedString("caregiver.account.delete.failed", comment: "Account delete failed"), kind: .error)
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

    private func applySelectedPatientSlotTimes() {
        guard let slotTimes = selectedPatient?.slotTimes else { return }
        applySlotTime(slotTimes.morning, to: .morning)
        applySlotTime(slotTimes.noon, to: .noon)
        applySlotTime(slotTimes.evening, to: .evening)
        applySlotTime(slotTimes.bedtime, to: .bedtime)
    }

    private func applySlotTime(_ value: String, to slot: NotificationSlot) {
        guard let parsed = parseTime(value) else { return }
        preferencesStore.setSlotTime(slot, hour: parsed.hour, minute: parsed.minute)
    }

    private func parseTime(_ value: String) -> (hour: Int, minute: Int)? {
        let parts = value.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2,
              (0...23).contains(parts[0]),
              (0...59).contains(parts[1]) else {
            return nil
        }
        return (parts[0], parts[1])
    }

    private func currentSlotTimesDTO() -> PatientSlotTimesDTO {
        let morning = preferencesStore.slotTime(for: .morning)
        let noon = preferencesStore.slotTime(for: .noon)
        let evening = preferencesStore.slotTime(for: .evening)
        let bedtime = preferencesStore.slotTime(for: .bedtime)
        return PatientSlotTimesDTO(
            morning: String(format: "%02d:%02d", morning.hour, morning.minute),
            noon: String(format: "%02d:%02d", noon.hour, noon.minute),
            evening: String(format: "%02d:%02d", evening.hour, evening.minute),
            bedtime: String(format: "%02d:%02d", bedtime.hour, bedtime.minute)
        )
    }

    private func saveDetailSettings() async -> Bool {
        guard !isSavingDetail else { return false }
        guard let patientId = viewModel.selectedPatientId else { return false }
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
            let savedSlotTimes = try await apiClient.updatePatientSlotTimes(
                patientId: patientId,
                slotTimes: currentSlotTimesDTO()
            )
            viewModel.updateSlotTimes(patientId: patientId, slotTimes: savedSlotTimes)
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
            if let oldTime = oldSlotTimes[slot] {
                let oldString = String(format: "%02d:%02d", oldTime.hour, oldTime.minute)
                mapping[oldString] = slot.rawValue
            }
            let defaultTime = slot.hourMinute
            let defaultString = String(format: "%02d:%02d", defaultTime.hour, defaultTime.minute)
            mapping[defaultString] = slot.rawValue
            let scheduleDefault = defaultScheduleTimeString(for: slot)
            mapping[scheduleDefault] = slot.rawValue
            let configured = preferencesStore.slotTime(for: slot)
            let configuredString = String(format: "%02d:%02d", configured.hour, configured.minute)
            mapping[configuredString] = slot.rawValue
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

    private func showToast(_ message: String, kind: ToastKind = .success) {
        toastPresenter.show(message, kind: kind)
    }

}
