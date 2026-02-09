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
    let isPrn: Bool
    let prnInstructions: String?
    let startDate: Date
    let endDate: Date?
    let inventoryCount: Int?
    let inventoryUnit: String?
    let inventoryEnabled: Bool
    let inventoryQuantity: Int
    let inventoryOut: Bool
    let isActive: Bool
    let isArchived: Bool
    let nextScheduledAt: Date?
    let regimenTimes: [String]?
    let regimenDaysOfWeek: [String]?

    var isOutOfStock: Bool {
        inventoryEnabled && inventoryOut
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case patientId
        case name
        case dosageText
        case doseCountPerIntake
        case dosageStrengthValue
        case dosageStrengthUnit
        case notes
        case isPrn
        case prnInstructions
        case startDate
        case endDate
        case inventoryCount
        case inventoryUnit
        case inventoryEnabled
        case inventoryQuantity
        case inventoryOut
        case isActive
        case isArchived
        case nextScheduledAt
        case regimenTimes
        case regimenDaysOfWeek
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        patientId = try container.decode(String.self, forKey: .patientId)
        name = try container.decode(String.self, forKey: .name)
        dosageText = try container.decode(String.self, forKey: .dosageText)
        doseCountPerIntake = try container.decode(Int.self, forKey: .doseCountPerIntake)
        dosageStrengthValue = try container.decode(Double.self, forKey: .dosageStrengthValue)
        dosageStrengthUnit = try container.decode(String.self, forKey: .dosageStrengthUnit)
        notes = try container.decodeIfPresent(String.self, forKey: .notes)
        isPrn = try container.decodeIfPresent(Bool.self, forKey: .isPrn) ?? false
        prnInstructions = try container.decodeIfPresent(String.self, forKey: .prnInstructions)
        startDate = try container.decode(Date.self, forKey: .startDate)
        endDate = try container.decodeIfPresent(Date.self, forKey: .endDate)
        inventoryCount = try container.decodeIfPresent(Int.self, forKey: .inventoryCount)
        inventoryUnit = try container.decodeIfPresent(String.self, forKey: .inventoryUnit)
        inventoryEnabled = try container.decodeIfPresent(Bool.self, forKey: .inventoryEnabled) ?? false
        inventoryQuantity = try container.decodeIfPresent(Int.self, forKey: .inventoryQuantity) ?? 0
        inventoryOut = try container.decodeIfPresent(Bool.self, forKey: .inventoryOut) ?? false
        isActive = try container.decode(Bool.self, forKey: .isActive)
        isArchived = try container.decode(Bool.self, forKey: .isArchived)
        nextScheduledAt = try container.decodeIfPresent(Date.self, forKey: .nextScheduledAt)
        regimenTimes = try container.decodeIfPresent([String].self, forKey: .regimenTimes)
        regimenDaysOfWeek = try container.decodeIfPresent([String].self, forKey: .regimenDaysOfWeek)
    }
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
    let isPrn: Bool
    let prnInstructions: String?
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
    let isPrn: Bool
    let prnInstructions: String?
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
