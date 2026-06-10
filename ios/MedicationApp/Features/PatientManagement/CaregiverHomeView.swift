import SwiftUI

enum CaregiverTab: Hashable {
    case medications
    case today
    case history
    case inventory
    case patients
}

struct CaregiverHomeView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var notificationRouter: NotificationDeepLinkRouter
    @State private var selectedTab: CaregiverTab = .today
    @State private var currentPatientName: String?
    @State private var hasAnyPatient: Bool?
    @State private var hasLowStock = false
    @State private var deepLinkTarget: NotificationDeepLinkTarget?
    @State private var shouldOpenCreatePatient = false
    var entitlementStore: EntitlementStore?

    var body: some View {
        ZStack {
            switch selectedTab {
            case .today:
                CaregiverTodayTabView(
                    sessionStore: sessionStore,
                    patientName: currentPatientName,
                    onOpenPatients: { openPatientSettings() },
                    onCreatePatient: { openPatientCreate() }
                )
            case .medications:
                CaregiverMedicationView(
                    sessionStore: sessionStore,
                    onOpenPatients: { openPatientSettings() },
                    onCreatePatient: { openPatientCreate() }
                )
            case .history:
                NavigationStack {
                    CaregiverHistoryView(
                        sessionStore: sessionStore,
                        entitlementStore: entitlementStore,
                        patientName: currentPatientName,
                        hasAnyPatient: hasAnyPatient,
                        deepLinkTarget: $deepLinkTarget,
                        onOpenPatients: { openPatientSettings() },
                        onCreatePatient: { openPatientCreate() }
                    )
                }
            case .inventory:
                NavigationStack {
                    InventoryListView(
                        sessionStore: sessionStore,
                        onOpenPatients: { openPatientSettings() },
                        onCreatePatient: { openPatientCreate() },
                        hasAnyPatient: hasAnyPatient,
                        patientName: currentPatientName
                    )
                }
            case .patients:
                PatientManagementView(
                    sessionStore: sessionStore,
                    entitlementStore: entitlementStore,
                    shouldOpenCreate: $shouldOpenCreatePatient
                )
            }
        }
        .safeAreaInset(edge: .bottom) {
            CaregiverBottomTabBar(selectedTab: $selectedTab, hasLowStock: hasLowStock)
            .padding(.horizontal, 12)
            .padding(.bottom, 4)
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
            if newTab == .inventory || newTab == .medications || newTab == .today {
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
        .onReceive(notificationRouter.$target) { newTarget in
            guard let target = newTarget,
                  sessionStore.mode == .caregiver else { return }
            selectedTab = .history
            deepLinkTarget = target
            notificationRouter.clear()
        }
    }

    // MARK: - Data Loading

    private func loadCurrentPatientName() {
        guard sessionStore.mode == .caregiver else {
            currentPatientName = nil
            return
        }
        Task { @MainActor in
            do {
                let apiClient = APIClient(baseURL: SessionStore.resolveBaseURL(), sessionStore: sessionStore)
                let patients = try await apiClient.listPatients()
                hasAnyPatient = !patients.isEmpty
                let selectedPatient = patients.first { $0.id == sessionStore.currentPatientId }
                if let selectedPatient {
                    currentPatientName = selectedPatient.displayName
                } else {
                    if sessionStore.currentPatientId != nil {
                        sessionStore.clearCurrentPatientId()
                    }
                    if patients.count == 1, let onlyPatient = patients.first {
                        sessionStore.setCurrentPatientId(onlyPatient.id)
                        currentPatientName = onlyPatient.displayName
                    } else {
                        currentPatientName = nil
                    }
                }
            } catch {
                currentPatientName = nil
                hasAnyPatient = nil
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

    private func openPatientSettings() {
        selectedTab = .patients
    }

    private func openPatientCreate() {
        shouldOpenCreatePatient = true
        selectedTab = .patients
    }
}

private struct CaregiverBottomTabBar: View {
    @Binding var selectedTab: CaregiverTab
    var hasLowStock: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            tabButton(
                title: NSLocalizedString("caregiver.tabs.today", comment: "Today tab"),
                systemImage: "house.fill",
                isSelected: selectedTab == .today
            ) {
                selectedTab = .today
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab"),
                systemImage: "pills.fill",
                isSelected: selectedTab == .medications
            ) {
                selectedTab = .medications
            }
            tabButton(
                title: NSLocalizedString("caregiver.tabs.inventory", comment: "Inventory tab"),
                systemImage: "shippingbox.fill",
                isSelected: selectedTab == .inventory,
                showBadge: hasLowStock
            ) {
                selectedTab = .inventory
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
                systemImage: "gearshape.fill",
                isSelected: selectedTab == .patients
            ) {
                selectedTab = .patients
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 8)
        .padding(.bottom, 6)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.black.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.08), radius: 14, y: 4)
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
                    .font(.system(size: 24, weight: .semibold))
                    .overlay(alignment: .topTrailing) {
                        if showBadge {
                            Circle()
                                .fill(.red)
                                .frame(width: 9, height: 9)
                                .offset(x: 4, y: -3)
                        }
                    }
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.70)
            }
            .foregroundStyle(isSelected ? Color(red: 0.0, green: 0.55, blue: 0.50) : Color.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

enum CaregiverUI {
    static let teal = Color(red: 0.0, green: 0.55, blue: 0.50)
    static let tealDark = Color(red: 0.0, green: 0.43, blue: 0.40)
    static let blue = Color(red: 0.12, green: 0.48, blue: 0.82)
    static let orange = Color(red: 0.94, green: 0.42, blue: 0.0)
    static let red = Color(red: 0.82, green: 0.16, blue: 0.16)
    static let background = Color(red: 0.95, green: 0.98, blue: 0.99)
    static let cardStroke = Color.black.opacity(0.10)
    static let cardShadow = Color.black.opacity(0.06)
}

struct CaregiverScreenBackground<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.93, green: 0.98, blue: 1.0), Color(.systemGroupedBackground)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            content
        }
    }
}

struct CaregiverPatientHeader: View {
    let title: String
    let patientName: String?
    let systemImage: String
    var subtitle: String? = nil
    var trailing: AnyView?

    var body: some View {
        HStack(spacing: 14) {
            CaregiverAvatar(name: patientName, systemImage: systemImage)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(subtitle ?? patientNameText)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            Spacer(minLength: 0)
            if let trailing {
                trailing
            }
        }
    }

    private var patientNameText: String {
        guard let patientName, !patientName.isEmpty else {
            return NSLocalizedString("caregiver.common.patient.none", comment: "No patient selected")
        }
        return String(format: NSLocalizedString("caregiver.common.patient.format", comment: "Patient name format"), patientName)
    }
}

struct CaregiverAvatar: View {
    let name: String?
    var systemImage: String = "person.crop.circle.fill"

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white)
                .frame(width: 62, height: 62)
                .shadow(color: CaregiverUI.cardShadow, radius: 8, y: 3)
            if let initial = name?.prefix(1), !initial.isEmpty {
                Text(String(initial))
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(width: 50, height: 50)
                    .background(CaregiverUI.teal, in: Circle())
            } else {
                Image(systemName: systemImage)
                    .font(.system(size: 42))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(CaregiverUI.teal)
            }
        }
    }
}

struct CaregiverCard<Content: View>: View {
    var accent: Color?
    let content: Content

    init(accent: Color? = nil, @ViewBuilder content: () -> Content) {
        self.accent = accent
        self.content = content()
    }

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke((accent ?? CaregiverUI.cardStroke).opacity(accent == nil ? 1 : 0.55), lineWidth: accent == nil ? 1 : 1.5)
            }
            .shadow(color: CaregiverUI.cardShadow, radius: 12, y: 5)
    }
}

struct CaregiverStatusPill: View {
    let text: String
    let color: Color
    var systemImage: String?

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.caption.weight(.bold))
            }
            Text(text)
                .font(.caption.weight(.bold))
                .lineLimit(1)
                .minimumScaleFactor(0.74)
        }
        .foregroundStyle(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(color.opacity(0.13), in: Capsule())
    }
}

struct CaregiverPrimaryButton: View {
    let title: String
    let systemImage: String
    var color: Color = CaregiverUI.teal
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(color, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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
    private let sessionStore: SessionStore

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func loadPatients() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                patients = try await apiClient.listPatients()
                let selectedPatient = patients.first { $0.id == sessionStore.currentPatientId }
                if selectedPatient == nil, sessionStore.currentPatientId != nil {
                    sessionStore.clearCurrentPatientId()
                }
                if sessionStore.currentPatientId == nil, patients.count == 1, let onlyPatient = patients.first {
                    sessionStore.setCurrentPatientId(onlyPatient.id)
                }
            } catch {
                errorMessage = NSLocalizedString("caregiver.dataUnavailable.message", comment: "Caregiver data unavailable message")
            }
        }
    }
}

struct CaregiverMedicationView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void
    private let onCreatePatient: () -> Void
    @StateObject private var viewModel: CaregiverMedicationViewModel

    init(
        sessionStore: SessionStore,
        onOpenPatients: @escaping () -> Void,
        onCreatePatient: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.onOpenPatients = onOpenPatients
        self.onCreatePatient = onCreatePatient
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverMedicationViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else if let errorMessage = viewModel.errorMessage {
                    CaregiverDataUnavailableView(message: errorMessage) {
                        viewModel.loadPatients()
                    }
                } else if viewModel.patients.isEmpty {
                    CaregiverNoPatientEmptyStateView(onCreatePatient: onCreatePatient)
                } else if sessionStore.currentPatientId == nil {
                    CaregiverPatientSelectionRequiredView(
                        systemImage: "pills",
                        onOpenPatients: onOpenPatients
                    )
                } else {
                    MedicationListView(
                        sessionStore: sessionStore,
                        onOpenPatients: onOpenPatients,
                        patientName: viewModel.patients.first { $0.id == sessionStore.currentPatientId }?.displayName
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: viewModel.isLoading ? .center : .top)
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear {
            viewModel.loadPatients()
        }
        .accessibilityIdentifier("CaregiverMedicationView")
    }
}

// MARK: - Today Tab View (standalone tab for today's schedule)

struct CaregiverTodayTabView: View {
    private let sessionStore: SessionStore
    private let patientName: String?
    private let onOpenPatients: () -> Void
    private let onCreatePatient: () -> Void
    @StateObject private var viewModel: CaregiverMedicationViewModel

    init(
        sessionStore: SessionStore,
        patientName: String?,
        onOpenPatients: @escaping () -> Void,
        onCreatePatient: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.patientName = patientName
        self.onOpenPatients = onOpenPatients
        self.onCreatePatient = onCreatePatient
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: CaregiverMedicationViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else if let errorMessage = viewModel.errorMessage {
                    CaregiverDataUnavailableView(message: errorMessage) {
                        viewModel.loadPatients()
                    }
                } else if viewModel.patients.isEmpty {
                    CaregiverNoPatientEmptyStateView(onCreatePatient: onCreatePatient)
                } else if sessionStore.currentPatientId == nil {
                    CaregiverPatientSelectionRequiredView(
                        systemImage: "calendar.badge.questionmark",
                        onOpenPatients: onOpenPatients
                    )
                } else {
                    CaregiverTodayView(
                        sessionStore: sessionStore,
                        onOpenPatients: onOpenPatients,
                        patientName: patientName
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: viewModel.isLoading ? .center : .top)
            .background(CaregiverUI.background.ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear {
            viewModel.loadPatients()
        }
        .accessibilityIdentifier("CaregiverTodayTabView")
    }
}
