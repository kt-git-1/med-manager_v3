import Foundation

@MainActor
final class HistoryViewModel: ObservableObject {
    @Published var month: HistoryMonthResponseDTO?
    @Published var day: HistoryDayResponseDTO?
    @Published var isLoadingMonth = false
    @Published var isLoadingDay = false
    @Published var isUpdating = false
    @Published var monthErrorMessage: String?
    @Published var dayErrorMessage: String?

    private let apiClient: APIClient
    private let sessionStore: SessionStore
    private var activeRequests = 0

    init(apiClient: APIClient, sessionStore: SessionStore) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func loadMonth(year: Int, month: Int) {
        guard !isLoadingMonth else { return }
        monthErrorMessage = nil
        startRequest()
        isLoadingMonth = true
        Task {
            defer {
                endRequest()
                isLoadingMonth = false
            }
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
                self.monthErrorMessage = NSLocalizedString(
                    "history.error.retry",
                    comment: "History load failed with retry"
                )
            }
        }
    }

    func loadDay(date: String) {
        guard !isLoadingDay else { return }
        dayErrorMessage = nil
        startRequest()
        isLoadingDay = true
        Task {
            defer {
                endRequest()
                isLoadingDay = false
            }
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
                self.dayErrorMessage = NSLocalizedString(
                    "history.error.retry",
                    comment: "History load failed with retry"
                )
            }
        }
    }

    private func startRequest() {
        activeRequests += 1
        isUpdating = activeRequests > 0
    }

    private func endRequest() {
        activeRequests = max(0, activeRequests - 1)
        isUpdating = activeRequests > 0
    }
}
