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
        case .mon: return NSLocalizedString("schedule.day.mon", comment: "Monday")
        case .tue: return NSLocalizedString("schedule.day.tue", comment: "Tuesday")
        case .wed: return NSLocalizedString("schedule.day.wed", comment: "Wednesday")
        case .thu: return NSLocalizedString("schedule.day.thu", comment: "Thursday")
        case .fri: return NSLocalizedString("schedule.day.fri", comment: "Friday")
        case .sat: return NSLocalizedString("schedule.day.sat", comment: "Saturday")
        case .sun: return NSLocalizedString("schedule.day.sun", comment: "Sunday")
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
        case .morning: return NSLocalizedString("schedule.slot.morning", comment: "Morning")
        case .noon: return NSLocalizedString("schedule.slot.noon", comment: "Noon")
        case .evening: return NSLocalizedString("schedule.slot.evening", comment: "Evening")
        case .bedtime: return NSLocalizedString("schedule.slot.bedtime", comment: "Bedtime")
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
    @Published var inventoryUnit = NSLocalizedString("common.unit.tablet", comment: "Tablet unit")
    @Published var isPrn = false
    @Published var prnInstructions = ""
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
            inventoryUnit = NSLocalizedString("common.unit.tablet", comment: "Tablet unit")
            isPrn = existingMedication.isPrn
            prnInstructions = existingMedication.prnInstructions ?? ""
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
            errors.append(NSLocalizedString("medication.form.validation.name.required", comment: "Name required"))
        }
        let dosageValue = dosageStrengthValue.trimmingCharacters(in: .whitespacesAndNewlines)
        let dosageUnit = dosageStrengthUnit.trimmingCharacters(in: .whitespacesAndNewlines)
        let unknownLabel = NSLocalizedString("common.dosage.unknown", comment: "Unknown dosage")
        if dosageUnit.isEmpty {
            errors.append(NSLocalizedString("medication.form.validation.dosage.required", comment: "Dosage required"))
        } else if dosageUnit != unknownLabel && dosageValue.isEmpty {
            errors.append(NSLocalizedString("medication.form.validation.dosage.value.required", comment: "Dosage value required"))
        }
        if let endDate, endDate < startDate {
            errors.append(NSLocalizedString("medication.form.validation.endDate.invalid", comment: "End date invalid"))
        }
        if !isPrn {
            if selectedTimeSlots.isEmpty {
                errors.append(NSLocalizedString("medication.form.validation.timeSlot.required", comment: "Time slot required"))
            }
            if scheduleFrequency == .weekly && selectedDays.isEmpty {
                errors.append(NSLocalizedString("medication.form.validation.weekday.required", comment: "Weekday required"))
            }
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
            let inventoryUnitValue = NSLocalizedString("common.unit.tablet", comment: "Tablet unit")
            if let existingMedication {
                let request = MedicationUpdateRequestDTO(
                    name: name,
                    dosageText: dosageText(),
                    doseCountPerIntake: doseCountValue,
                    dosageStrengthValue: strengthValue,
                    dosageStrengthUnit: dosageStrengthUnit,
                    notes: notes.isEmpty ? nil : notes,
                    isPrn: isPrn,
                    prnInstructions: prnInstructions.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? nil
                        : prnInstructions,
                    startDate: startDate,
                    endDate: endDate,
                    inventoryCount: inventoryValue,
                    inventoryUnit: inventoryUnitValue
                )
                let updated = try await apiClient.updateMedication(
                    id: existingMedication.id,
                    patientId: patientId,
                    input: request
                )
                if !isPrn {
                    try await persistRegimen(medicationId: updated.id)
                }
            } else {
                let request = MedicationCreateRequestDTO(
                    patientId: patientId,
                    name: name,
                    dosageText: dosageText(),
                    doseCountPerIntake: doseCountValue,
                    dosageStrengthValue: strengthValue,
                    dosageStrengthUnit: dosageStrengthUnit,
                    notes: notes.isEmpty ? nil : notes,
                    isPrn: isPrn,
                    prnInstructions: prnInstructions.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? nil
                        : prnInstructions,
                    startDate: startDate,
                    endDate: endDate,
                    inventoryCount: inventoryValue,
                    inventoryUnit: inventoryUnitValue
                )
                let created = try await apiClient.createMedication(request)
                if !isPrn {
                    try await persistRegimen(medicationId: created.id)
                }
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
        guard !isPrn else { return }
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
