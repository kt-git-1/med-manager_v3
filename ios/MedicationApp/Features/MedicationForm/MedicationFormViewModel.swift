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
        // TODO: call APIClient
        return true
    }
}
