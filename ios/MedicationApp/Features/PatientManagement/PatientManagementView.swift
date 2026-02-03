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
    @State private var showingCreate = false
    @State private var revokeTarget: PatientDTO?

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        _viewModel = StateObject(wrappedValue: PatientManagementViewModel(sessionStore: store))
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
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 16)
                    }
                }
            }
            .navigationTitle(NSLocalizedString("caregiver.patients.title", comment: "Patients title"))
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button(NSLocalizedString("caregiver.patients.add", comment: "Add patient")) {
                        showingCreate = true
                    }
                    Button(NSLocalizedString("common.logout", comment: "Logout")) {
                        sessionStore.clearCaregiverToken()
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
        }
        .accessibilityIdentifier("PatientManagementView")
    }
}
