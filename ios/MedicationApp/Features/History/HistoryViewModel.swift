import Foundation
import SwiftUI

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
    private var activeRequests = 0
    private var pendingMonthRequest: (year: Int, month: Int)?
    private var pendingDayRequest: String?
    var toastPresenter: ToastPresenter?

    init(
        apiClient: APIClient,
        sessionStore: SessionStore
    ) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
    }

    func loadMonth(year: Int, month: Int) {
        guard !isLoadingMonth else {
            pendingMonthRequest = (year, month)
            return
        }
        monthErrorMessage = nil
        let shouldShowUpdatingOverlay = self.month != nil
        if shouldShowUpdatingOverlay {
            startRequest()
        }
        isLoadingMonth = true
        Task {
            defer {
                if shouldShowUpdatingOverlay {
                    endRequest()
                }
                isLoadingMonth = false
                if let pendingRequest = pendingMonthRequest {
                    pendingMonthRequest = nil
                    loadMonth(year: pendingRequest.year, month: pendingRequest.month)
                }
            }
            do {
                switch sessionStore.mode {
                case .patient:
                    self.month = try await apiClient.fetchPatientHistoryMonth(year: year, month: month, slotTimeItems: [])
                case .caregiver:
                    self.month = try await apiClient.fetchCaregiverHistoryMonth(year: year, month: month, slotTimeItems: [])
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
                    self.monthErrorMessage = dataUnavailableMessage()
                }
            } catch {
                self.monthErrorMessage = dataUnavailableMessage()
            }
        }
    }

    func loadDay(date: String) {
        guard !isLoadingDay else {
            pendingDayRequest = date
            return
        }
        dayErrorMessage = nil
        let shouldShowUpdatingOverlay = self.day != nil
        if shouldShowUpdatingOverlay {
            startRequest()
        }
        isLoadingDay = true
        Task {
            defer {
                if shouldShowUpdatingOverlay {
                    endRequest()
                }
                isLoadingDay = false
                if let pendingRequest = pendingDayRequest {
                    pendingDayRequest = nil
                    loadDay(date: pendingRequest)
                }
            }
            do {
                switch sessionStore.mode {
                case .patient:
                    self.day = try await apiClient.fetchPatientHistoryDay(date: date, slotTimeItems: [])
                case .caregiver:
                    self.day = try await apiClient.fetchCaregiverHistoryDay(date: date, slotTimeItems: [])
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
                    self.dayErrorMessage = dataUnavailableMessage()
                }
            } catch {
                self.dayErrorMessage = dataUnavailableMessage()
            }
        }
    }

    func recordMissedCaregiverDose(
        _ dose: HistoryDayItemDTO,
        date: String,
        year: Int,
        month: Int
    ) {
        guard sessionStore.mode == .caregiver, dose.effectiveStatus == .missed else { return }
        startRequest()
        Task {
            defer {
                endRequest()
            }
            do {
                _ = try await apiClient.createCaregiverDoseRecord(
                    input: DoseRecordCreateRequestDTO(
                        medicationId: dose.medicationId,
                        scheduledAt: dose.scheduledAt
                    )
                )
                NotificationCenter.default.post(name: .doseRecordsUpdated, object: nil)
                showToast(NSLocalizedString("history.day.backfill.recorded", comment: "Backfill recorded"))
                loadMonth(year: year, month: month)
                loadDay(date: date)
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"), kind: .error)
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

    private func showToast(_ message: String, kind: ToastKind = .success) {
        toastPresenter?.show(message, kind: kind)
    }

    private func dataUnavailableMessage() -> String {
        if sessionStore.mode == .caregiver {
            return NSLocalizedString(
                "caregiver.dataUnavailable.message",
                comment: "Caregiver data unavailable message"
            )
        }
        return NSLocalizedString(
            "history.error.retry",
            comment: "History load failed with retry"
        )
    }
}
