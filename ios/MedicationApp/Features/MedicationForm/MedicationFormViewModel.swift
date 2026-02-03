import Foundation

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

    private let apiClient: APIClient
    private let sessionStore: SessionStore
    private let existingMedication: MedicationDTO?

    var isEditing: Bool {
        existingMedication != nil
    }

    init(apiClient: APIClient, sessionStore: SessionStore, existingMedication: MedicationDTO? = nil) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.existingMedication = existingMedication
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
        return errors
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
                _ = try await apiClient.updateMedication(
                    id: existingMedication.id,
                    patientId: patientId,
                    input: request
                )
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
                _ = try await apiClient.createMedication(request)
            }
            return true
        } catch {
            errorMessage = NSLocalizedString("common.error.generic", comment: "Generic error")
            return false
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
