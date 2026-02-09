import Foundation

struct InventoryItemDTO: Decodable, Identifiable {
    let medicationId: String
    let name: String
    let isPrn: Bool
    let doseCountPerIntake: Double
    let inventoryEnabled: Bool
    let inventoryQuantity: Double
    let inventoryLowThreshold: Int
    let periodEnded: Bool
    let low: Bool
    let out: Bool
    let dailyPlannedUnits: Double?
    let daysRemaining: Int?
    let refillDueDate: String?

    var id: String { medicationId }
}

struct InventoryListDataDTO: Decodable {
    let patientId: String
    let medications: [InventoryItemDTO]
}

struct InventoryListResponseDTO: Decodable {
    let data: InventoryListDataDTO
}

struct InventoryResponseDTO: Decodable {
    let data: InventoryItemDTO
}

struct InventoryUpdateRequestDTO: Encodable {
    let inventoryEnabled: Bool?
    let inventoryQuantity: Double?
    let inventoryLowThreshold: Int?
}

struct InventoryAdjustRequestDTO: Encodable {
    let reason: String
    let delta: Double?
    let absoluteQuantity: Double?
}
