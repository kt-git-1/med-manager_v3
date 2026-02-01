import SwiftUI

struct MedicationListItem: Identifiable {
    let id: String
    let name: String
    let startDateText: String
    let nextScheduledText: String?
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
        let baseURL = URL(string: ProcessInfo.processInfo.environment["API_BASE_URL"] ?? "http://localhost:3000")!
        self.init(apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore), sessionStore: sessionStore)
    }

    func load() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                guard let patientId = currentPatientId() else {
                    items = []
                    errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
                    return
                }
                let medications = try await apiClient.fetchMedications(patientId: patientId)
                items = medications.map { medication in
                    MedicationListItem(
                        id: medication.id,
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
            return sessionStore.caregiverToken
        case .patient:
            return nil
        case .none:
            return nil
        }
    }
}

struct MedicationListView: View {
    @StateObject private var viewModel: MedicationListViewModel

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        let baseURL = URL(string: ProcessInfo.processInfo.environment["API_BASE_URL"] ?? "http://localhost:3000")!
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
                EmptyStateView(
                    title: NSLocalizedString("medication.list.empty.title", comment: "Empty list title"),
                    message: NSLocalizedString("medication.list.empty.message", comment: "Empty list message")
                )
            } else {
                List(viewModel.items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.name)
                            .font(.headline)
                            .accessibilityLabel("薬名 \(item.name)")
                        Text("\(NSLocalizedString("medication.list.startDate", comment: "Start date")): \(item.startDateText)")
                            .font(.subheadline)
                            .accessibilityLabel("開始日 \(item.startDateText)")
                        if let next = item.nextScheduledText {
                            Text("\(NSLocalizedString("medication.list.nextDose", comment: "Next dose")): \(next)")
                                .font(.subheadline)
                                .accessibilityLabel("次回予定 \(next)")
                        }
                    }
                }
            }
        }
        .onAppear {
            viewModel.load()
        }
        .accessibilityIdentifier("MedicationListView")
    }
}
