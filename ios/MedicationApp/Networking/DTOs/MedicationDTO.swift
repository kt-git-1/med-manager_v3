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

struct RegimenDTO: Decodable, Identifiable {
    let id: String
    let patientId: String
    let medicationId: String
    let timezone: String
    let startDate: Date
    let endDate: Date?
    let times: [String]
    let daysOfWeek: [String]
    let enabled: Bool
}

struct RegimenListResponseDTO: Decodable {
    let data: [RegimenDTO]
}

struct RegimenResponseDTO: Decodable {
    let data: RegimenDTO
}

struct RegimenCreateRequestDTO: Encodable {
    let timezone: String
    let startDate: Date
    let endDate: Date?
    let times: [String]
    let daysOfWeek: [String]
}

struct RegimenUpdateRequestDTO: Encodable {
    let timezone: String?
    let startDate: Date?
    let endDate: Date?
    let times: [String]?
    let daysOfWeek: [String]?
    let enabled: Bool?
}
