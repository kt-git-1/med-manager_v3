import Foundation

enum ScheduleFrequency: String, CaseIterable, Identifiable {
    case daily
    case weekly

    var id: String { rawValue }
}

enum ScheduleDay: String, CaseIterable, Identifiable {
    case mon = "MON"
    case tue = "TUE"
    case wed = "WED"
    case thu = "THU"
    case fri = "FRI"
    case sat = "SAT"
    case sun = "SUN"

    var id: String { rawValue }

    var shortLabel: String {
        switch self {
        case .mon: return "月"
        case .tue: return "火"
        case .wed: return "水"
        case .thu: return "木"
        case .fri: return "金"
        case .sat: return "土"
        case .sun: return "日"
        }
    }
}

enum ScheduleTimeSlot: String, CaseIterable, Identifiable {
    case morning
    case noon
    case evening
    case bedtime

    var id: String { rawValue }

    var label: String {
        switch self {
        case .morning: return "朝"
        case .noon: return "昼"
        case .evening: return "夜"
        case .bedtime: return "眠前"
        }
    }

    var timeValue: String {
        switch self {
        case .morning: return "08:00"
        case .noon: return "12:00"
        case .evening: return "18:00"
        case .bedtime: return "21:00"
        }
    }

    var notificationSlot: NotificationSlot {
        switch self {
        case .morning:
            return .morning
        case .noon:
            return .noon
        case .evening:
            return .evening
        case .bedtime:
            return .bedtime
        }
    }

    static func slot(for timeValue: String) -> ScheduleTimeSlot? {
        return ScheduleTimeSlot.allCases.first { $0.timeValue == timeValue }
    }
}

@MainActor
final class MedicationFormViewModel: ObservableObject {
    @Published var name = ""
    @Published var dosageStrengthValue = ""
    @Published var dosageStrengthUnit = ""
    @Published var doseCountPerIntake = ""
    @Published var startDate = Date()
    @Published var endDate: Date?
    @Published var notes = ""
    @Published var inventoryCount = ""
    @Published var inventoryUnit = ""
    @Published var errorMessage: String?
    @Published var isSubmitting = false
    @Published var isDeleting = false
    @Published var scheduleFrequency: ScheduleFrequency = .daily
    @Published var selectedDays: Set<ScheduleDay> = []
    @Published var selectedTimeSlots: Set<ScheduleTimeSlot> = []
    @Published var scheduleIsLoading = false
    @Published var scheduleNotSet = false

    private let apiClient: APIClient
    private let sessionStore: SessionStore
    private let preferencesStore: NotificationPreferencesStore
    private let existingMedication: MedicationDTO?
    private var existingRegimenId: String?
    private var didLoadSchedule = false

    var isEditing: Bool {
        existingMedication != nil
    }

    init(
        apiClient: APIClient,
        sessionStore: SessionStore,
        existingMedication: MedicationDTO? = nil,
        preferencesStore: NotificationPreferencesStore = NotificationPreferencesStore()
    ) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.existingMedication = existingMedication
        self.preferencesStore = preferencesStore
        if let existingMedication {
            name = existingMedication.name
            dosageStrengthValue = existingMedication.dosageStrengthValue == 0 ? "" : String(existingMedication.dosageStrengthValue)
            dosageStrengthUnit = existingMedication.dosageStrengthUnit
            doseCountPerIntake = String(existingMedication.doseCountPerIntake)
            startDate = existingMedication.startDate
            endDate = existingMedication.endDate
            notes = existingMedication.notes ?? ""
            inventoryCount = existingMedication.inventoryCount.map(String.init) ?? ""
            inventoryUnit = existingMedication.inventoryUnit ?? ""
        }
    }

    convenience init() {
        let sessionStore = SessionStore()
        let baseURL = SessionStore.resolveBaseURL()
        self.init(apiClient: APIClient(baseURL: baseURL, sessionStore: sessionStore), sessionStore: sessionStore)
    }

    func validate() -> [String] {
        var errors: [String] = []
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errors.append("薬名は必須です")
        }
        if let endDate, endDate < startDate {
            errors.append("終了日は開始日以降にしてください")
        }
        if selectedTimeSlots.isEmpty {
            errors.append("時間は1件以上選択してください")
        }
        if scheduleFrequency == .weekly && selectedDays.isEmpty {
            errors.append("曜日は1つ以上選択してください")
        }
        return errors
    }

    func scheduleTimes() -> [String] {
        ScheduleTimeSlot.allCases
            .filter { selectedTimeSlots.contains($0) }
            .map { timeValue(for: $0) }
    }

    func scheduleDays() -> [String] {
        ScheduleDay.allCases
            .filter { selectedDays.contains($0) }
            .map(\.rawValue)
    }

    func submit() async -> Bool {
        if sessionStore.mode == .caregiver, sessionStore.currentPatientId == nil {
            errorMessage = NSLocalizedString("medication.form.patient.required", comment: "Patient required")
            return false
        }
        let errors = validate()
        if !errors.isEmpty {
            errorMessage = errors.joined(separator: "\n")
            return false
        }
        if isSubmitting {
            return false
        }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            let patientId: String
            if sessionStore.mode == .caregiver {
                guard let currentPatientId = sessionStore.currentPatientId, !currentPatientId.isEmpty else {
                    errorMessage = NSLocalizedString("medication.form.patient.required", comment: "Patient required")
                    return false
                }
                patientId = currentPatientId
            } else {
                patientId = existingMedication?.patientId ?? ""
            }
            let doseCountValue = Int(doseCountPerIntake) ?? 0
            let strengthValue = Double(dosageStrengthValue) ?? 0
            let inventoryValue = Int(inventoryCount)
            if let existingMedication {
                let request = MedicationUpdateRequestDTO(
                    name: name,
                    dosageText: dosageText(),
                    doseCountPerIntake: doseCountValue,
                    dosageStrengthValue: strengthValue,
                    dosageStrengthUnit: dosageStrengthUnit,
                    notes: notes.isEmpty ? nil : notes,
                    startDate: startDate,
                    endDate: endDate,
                    inventoryCount: inventoryValue,
                    inventoryUnit: inventoryUnit.isEmpty ? nil : inventoryUnit
                )
                let updated = try await apiClient.updateMedication(
                    id: existingMedication.id,
                    patientId: patientId,
                    input: request
                )
                try await persistRegimen(medicationId: updated.id)
            } else {
                let request = MedicationCreateRequestDTO(
                    patientId: patientId,
                    name: name,
                    dosageText: dosageText(),
                    doseCountPerIntake: doseCountValue,
                    dosageStrengthValue: strengthValue,
                    dosageStrengthUnit: dosageStrengthUnit,
                    notes: notes.isEmpty ? nil : notes,
                    startDate: startDate,
                    endDate: endDate,
                    inventoryCount: inventoryValue,
                    inventoryUnit: inventoryUnit.isEmpty ? nil : inventoryUnit
                )
                let created = try await apiClient.createMedication(request)
                try await persistRegimen(medicationId: created.id)
            }
            return true
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
        }
    }

    func deleteMedication() async -> Bool {
        guard let existingMedication else { return false }
        if sessionStore.mode == .caregiver, sessionStore.currentPatientId == nil {
            errorMessage = NSLocalizedString("medication.form.patient.required", comment: "Patient required")
            return false
        }
        if isDeleting || isSubmitting {
            return false
        }
        isDeleting = true
        defer { isDeleting = false }
        do {
            let patientId: String
            if sessionStore.mode == .caregiver {
                guard let currentPatientId = sessionStore.currentPatientId, !currentPatientId.isEmpty else {
                    errorMessage = NSLocalizedString("medication.form.patient.required", comment: "Patient required")
                    return false
                }
                patientId = currentPatientId
            } else {
                patientId = existingMedication.patientId
            }
            try await apiClient.deleteMedication(id: existingMedication.id, patientId: patientId)
            return true
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
        }
    }

    func loadExistingScheduleIfNeeded() async {
        guard let existingMedication else { return }
        guard !didLoadSchedule else { return }
        didLoadSchedule = true
        scheduleIsLoading = true
        defer { scheduleIsLoading = false }
        do {
            let regimens = try await apiClient.fetchRegimens(medicationId: existingMedication.id)
            if let regimen = regimens.first(where: { $0.enabled }) ?? regimens.first {
                applyRegimen(regimen)
            } else {
                scheduleNotSet = true
            }
        } catch {
            scheduleNotSet = true
        }
    }

    func applyRegimen(_ regimen: RegimenDTO) {
        existingRegimenId = regimen.id
        scheduleNotSet = false
        let days = regimen.daysOfWeek
            .compactMap { ScheduleDay(rawValue: $0) }
        selectedDays = Set(days)
        scheduleFrequency = days.isEmpty ? .daily : .weekly
        let timeSlots = regimen.times
            .compactMap { slotForTimeValue($0) }
        selectedTimeSlots = Set(timeSlots)
    }

    func timeValue(for slot: ScheduleTimeSlot) -> String {
        let time = preferencesStore.slotTime(for: slot.notificationSlot)
        return String(format: "%02d:%02d", time.hour, time.minute)
    }

    private func slotForTimeValue(_ timeString: String) -> ScheduleTimeSlot? {
        let normalized = timeString.trimmingCharacters(in: .whitespacesAndNewlines)
        return ScheduleTimeSlot.allCases.first { slot in
            self.timeValue(for: slot) == normalized
        }
    }

    private func persistRegimen(medicationId: String) async throws {
        let times = scheduleTimes()
        let days = scheduleFrequency == .weekly ? scheduleDays() : []
        let timezone = TimeZone.current.identifier
        let createInput = RegimenCreateRequestDTO(
            timezone: timezone,
            startDate: startDate,
            endDate: endDate,
            times: times,
            daysOfWeek: days
        )
        if let existingRegimenId {
            let updateInput = RegimenUpdateRequestDTO(
                timezone: timezone,
                startDate: startDate,
                endDate: endDate,
                times: times,
                daysOfWeek: days,
                enabled: true
            )
            _ = try await apiClient.updateRegimen(id: existingRegimenId, input: updateInput)
        } else {
            let created = try await apiClient.createRegimen(medicationId: medicationId, input: createInput)
            existingRegimenId = created.id
        }
    }

    private func dosageText() -> String {
        let value = dosageStrengthValue.trimmingCharacters(in: .whitespacesAndNewlines)
        let unit = dosageStrengthUnit.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty && unit.isEmpty {
            return ""
        }
        if value.isEmpty {
            return unit
        }
        if unit.isEmpty {
            return value
        }
        return "\(value) \(unit)"
    }
}
