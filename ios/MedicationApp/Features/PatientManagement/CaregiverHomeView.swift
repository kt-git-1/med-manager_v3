import SwiftUI

enum CaregiverTab: Hashable {
    case medications
    case patients
}

struct CaregiverHomeView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var selectedTab: CaregiverTab = .medications

    var body: some View {
        TabView(selection: $selectedTab) {
            CaregiverMedicationView(
                sessionStore: sessionStore,
                onOpenPatients: { selectedTab = .patients }
            )
                .tabItem {
                    Label(
                        NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab"),
                        systemImage: "pills"
                    )
                }
                .tag(CaregiverTab.medications)
            PatientManagementView(sessionStore: sessionStore)
                .tabItem {
                    Label(
                        NSLocalizedString("caregiver.tabs.patients", comment: "Patients tab"),
                        systemImage: "person.2"
                    )
                }
                .tag(CaregiverTab.patients)
        }
        .onChange(of: sessionStore.shouldRedirectCaregiverToMedicationTab) { _, shouldRedirect in
            guard shouldRedirect else { return }
            selectedTab = .medications
            sessionStore.shouldRedirectCaregiverToMedicationTab = false
        }
    }
}

@MainActor
final class CaregiverMedicationViewModel: ObservableObject {
    @Published var patients: [PatientDTO] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func loadPatients() {
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
}

struct CaregiverMedicationView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    @StateObject private var viewModel: CaregiverMedicationViewModel

    init(sessionStore: SessionStore, onOpenPatients: @escaping () -> Void) {
        self.sessionStore = sessionStore
        self.onOpenPatients = onOpenPatients
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverMedicationViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore)
            )
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                } else if let errorMessage = viewModel.errorMessage {
                    ErrorStateView(message: errorMessage)
                } else if viewModel.patients.isEmpty {
                    VStack(spacing: 12) {
                        EmptyStateView(
                            title: NSLocalizedString("caregiver.medications.noPatients.title", comment: "No patients title"),
                            message: NSLocalizedString("caregiver.medications.noPatients.message", comment: "No patients message")
                        )
                        Button(NSLocalizedString("caregiver.medications.noPatients.action", comment: "Go to patients action")) {
                            onOpenPatients()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else if sessionStore.currentPatientId == nil {
                    VStack(spacing: 12) {
                        EmptyStateView(
                            title: NSLocalizedString("caregiver.medications.noSelection.title", comment: "No selection title"),
                            message: NSLocalizedString("caregiver.medications.noSelection.message", comment: "No selection message")
                        )
                        Button(NSLocalizedString("caregiver.medications.noSelection.action", comment: "Go to patients action")) {
                            onOpenPatients()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else {
                    let selectedPatient = viewModel.patients.first { $0.id == sessionStore.currentPatientId }
                    MedicationListView(
                        sessionStore: sessionStore,
                        selectedPatientName: selectedPatient?.displayName,
                        onOpenPatients: onOpenPatients
                    )
                    .frame(maxHeight: .infinity, alignment: .top)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .navigationTitle(NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab"))
            .navigationBarTitleDisplayMode(.large)
        }
        .onAppear {
            viewModel.loadPatients()
        }
        .accessibilityIdentifier("CaregiverMedicationView")
    }
}
