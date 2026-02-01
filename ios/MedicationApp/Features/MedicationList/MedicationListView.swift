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
                    errorMessage = "患者IDが未設定です"
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
                errorMessage = "取得に失敗しました"
            }
        }
    }

    private func currentPatientId() -> String? {
        switch sessionStore.mode {
        case .caregiver:
            return sessionStore.caregiverToken
        case .patient:
            return sessionStore.patientToken
        case .none:
            return nil
        }
    }
}

struct MedicationListView: View {
    @StateObject private var viewModel = MedicationListViewModel()

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView()
            } else if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
            } else if viewModel.items.isEmpty {
                Text("薬がありません")
            } else {
                List(viewModel.items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.name)
                            .font(.headline)
                        Text("開始日: \(item.startDateText)")
                            .font(.subheadline)
                        if let next = item.nextScheduledText {
                            Text("次回予定: \(next)")
                                .font(.subheadline)
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
