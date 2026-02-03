import Foundation

struct MedicationDTO: Decodable, Identifiable {
    let id: String
    let patientId: String
    let name: String
    let dosageText: String
    let doseCountPerIntake: Int
    let dosageStrengthValue: Double
    let dosageStrengthUnit: String
    let notes: String?
    let startDate: Date
    let endDate: Date?
    let inventoryCount: Int?
    let inventoryUnit: String?
    let isActive: Bool
    let isArchived: Bool
    let nextScheduledAt: Date?
}

struct MedicationListResponseDTO: Decodable {
    let data: [MedicationDTO]
}

struct MedicationResponseDTO: Decodable {
    let data: MedicationDTO
}

struct MedicationCreateRequestDTO: Encodable {
    let patientId: String
    let name: String
    let dosageText: String
    let doseCountPerIntake: Int
    let dosageStrengthValue: Double
    let dosageStrengthUnit: String
    let notes: String?
    let startDate: Date
    let endDate: Date?
    let inventoryCount: Int?
    let inventoryUnit: String?
}

struct MedicationUpdateRequestDTO: Encodable {
    let name: String
    let dosageText: String
    let doseCountPerIntake: Int
    let dosageStrengthValue: Double
    let dosageStrengthUnit: String
    let notes: String?
    let startDate: Date
    let endDate: Date?
    let inventoryCount: Int?
    let inventoryUnit: String?
}
