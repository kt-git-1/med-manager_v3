import Foundation
import SwiftUI

@MainActor
final class CaregiverTodayViewModel: ObservableObject {
    @Published var items: [ScheduleDoseDTO] = []
    @Published var isLoading = false
    @Published var isUpdating = false
    @Published var errorMessage: String?
    @Published var toastMessage: String?
    @Published var outOfStockMedicationIds: Set<String> = []

    private let apiClient: APIClient
    private let dateFormatter: DateFormatter
    private let timeFormatter: DateFormatter
    private let calendar: Calendar

    init(apiClient: APIClient) {
        self.apiClient = apiClient
        self.dateFormatter = DateFormatter()
        self.dateFormatter.locale = Locale(identifier: "ja_JP")
        self.dateFormatter.dateStyle = .medium
        self.dateFormatter.timeStyle = .none
        self.timeFormatter = DateFormatter()
        self.timeFormatter.locale = Locale(identifier: "ja_JP")
        self.timeFormatter.dateStyle = .none
        self.timeFormatter.timeStyle = .short
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo") ?? .current
        self.calendar = calendar
    }

    func load(showLoading: Bool) {
        guard !isLoading else { return }
        isLoading = showLoading
        isUpdating = true
        errorMessage = nil
        Task {
            defer {
                isLoading = false
                isUpdating = false
            }
            do {
                async let dosesTask = apiClient.fetchCaregiverToday()
                async let inventoryTask = apiClient.fetchInventory()
                let (doses, inventory) = try await (dosesTask, inventoryTask)
                items = doses.sorted(by: sortDose)
                outOfStockMedicationIds = Set(
                    inventory.filter { $0.inventoryEnabled && $0.out }.map { $0.medicationId }
                )
            } catch {
                items = []
                outOfStockMedicationIds = []
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    func isMedicationOutOfStock(_ medicationId: String) -> Bool {
        outOfStockMedicationIds.contains(medicationId)
    }

    func reset() {
        items = []
        outOfStockMedicationIds = []
        isLoading = false
        isUpdating = false
        errorMessage = nil
        toastMessage = nil
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
                showToast(NSLocalizedString("caregiver.today.recorded", comment: "Recorded"))
                load(showLoading: false)
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
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
                showToast(NSLocalizedString("caregiver.today.deleted", comment: "Deleted"))
                load(showLoading: false)
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
            }
        }
    }

    func timeText(for date: Date) -> String {
        timeFormatter.string(from: date)
    }

    func dateText(for date: Date) -> String {
        dateFormatter.string(from: date)
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            await MainActor.run {
                withAnimation {
                    self?.toastMessage = nil
                }
            }
        }
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
