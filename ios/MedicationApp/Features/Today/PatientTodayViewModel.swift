import Foundation
import SwiftUI
import UIKit

@MainActor
final class PatientTodayViewModel: ObservableObject {
    @Published var items: [ScheduleDoseDTO] = []
    @Published var isLoading = false
    @Published var isUpdating = false
    @Published var errorMessage: String?
    @Published var toastMessage: String?
    @Published var confirmDose: ScheduleDoseDTO?
    @Published var prnMedications: [MedicationDTO] = []
    @Published var confirmPrnMedication: MedicationDTO?
    @Published var isPrnSubmitting = false
    @Published var highlightedSlot: NotificationSlot?
    @Published var outOfStockMedicationIds: Set<String> = []
    @Published var confirmSlot: NotificationSlot?

    private let apiClient: APIClient
    let preferencesStore: NotificationPreferencesStore
    private let dateFormatter: DateFormatter
    private let timeFormatter: DateFormatter
    private let dateKeyFormatter: DateFormatter
    private let calendar: Calendar
    private var foregroundTask: Task<Void, Never>?
    private var medicationCache: [String: MedicationDTO] = [:]

    init(
        apiClient: APIClient,
        preferencesStore: NotificationPreferencesStore = NotificationPreferencesStore()
    ) {
        self.apiClient = apiClient
        self.preferencesStore = preferencesStore
        self.dateFormatter = DateFormatter()
        self.dateFormatter.locale = AppConstants.japaneseLocale
        self.dateFormatter.dateStyle = .medium
        self.dateFormatter.timeStyle = .none
        self.timeFormatter = DateFormatter()
        self.timeFormatter.locale = AppConstants.japaneseLocale
        self.timeFormatter.dateStyle = .none
        self.timeFormatter.timeStyle = .short
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppConstants.defaultTimeZone
        self.calendar = calendar
        let dateKeyFormatter = DateFormatter()
        dateKeyFormatter.calendar = calendar
        dateKeyFormatter.timeZone = calendar.timeZone
        dateKeyFormatter.locale = AppConstants.posixLocale
        dateKeyFormatter.dateFormat = "yyyy-MM-dd"
        self.dateKeyFormatter = dateKeyFormatter
    }

    deinit {
        foregroundTask?.cancel()
    }

    func handleAppear() {
        startForegroundRefresh()
        load(showLoading: true)
    }

    func handleDisappear() {
        foregroundTask?.cancel()
        foregroundTask = nil
    }

    func load(showLoading: Bool) {
        guard !isLoading else { return }
        isLoading = showLoading
        isUpdating = true
        errorMessage = nil
        Task { @MainActor in
            defer {
                isLoading = false
                isUpdating = false
            }
            do {
                try await refreshTodayData()
            } catch {
                items = []
                prnMedications = []
                errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            }
        }
    }

    func confirmRecord(for dose: ScheduleDoseDTO) {
        if dose.effectiveStatus == .taken {
            showToast(NSLocalizedString("patient.today.alreadyRecorded", comment: "Already recorded"))
            return
        }
        confirmDose = dose
    }

    func recordConfirmedDose() {
        guard let dose = confirmDose else { return }
        confirmDose = nil
        isUpdating = true
        Task { @MainActor in
            defer { isUpdating = false }
            do {
                _ = try await apiClient.createPatientDoseRecord(
                    input: DoseRecordCreateRequestDTO(
                        medicationId: dose.medicationId,
                        scheduledAt: dose.scheduledAt
                    )
                )
                showToast(NSLocalizedString("patient.today.recorded", comment: "Recorded"))
                load(showLoading: false)
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
            }
        }
    }

    func confirmPrnRecord(for medication: MedicationDTO) {
        confirmPrnMedication = medication
    }

    func recordConfirmedPrnDose() {
        guard let medication = confirmPrnMedication else { return }
        confirmPrnMedication = nil
        recordPrnDose(for: medication, onSuccess: {})
    }

    func recordPrnDose(for medication: MedicationDTO, onSuccess: @escaping () -> Void) {
        guard !isPrnSubmitting else { return }
        isUpdating = true
        isPrnSubmitting = true
        Task { @MainActor in
            defer {
                isUpdating = false
                isPrnSubmitting = false
            }
            do {
                _ = try await apiClient.createPrnDoseRecord(
                    patientId: medication.patientId,
                    input: PrnDoseRecordCreateRequestDTO(
                        medicationId: medication.id,
                        takenAt: nil,
                        quantityTaken: nil
                    )
                )
                showToast(NSLocalizedString("patient.today.prn.recorded", comment: "PRN recorded"))
                try await refreshTodayData()
                onSuccess()
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

    func handleDeepLink(_ target: NotificationDeepLinkTarget) -> String? {
        guard let dose = items.first(where: { dose in
            guard let slot = NotificationSlot.from(date: dose.scheduledAt, timeZone: calendar.timeZone, slotTimes: preferencesStore.slotTimesMap()) else {
                return false
            }
            return slot == target.slot && dateKey(for: dose.scheduledAt) == target.dateKey
        }) else {
            showToast(NSLocalizedString("patient.today.alreadyRecorded", comment: "Already recorded"))
            return nil
        }

        if dose.effectiveStatus != .pending {
            showToast(NSLocalizedString("patient.today.alreadyRecorded", comment: "Already recorded"))
        } else {
            triggerHighlight(for: target.slot)
        }

        return dose.key
    }

    func fetchMedicationDetail(medicationId: String) async throws -> MedicationDTO? {
        if let cached = medicationCache[medicationId] {
            return cached
        }
        let medications = try await apiClient.fetchMedications(patientId: nil)
        var matched: MedicationDTO?
        for medication in medications {
            medicationCache[medication.id] = medication
            if medication.id == medicationId {
                matched = medication
            }
        }
        return matched
    }

    func isMedicationOutOfStock(_ medicationId: String) -> Bool {
        outOfStockMedicationIds.contains(medicationId)
    }

    // MARK: - Slot Bulk Recording

    /// Recording window: slot time −30 min to +60 min
    private static let recordingWindowBeforeSeconds: TimeInterval = 30 * 60
    private static let recordingWindowAfterSeconds: TimeInterval = 60 * 60

    struct SlotSummary {
        let totalPills: Double
        let medCount: Int
        let remainingCount: Int
        let slotTime: String
        let aggregateStatus: DoseStatusDTO
        let isWithinRecordingWindow: Bool
    }

    var slotSummaries: [NotificationSlot: SlotSummary] {
        let slotTimesMap = preferencesStore.slotTimesMap()
        var grouped: [NotificationSlot: [ScheduleDoseDTO]] = [:]
        for dose in items {
            guard let slot = NotificationSlot.from(date: dose.scheduledAt, slotTimes: slotTimesMap) else {
                continue
            }
            grouped[slot, default: []].append(dose)
        }

        let timeFormat = DateFormatter()
        timeFormat.timeZone = AppConstants.defaultTimeZone
        timeFormat.dateFormat = "HH:mm"

        var result: [NotificationSlot: SlotSummary] = [:]
        for (slot, doses) in grouped {
            let totalPills = doses.reduce(0.0) { $0 + $1.medicationSnapshot.doseCountPerIntake }
            let medCount = doses.count
            let remaining = doses.filter { $0.effectiveStatus == .pending || $0.effectiveStatus == .missed }.count
            let slotTime = doses.first.map { timeFormat.string(from: $0.scheduledAt) } ?? "00:00"

            // Worst-case aggregate: missed > pending > taken
            let aggregate: DoseStatusDTO
            if doses.contains(where: { $0.effectiveStatus == .missed }) {
                aggregate = .missed
            } else if doses.contains(where: { $0.effectiveStatus == .pending || $0.effectiveStatus == nil }) {
                aggregate = .pending
            } else {
                aggregate = .taken
            }

            // Recording window: scheduledAt −30 min … scheduledAt +60 min
            let now = Date()
            let withinWindow: Bool
            if let firstScheduled = doses.first?.scheduledAt {
                let windowOpen = firstScheduled.addingTimeInterval(-Self.recordingWindowBeforeSeconds)
                let windowClose = firstScheduled.addingTimeInterval(Self.recordingWindowAfterSeconds)
                withinWindow = now >= windowOpen && now <= windowClose
            } else {
                withinWindow = false
            }

            result[slot] = SlotSummary(
                totalPills: totalPills,
                medCount: medCount,
                remainingCount: remaining,
                slotTime: slotTime,
                aggregateStatus: aggregate,
                isWithinRecordingWindow: withinWindow
            )
        }
        return result
    }

    func confirmBulkRecord(for slot: NotificationSlot) {
        confirmSlot = slot
    }

    func executeBulkRecord() {
        guard let slot = confirmSlot else { return }
        confirmSlot = nil
        isUpdating = true
        Task { @MainActor in
            defer { isUpdating = false }
            do {
                let dateString = dateKeyFormatter.string(from: Date())
                let slotTimes = preferencesStore.slotTimeQueryItems()
                _ = try await apiClient.bulkRecordSlot(
                    date: dateString,
                    slot: slot.rawValue,
                    slotTimes: slotTimes
                )
                showToast(NSLocalizedString("patient.today.slot.bulk.success", comment: "Bulk recorded"))
                load(showLoading: false)
            } catch {
                showToast(NSLocalizedString("common.error.generic", comment: "Generic error"))
            }
        }
    }

    private func refreshTodayData() async throws {
        async let dosesTask = apiClient.fetchPatientToday()
        async let medicationsTask = apiClient.fetchMedications(patientId: nil)
        let (doses, medications) = try await (dosesTask, medicationsTask)
        items = doses.sorted(by: sortDose)
        prnMedications = medications.filter { $0.isPrn }
        outOfStockMedicationIds = Set(medications.filter { $0.isOutOfStock }.map { $0.id })
        for medication in medications {
            medicationCache[medication.id] = medication
        }
    }

    private func showToast(_ message: String) {
        withAnimation {
            toastMessage = message
        }
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(AppConstants.toastDuration))
            await MainActor.run {
                withAnimation {
                    self?.toastMessage = nil
                }
            }
        }
    }

    private func triggerHighlight(for slot: NotificationSlot) {
        highlightedSlot = slot
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(AppConstants.slotHighlightDuration))
            await MainActor.run {
                if self?.highlightedSlot == slot {
                    self?.highlightedSlot = nil
                }
            }
        }
    }

    private func dateKey(for date: Date) -> String {
        dateKeyFormatter.string(from: date)
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

    private func startForegroundRefresh() {
        guard foregroundTask == nil else { return }
        foregroundTask = Task { [weak self] in
            for await _ in NotificationCenter.default.notifications(
                named: UIApplication.willEnterForegroundNotification
            ) {
                await MainActor.run {
                    self?.load(showLoading: false)
                }
            }
        }
    }
}
