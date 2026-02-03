import SwiftUI

struct MedicationListItem: Identifiable {
    let medication: MedicationDTO
    let name: String
    let startDateText: String
    let nextScheduledText: String?

    var id: String { medication.id }
}

@MainActor
final class MedicationListViewModel: ObservableObject {
    @Published var items: [MedicationListItem] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore
    private let dateFormatter: DateFormatter
    private let dateTimeFormatter: DateFormatter

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.dateFormatter = DateFormatter()
        self.dateFormatter.locale = Locale(identifier: "ja_JP")
        self.dateFormatter.dateStyle = .medium
        self.dateFormatter.timeStyle = .none
        self.dateTimeFormatter = DateFormatter()
        self.dateTimeFormatter.locale = Locale(identifier: "ja_JP")
        self.dateTimeFormatter.dateStyle = .medium
        self.dateTimeFormatter.timeStyle = .short
    }

    convenience init() {
        let sessionStore = SessionStore()
        let baseURL = SessionStore.resolveBaseURL()
        self.init(apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore), sessionStore: sessionStore)
    }

    func load() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                let patientId: String?
                if sessionStore.mode == .caregiver {
                    guard let selectedPatientId = currentPatientId() else {
                        items = []
                        errorMessage = nil
                        return
                    }
                    patientId = selectedPatientId
                } else {
                    patientId = nil
                }
                let medications = try await apiClient.fetchMedications(patientId: patientId)
                items = medications.map { medication in
                    MedicationListItem(
                        medication: medication,
                        name: medication.name,
                        startDateText: dateFormatter.string(from: medication.startDate),
                        nextScheduledText: medication.nextScheduledAt.map { dateTimeFormatter.string(from: $0) }
                    )
                }
            } catch {
                items = []
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    private func currentPatientId() -> String? {
        switch sessionStore.mode {
        case .caregiver:
            return sessionStore.currentPatientId
        case .patient:
            return nil
        case .none:
            return nil
        }
    }
}

struct MedicationListView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: (() -> Void)?
    private let headerView: AnyView?
    @StateObject private var viewModel: MedicationListViewModel
    @State private var showingCreate = false
    @State private var selectedMedication: MedicationDTO?
    @State private var toastMessage: String?

    init(
        sessionStore: SessionStore? = nil,
        onOpenPatients: (() -> Void)? = nil,
        headerView: AnyView? = nil
    ) {
        let store = sessionStore ?? SessionStore()
        self.sessionStore = store
        self.onOpenPatients = onOpenPatients
        self.headerView = headerView
        let baseURL = SessionStore.resolveBaseURL()
        _viewModel = StateObject(
            wrappedValue: MedicationListViewModel(
                apiClient: APIClient(baseURL: baseURL, sessionStore: store),
                sessionStore: store
            )
        )
    }

    var body: some View {
        Group {
            if viewModel.isLoading {
                LoadingStateView(message: NSLocalizedString("common.loading", comment: "Loading"))
            } else if let errorMessage = viewModel.errorMessage {
                ErrorStateView(message: errorMessage)
            } else if viewModel.items.isEmpty {
                ZStack {
                    RoundedRectangle(cornerRadius: 0)
                        .fill(Color(.secondarySystemBackground))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    VStack {
                        Spacer(minLength: 0)
                        VStack(spacing: 12) {
                            EmptyStateView(
                                title: NSLocalizedString("medication.list.empty.title", comment: "Empty list title"),
                                message: NSLocalizedString("medication.list.empty.message", comment: "Empty list message")
                            )
                            if sessionStore.mode == .caregiver {
                                Button(NSLocalizedString("medication.list.empty.action", comment: "Add medication action")) {
                                    showingCreate = true
                                }
                                .buttonStyle(.borderedProminent)
                            }
                        }
                        .padding(.horizontal, 24)
                        Spacer(minLength: 0)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                let baseList = List {
                    if let headerView {
                        headerView
                            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                    }
                    Section {
                        ForEach(viewModel.items) { item in
                            let rowContent = HStack(alignment: .top, spacing: 12) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.name)
                                        .font(.title3.weight(.semibold))
                                        .accessibilityLabel("薬名 \(item.name)")
                                    Text("\(NSLocalizedString("medication.list.startDate", comment: "Start date")): \(item.startDateText)")
                                        .font(.body)
                                        .accessibilityLabel("開始日 \(item.startDateText)")
                                    if let next = item.nextScheduledText {
                                        Text("\(NSLocalizedString("medication.list.nextDose", comment: "Next dose")): \(next)")
                                            .font(.body)
                                            .accessibilityLabel("次回予定 \(next)")
                                    }
                                }
                                Spacer()
                                if sessionStore.mode == .caregiver {
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.secondary)
                                        .padding(.top, 2)
                                }
                            }
                            if sessionStore.mode == .caregiver {
                                Button(action: { selectedMedication = item.medication }) {
                                    rowContent
                                        .padding(16)
                                        .frame(maxWidth: .infinity)
                                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                                }
                                .buttonStyle(.plain)
                                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                .listRowSeparator(.hidden)
                            } else {
                                rowContent
                                    .padding(16)
                                    .frame(maxWidth: .infinity)
                                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                    .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                    .listRowSeparator(.hidden)
                            }
                        }
                    } header: {
                        Text(NSLocalizedString("medication.list.section.title", comment: "Medication list section"))
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .textCase(nil)
                    }
                    .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(Color.white)
                .safeAreaPadding(.bottom, 120)
                let listWithInsets = headerView == nil ? AnyView(baseList.safeAreaPadding(.top)) : AnyView(baseList)
                listWithInsets
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
            }
        }
        .onAppear {
            viewModel.load()
        }
        .toolbar {
            if sessionStore.mode == .caregiver {
                Button(NSLocalizedString("medication.list.add", comment: "Add medication")) {
                    showingCreate = true
                }
            }
        }
        .sheet(isPresented: $showingCreate) {
            MedicationFormView(sessionStore: sessionStore, onSuccess: showToast)
                .environmentObject(sessionStore)
        }
        .sheet(item: $selectedMedication) { medication in
            MedicationFormView(sessionStore: sessionStore, medication: medication, onSuccess: showToast)
                .environmentObject(sessionStore)
        }
        .onChange(of: showingCreate) { _, isPresented in
            if !isPresented {
                viewModel.load()
            }
        }
        .onChange(of: selectedMedication?.id) { _, medicationId in
            if medicationId == nil {
                viewModel.load()
            }
        }
        .sensoryFeedback(.success, trigger: toastMessage)
        .accessibilityIdentifier("MedicationListView")
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task {
            try? await Task.sleep(for: .seconds(2))
            await MainActor.run {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
    }
}
