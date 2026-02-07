import Foundation

struct InventoryItemDTO: Decodable, Identifiable {
    let medicationId: String
    let name: String
    let doseCountPerIntake: Int
    let inventoryEnabled: Bool
    let inventoryQuantity: Int
    let inventoryLowThreshold: Int
    let periodEnded: Bool
    let low: Bool
    let out: Bool
    let dailyPlannedUnits: Int?
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
    let inventoryQuantity: Int?
    let inventoryLowThreshold: Int?
}

struct InventoryAdjustRequestDTO: Encodable {
    let reason: String
    let delta: Int?
    let absoluteQuantity: Int?
}
