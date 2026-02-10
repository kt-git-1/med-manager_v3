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
    @Published var retentionLocked = false
    @Published var retentionCutoffDate: String?
    @Published var retentionDays: Int?

    private let apiClient: APIClient
    private let sessionStore: SessionStore
    private let preferencesStore: NotificationPreferencesStore
    private var activeRequests = 0

    init(
        apiClient: APIClient,
        sessionStore: SessionStore,
        preferencesStore: NotificationPreferencesStore = NotificationPreferencesStore()
    ) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.preferencesStore = preferencesStore
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
                let slotItems = preferencesStore.slotTimeQueryItems()
                switch sessionStore.mode {
                case .patient:
                    self.month = try await apiClient.fetchPatientHistoryMonth(year: year, month: month, slotTimeItems: slotItems)
                case .caregiver:
                    self.month = try await apiClient.fetchCaregiverHistoryMonth(year: year, month: month, slotTimeItems: slotItems)
                case .none:
                    self.month = nil
                }
                self.retentionLocked = false
                self.retentionCutoffDate = nil
                self.retentionDays = nil
            } catch let error as APIError {
                if case .historyRetentionLimit(let cutoffDate, let retentionDays) = error {
                    self.retentionLocked = true
                    self.retentionCutoffDate = cutoffDate
                    self.retentionDays = retentionDays
                } else {
                    self.monthErrorMessage = NSLocalizedString(
                        "history.error.retry",
                        comment: "History load failed with retry"
                    )
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
                let slotItems = preferencesStore.slotTimeQueryItems()
                switch sessionStore.mode {
                case .patient:
                    self.day = try await apiClient.fetchPatientHistoryDay(date: date, slotTimeItems: slotItems)
                case .caregiver:
                    self.day = try await apiClient.fetchCaregiverHistoryDay(date: date, slotTimeItems: slotItems)
                case .none:
                    self.day = nil
                }
                self.retentionLocked = false
                self.retentionCutoffDate = nil
                self.retentionDays = nil
            } catch let error as APIError {
                if case .historyRetentionLimit(let cutoffDate, let retentionDays) = error {
                    self.retentionLocked = true
                    self.retentionCutoffDate = cutoffDate
                    self.retentionDays = retentionDays
                } else {
                    self.dayErrorMessage = NSLocalizedString(
                        "history.error.retry",
                        comment: "History load failed with retry"
                    )
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
