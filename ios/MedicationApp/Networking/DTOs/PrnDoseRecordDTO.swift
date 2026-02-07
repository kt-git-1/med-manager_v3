import Foundation

enum PrnActorTypeDTO: String, Decodable {
    case patient = "PATIENT"
    case caregiver = "CAREGIVER"
}

struct PrnDoseRecordDTO: Decodable, Identifiable {
    let id: String
    let patientId: String
    let medicationId: String
    let takenAt: Date
    let quantityTaken: Int
    let actorType: PrnActorTypeDTO
    let createdAt: Date
}

struct MedicationInventorySnapshotDTO: Decodable {
    let medicationId: String
    let inventoryEnabled: Bool
    let inventoryQuantity: Int
    let inventoryLowThreshold: Int
    let low: Bool
    let out: Bool
}

struct PrnDoseRecordCreateRequestDTO: Encodable {
    let medicationId: String
    let takenAt: Date?
    let quantityTaken: Int?
}

struct PrnDoseRecordCreateResponseDTO: Decodable {
    let record: PrnDoseRecordDTO
    let medicationInventory: MedicationInventorySnapshotDTO?
}
