import SwiftUI

enum CaregiverTab: Hashable {
    case medications
    case history
    case patients
}

struct CaregiverHomeView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var selectedTab: CaregiverTab = .medications
    @State private var currentPatientName: String?

    var body: some View {
        ZStack {
            switch selectedTab {
            case .medications:
                CaregiverMedicationView(
                    sessionStore: sessionStore,
                    onOpenPatients: { selectedTab = .patients }
                )
            case .history:
                NavigationStack {
                    CaregiverHistoryView(
                        sessionStore: sessionStore,
                        onOpenPatients: { selectedTab = .patients }
                    )
                    .navigationTitle(NSLocalizedString("caregiver.tabs.history", comment: "History tab"))
                    .navigationBarTitleDisplayMode(.inline)
                }
            case .patients:
                PatientManagementView(sessionStore: sessionStore)
            }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                if let patientName = currentPatientName {
                    HStack {
                        Text(
                            String(
                                format: NSLocalizedString(
                                    "caregiver.medications.currentPatient.inline",
                                    comment: "Current patient inline label"
                                ),
                                patientName
                            )
                        )
                        .font(.caption)
                        .foregroundColor(.accentColor)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.accentColor.opacity(0.12), in: Capsule())
                        .overlay(
                            Capsule()
                                .stroke(Color.accentColor, lineWidth: 1)
                        )
                        Spacer()
                    }
                }
                CaregiverBottomTabBar(selectedTab: $selectedTab)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 6)
        }
        .onAppear {
            loadCurrentPatientName()
        }
        .onChange(of: sessionStore.currentPatientId) { _, _ in
            loadCurrentPatientName()
        }
        .onChange(of: sessionStore.mode) { _, _ in
            loadCurrentPatientName()
        }
        .onChange(of: sessionStore.shouldRedirectCaregiverToMedicationTab) { _, shouldRedirect in
            guard shouldRedirect else { return }
            selectedTab = .medications
            sessionStore.shouldRedirectCaregiverToMedicationTab = false
        }
    }

    private func loadCurrentPatientName() {
        guard sessionStore.mode == .caregiver,
              let patientId = sessionStore.currentPatientId else {
            currentPatientName = nil
            return
        }
        Task { @MainActor in
            do {
                let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
                let patients = try await apiClient.listPatients()
                currentPatientName = patients.first { $0.id == patientId }?.displayName
            } catch {
                currentPatientName = nil
            }
        }
    }
}

private struct CaregiverBottomTabBar: View {
    @Binding var selectedTab: CaregiverTab

    var body: some View {
        HStack(spacing: 12) {
            tabButton(
                title: NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab"),
                systemImage: "pills",
                isSelected: selectedTab == .medications
            ) {
                selectedTab = .medications
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.history", comment: "History tab"),
                systemImage: "clock",
                isSelected: selectedTab == .history
            ) {
                selectedTab = .history
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.patients", comment: "Patients tab"),
                systemImage: "person.2",
                isSelected: selectedTab == .patients
            ) {
                selectedTab = .patients
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(
            Capsule()
                .strokeBorder(Color(.separator).opacity(0.4))
        )
        .shadow(color: Color.black.opacity(0.12), radius: 18, y: 10)
    }

    private func tabButton(
        title: String,
        systemImage: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .semibold))
                Text(title)
                    .font(.headline)
            }
            .foregroundColor(isSelected ? .accentColor : .secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(isSelected ? Color(.secondarySystemBackground) : Color.clear)
            )
        }
        .buttonStyle(.plain)
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
    @State private var selectedSection: MedicationScheduleSection = .medications

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
                        .font(.headline)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                    .padding(.horizontal, 24)
                } else if sessionStore.currentPatientId == nil {
                    VStack(spacing: 12) {
                        Spacer(minLength: 0)
                        VStack(spacing: 12) {
                            Text(NSLocalizedString("caregiver.medications.noSelection.title", comment: "No selection title"))
                                .font(.title3.weight(.semibold))
                                .multilineTextAlignment(.center)
                            Text(NSLocalizedString("caregiver.medications.noSelection.message", comment: "No selection message"))
                                .font(.body)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                            Button(NSLocalizedString("caregiver.medications.noSelection.action", comment: "Go to patients action")) {
                                onOpenPatients()
                            }
                            .buttonStyle(.borderedProminent)
                            .font(.headline)
                            .padding(.top, 4)
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 16)
                        .frame(maxWidth: .infinity)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                        .padding(.horizontal, 24)
                        Spacer(minLength: 0)
                    }
                } else {
                    switch selectedSection {
                    case .medications:
                        MedicationListView(
                            sessionStore: sessionStore,
                            onOpenPatients: onOpenPatients,
                            headerView: AnyView(headerView)
                        )
                    case .today:
                        CaregiverTodayView(
                            sessionStore: sessionStore,
                            onOpenPatients: onOpenPatients,
                            headerView: AnyView(headerView)
                        )
                    }
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

    private var headerView: some View {
        Picker("", selection: $selectedSection) {
            Text(NSLocalizedString("caregiver.medications.segment.medications", comment: "Medications list segment"))
                .tag(MedicationScheduleSection.medications)
            Text(NSLocalizedString("caregiver.medications.segment.today", comment: "Today schedule segment"))
                .tag(MedicationScheduleSection.today)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .padding(.bottom, 4)
    }
}

private enum MedicationScheduleSection: Hashable {
    case medications
    case today
}
