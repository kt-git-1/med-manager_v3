import Foundation

@MainActor
final class HistoryViewModel: ObservableObject {
    @Published var month: HistoryMonthResponseDTO?
    @Published var day: HistoryDayResponseDTO?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func loadMonth(year: Int, month: Int) {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                switch sessionStore.mode {
                case .patient:
                    self.month = try await apiClient.fetchPatientHistoryMonth(year: year, month: month)
                case .caregiver:
                    self.month = try await apiClient.fetchCaregiverHistoryMonth(year: year, month: month)
                case .none:
                    self.month = nil
                }
            } catch {
                self.errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    func loadDay(date: String) {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        Task {
            defer { isLoading = false }
            do {
                switch sessionStore.mode {
                case .patient:
                    self.day = try await apiClient.fetchPatientHistoryDay(date: date)
                case .caregiver:
                    self.day = try await apiClient.fetchCaregiverHistoryDay(date: date)
                case .none:
                    self.day = nil
                }
            } catch {
                self.errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }
}
