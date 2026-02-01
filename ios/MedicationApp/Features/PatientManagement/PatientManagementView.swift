import SwiftUI

@MainActor
final class PatientManagementViewModel: ObservableObject {
    @Published var patients: [PatientDTO] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var issuedCode: LinkingCodeDTO?

    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    convenience init(sessionStore: SessionStore) {
        let baseURL = SessionStore.resolveBaseURL()
        self.init(apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore))
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
                } else {
                    List(viewModel.patients) { patient in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(patient.displayName)
                                .font(.headline)
                            HStack {
                                Button(NSLocalizedString("caregiver.patients.issueCode", comment: "Issue code")) {
                                    Task { await viewModel.issueLinkingCode(patientId: patient.id) }
                                }
                                .buttonStyle(.bordered)
                                Button(NSLocalizedString("caregiver.patients.revoke", comment: "Revoke")) {
                                    revokeTarget = patient
                                }
                                .buttonStyle(.bordered)
                                .tint(.red)
                            }
                        }
                    }
                }
            }
            .navigationTitle(NSLocalizedString("caregiver.patients.title", comment: "Patients title"))
            .toolbar {
                Button(NSLocalizedString("caregiver.patients.add", comment: "Add patient")) {
                    showingCreate = true
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
