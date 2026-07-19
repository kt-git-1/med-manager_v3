import Foundation
import SwiftUI

@MainActor
final class CaregiverTodayViewModel: ObservableObject {
    @Published var items: [ScheduleDoseDTO] = []
    @Published var prnMedications: [MedicationDTO] = []
    @Published var isLoading = false
    @Published var isUpdating = false
    @Published var errorMessage: String?
    @Published var outOfStockMedicationIds: Set<String> = []
    @Published private(set) var scrollToTopRequest = 0

    private let apiClient: APIClient
    private let dateFormatter: DateFormatter
    private let timeFormatter: DateFormatter
    private let dateKeyFormatter: DateFormatter
    private let calendar: Calendar
    private let onLowStockChange: (Bool) -> Void
    var toastPresenter: ToastPresenter?

    init(apiClient: APIClient, onLowStockChange: @escaping (Bool) -> Void = { _ in }) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        self.apiClient = apiClient
        self.onLowStockChange = onLowStockChange
        self.dateFormatter = DateFormatter()
        self.dateFormatter.locale = AppConstants.japaneseLocale
        self.dateFormatter.dateStyle = .medium
        self.dateFormatter.timeStyle = .none
        self.timeFormatter = DateFormatter()
        self.timeFormatter.locale = AppConstants.japaneseLocale
        self.timeFormatter.dateStyle = .none
        self.timeFormatter.timeStyle = .short
        self.dateKeyFormatter = DateFormatter()
        self.dateKeyFormatter.locale = Locale(identifier: "en_US_POSIX")
        self.dateKeyFormatter.calendar = calendar
        self.dateKeyFormatter.timeZone = AppConstants.defaultTimeZone
        self.dateKeyFormatter.dateFormat = "yyyy-MM-dd"
        self.calendar = calendar
    }

    func load(showLoading: Bool) {
        guard !isLoading else { return }
        isLoading = showLoading
        isUpdating = !showLoading
        errorMessage = nil
        Task {
            defer {
                isLoading = false
                isUpdating = false
            }
            do {
                try await refreshData()
            } catch {
                items = []
                prnMedications = []
                outOfStockMedicationIds = []
                errorMessage = NSLocalizedString("caregiver.dataUnavailable.message", comment: "Caregiver data unavailable message")
            }
        }
    }

    func isMedicationOutOfStock(_ medicationId: String) -> Bool {
        outOfStockMedicationIds.contains(medicationId)
    }

    func reset() {
        items = []
        prnMedications = []
        outOfStockMedicationIds = []
        isLoading = false
        isUpdating = false
        errorMessage = nil
    }

    func recordDose(_ dose: ScheduleDoseDTO) {
        guard dose.effectiveStatus != .taken else { return }
        isUpdating = true
        Task {
            defer { isUpdating = false }
            do {
                _ = try await apiClient.createCaregiverDoseRecord(
                    input: DoseRecordCreateRequestDTO(
                        medicationId: dose.medicationId,
                        scheduledAt: dose.scheduledAt
                    )
                )
                markDosesRecorded([dose])
                requestScrollToTop()
                notifyDoseRecordsUpdated()
                showToast(NSLocalizedString("caregiver.today.recorded", comment: "Recorded"))
                refreshAfterMutationInBackground()
            } catch {
                showToastMessage(for: error)
            }
        }
    }

    func recordDoses(_ doses: [ScheduleDoseDTO], slot: NotificationSlot) {
        let recordableDoses = doses.filter { $0.effectiveStatus != .taken }
        guard !recordableDoses.isEmpty else { return }
        isUpdating = true
        Task {
            defer { isUpdating = false }
            do {
                let result = try await apiClient.bulkRecordCaregiverSlot(
                    date: dateKeyFormatter.string(from: recordableDoses[0].scheduledAt),
                    slot: slot.rawValue
                )
                if result.updatedCount > 0 {
                    notifyDoseRecordsUpdated()
                }
                if result.insufficientCount > 0 {
                    showToast(
                        NSLocalizedString("patient.today.slot.bulk.partialSuccess", comment: "Bulk partially recorded"),
                        kind: .warning
                    )
                } else {
                    let format = NSLocalizedString("caregiver.today.recorded.bulk", comment: "Bulk recorded")
                    showToast(String(format: format, result.updatedCount))
                }
                if result.insufficientCount == 0 && result.updatedCount == recordableDoses.count {
                    markDosesRecorded(recordableDoses)
                    refreshAfterMutationInBackground()
                } else {
                    // The response does not identify which dose failed inventory validation.
                    // Wait for the authoritative schedule only for this uncommon partial-success case.
                    await refreshAfterMutation()
                }
                if result.updatedCount > 0 {
                    requestScrollToTop()
                }
            } catch {
                showToastMessage(for: error)
            }
        }
    }

    func recordPrnDose(for medication: MedicationDTO, onSuccess: @escaping () -> Void) {
        guard !isUpdating, !medication.isInsufficientForDose else { return }
        isUpdating = true
        Task {
            defer { isUpdating = false }
            do {
                _ = try await apiClient.createPrnDoseRecord(
                    patientId: medication.patientId,
                    input: PrnDoseRecordCreateRequestDTO(
                        medicationId: medication.id,
                        takenAt: nil,
                        quantityTaken: nil
                    )
                )
                notifyDoseRecordsUpdated()
                showToast(NSLocalizedString("caregiver.today.prn.recorded", comment: "Caregiver PRN recorded"))
                onSuccess()
                refreshAfterMutationInBackground()
            } catch {
                showToastMessage(for: error)
            }
        }
    }

    func deleteDose(_ dose: ScheduleDoseDTO) {
        guard dose.effectiveStatus == .taken else { return }
        isUpdating = true
        Task {
            defer { isUpdating = false }
            do {
                try await apiClient.deleteCaregiverDoseRecord(
                    medicationId: dose.medicationId,
                    scheduledAt: dose.scheduledAt
                )
                markDoseDeleted(dose)
                notifyDoseRecordsUpdated()
                showToast(NSLocalizedString("caregiver.today.deleted", comment: "Deleted"))
                refreshAfterMutationInBackground()
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"), kind: .error)
            }
        }
    }

    func timeText(for date: Date) -> String {
        timeFormatter.string(from: date)
    }

    func dateText(for date: Date) -> String {
        dateFormatter.string(from: date)
    }

    private func showToast(_ message: String, kind: ToastKind = .success) {
        toastPresenter?.show(message, kind: kind)
    }

    private func notifyDoseRecordsUpdated() {
        NotificationCenter.default.post(name: .doseRecordsUpdated, object: nil)
    }

    private func requestScrollToTop() {
        scrollToTopRequest += 1
    }

    private func showToastMessage(for error: Error) {
        if let apiError = error as? APIError, case .insufficientInventory = apiError {
            showToast(NSLocalizedString("patient.today.inventory.insufficient", comment: "Insufficient inventory"), kind: .warning)
            load(showLoading: false)
        } else {
            showToast(NSLocalizedString("common.error.generic", comment: "Generic error"), kind: .error)
        }
    }

    private func refreshAfterMutation() async {
        do {
            try await refreshData()
        } catch {
            // The mutation already succeeded. Preserve the currently rendered data instead of
            // replacing the whole screen with an empty/error state on a transient refresh failure.
            showToast(NSLocalizedString("common.error.generic", comment: "Generic error"), kind: .error)
        }
    }

    private func refreshAfterMutationInBackground() {
        Task { @MainActor [weak self] in
            await self?.refreshAfterMutation()
        }
    }

    private func markDosesRecorded(_ doses: [ScheduleDoseDTO]) {
        let recordedKeys = Set(doses.map(\.key))
        items = items.map { dose in
            guard recordedKeys.contains(dose.key) else { return dose }
            return replacingStatus(of: dose, with: .taken, recordedByType: .caregiver)
        }.sorted(by: sortDose)
    }

    private func markDoseDeleted(_ deletedDose: ScheduleDoseDTO) {
        let restoredStatus: DoseStatusDTO = Date() > deletedDose.scheduledAt.addingTimeInterval(60 * 60)
            ? .missed
            : .pending
        items = items.map { dose in
            guard dose.key == deletedDose.key else { return dose }
            return replacingStatus(of: dose, with: restoredStatus, recordedByType: nil)
        }.sorted(by: sortDose)
    }

    private func replacingStatus(
        of dose: ScheduleDoseDTO,
        with status: DoseStatusDTO,
        recordedByType: RecordedByTypeDTO?
    ) -> ScheduleDoseDTO {
        ScheduleDoseDTO(
            key: dose.key,
            patientId: dose.patientId,
            medicationId: dose.medicationId,
            scheduledAt: dose.scheduledAt,
            effectiveStatus: status,
            recordedByType: recordedByType,
            medicationSnapshot: dose.medicationSnapshot
        )
    }

    private func refreshData() async throws {
        async let dosesTask = apiClient.fetchCaregiverToday(slotTimeItems: [])
        async let medicationsTask = apiClient.fetchMedications(patientId: nil)
        async let inventoryTask = apiClient.fetchInventory()
        let (doses, medications, inventory) = try await (dosesTask, medicationsTask, inventoryTask)

        items = doses.sorted(by: sortDose)
        prnMedications = medications
            .filter { $0.isPrn && $0.isActive && !$0.isArchived }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        outOfStockMedicationIds = Set(
            inventory.filter { $0.isInsufficientForDose }.map { $0.medicationId }
        )
        onLowStockChange(
            inventory.contains { $0.inventoryEnabled && ($0.low || $0.out) }
        )
    }

    private func sortDose(_ lhs: ScheduleDoseDTO, _ rhs: ScheduleDoseDTO) -> Bool {
        let leftRank = statusRank(lhs.effectiveStatus)
        let rightRank = statusRank(rhs.effectiveStatus)
        if leftRank != rightRank {
            return leftRank < rightRank
        }
        return lhs.scheduledAt < rhs.scheduledAt
    }

    private func statusRank(_ status: DoseStatusDTO?) -> Int {
        switch status {
        case .taken:
            return 2
        case .missed:
            return 1
        case .pending, .none:
            return 0
        }
    }
}
