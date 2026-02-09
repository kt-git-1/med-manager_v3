import SwiftUI

enum CaregiverTab: Hashable {
    case medications
    case history
    case inventory
    case patients
}

struct CaregiverHomeView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var selectedTab: CaregiverTab = .medications
    @State private var currentPatientName: String?
    @State private var hasLowStock = false

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
                    .navigationTitle("")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        NavigationHeaderView(
                            icon: "clock.circle.fill",
                            title: NSLocalizedString("caregiver.tabs.history", comment: "History tab")
                        )
                    }
                }
            case .inventory:
                NavigationStack {
                    InventoryListView(
                        sessionStore: sessionStore,
                        onOpenPatients: { selectedTab = .patients }
                    )
                }
            case .patients:
                PatientManagementView(sessionStore: sessionStore)
            }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 6) {
                patientIndicator
                CaregiverBottomTabBar(selectedTab: $selectedTab, hasLowStock: hasLowStock)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 6)
        }
        .onAppear {
            loadCurrentPatientName()
            checkLowStock()
        }
        .onChange(of: sessionStore.currentPatientId) { _, _ in
            loadCurrentPatientName()
            checkLowStock()
        }
        .onChange(of: sessionStore.mode) { _, _ in
            loadCurrentPatientName()
            checkLowStock()
        }
        .onChange(of: selectedTab) { _, newTab in
            if newTab == .inventory || newTab == .medications {
                checkLowStock()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .medicationUpdated)) { _ in
            checkLowStock()
        }
        .onChange(of: sessionStore.shouldRedirectCaregiverToMedicationTab) { _, shouldRedirect in
            guard shouldRedirect else { return }
            selectedTab = .medications
            sessionStore.shouldRedirectCaregiverToMedicationTab = false
        }
    }

    // MARK: - Patient Indicator

    @ViewBuilder
    private var patientIndicator: some View {
        if let name = currentPatientName {
            HStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color.accentColor, Color.accentColor.opacity(0.7)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 24, height: 24)
                    Text(String(name.prefix(1)))
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                }
                Text(name)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .padding(.leading, 10)
            .padding(.trailing, 14)
            .padding(.vertical, 6)
            .background(.thinMaterial, in: Capsule())
            .frame(maxWidth: .infinity, alignment: .leading)
            .transition(.opacity.combined(with: .move(edge: .bottom)))
        }
    }

    // MARK: - Data Loading

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

    private func checkLowStock() {
        guard sessionStore.mode == .caregiver,
              sessionStore.currentPatientId != nil else {
            hasLowStock = false
            return
        }
        Task { @MainActor in
            do {
                let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
                let items = try await apiClient.fetchInventory()
                hasLowStock = items.contains { $0.inventoryEnabled && ($0.low || $0.out) }
            } catch {
                // Keep the current value on error
            }
        }
    }
}

private struct CaregiverBottomTabBar: View {
    @Binding var selectedTab: CaregiverTab
    var hasLowStock: Bool = false

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
                title: NSLocalizedString("caregiver.tabs.inventory", comment: "Inventory tab"),
                systemImage: "archivebox",
                isSelected: selectedTab == .inventory,
                showBadge: hasLowStock
            ) {
                selectedTab = .inventory
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.patients", comment: "Patients tab"),
                systemImage: "person.2",
                isSelected: selectedTab == .patients
            ) {
                selectedTab = .patients
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }

    private func tabButton(
        title: String,
        systemImage: String,
        isSelected: Bool,
        showBadge: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 22, weight: .semibold))
                    .overlay(alignment: .topTrailing) {
                        if showBadge {
                            Circle()
                                .fill(.red)
                                .frame(width: 9, height: 9)
                                .offset(x: 4, y: -3)
                        }
                    }
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
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
                    VStack(spacing: 16) {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.system(size: 44))
                            .foregroundStyle(.secondary)
                        EmptyStateView(
                            title: NSLocalizedString("caregiver.medications.noPatients.title", comment: "No patients title"),
                            message: NSLocalizedString("caregiver.medications.noPatients.message", comment: "No patients message")
                        )
                        Button {
                            onOpenPatients()
                        } label: {
                            Text(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab"))
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .glassEffect(.regular, in: .rect(cornerRadius: 20))
                    .padding(.horizontal, 24)
                } else if sessionStore.currentPatientId == nil {
                    VStack(spacing: 12) {
                        Spacer(minLength: 0)
                        VStack(spacing: 16) {
                            Image(systemName: "person.crop.circle.badge.questionmark")
                                .font(.system(size: 44))
                                .foregroundStyle(.secondary)
                            Text(NSLocalizedString("caregiver.medications.noSelection.title", comment: "No selection title"))
                                .font(.title3.weight(.semibold))
                                .multilineTextAlignment(.center)
                            Text(NSLocalizedString("caregiver.medications.noSelection.message", comment: "No selection message"))
                                .font(.body)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                            Button {
                                onOpenPatients()
                            } label: {
                                Text(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab"))
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                            }
                        }
                        .padding(24)
                        .frame(maxWidth: .infinity)
                        .glassEffect(.regular, in: .rect(cornerRadius: 20))
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
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                NavigationHeaderView(
                    icon: "pills.circle.fill",
                    title: NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab")
                )
            }
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
