import Foundation

enum HistoryDoseStatusDTO: String, Decodable, Equatable {
    case pending
    case taken
    case missed
}

enum HistorySlotDTO: String, Decodable, Equatable {
    case morning
    case noon
    case evening
    case bedtime
}

enum HistorySlotSummaryStatusDTO: String, Decodable, Equatable {
    case pending
    case taken
    case missed
    case none
}

struct HistoryDaySummaryDTO: Decodable, Equatable {
    let date: String
    let slotSummary: HistorySlotSummaryDTO
}

struct HistorySlotSummaryDTO: Decodable, Equatable {
    let morning: HistorySlotSummaryStatusDTO
    let noon: HistorySlotSummaryStatusDTO
    let evening: HistorySlotSummaryStatusDTO
    let bedtime: HistorySlotSummaryStatusDTO
}

struct HistoryMonthResponseDTO: Decodable, Equatable {
    let year: Int
    let month: Int
    let days: [HistoryDaySummaryDTO]
    let prnCountByDay: [String: Int]?

    enum CodingKeys: String, CodingKey {
        case year
        case month
        case days
        case monthSummary
        case prnCountByDay
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        year = try container.decode(Int.self, forKey: .year)
        month = try container.decode(Int.self, forKey: .month)
        if let days = try container.decodeIfPresent([HistoryDaySummaryDTO].self, forKey: .days) {
            self.days = days
        } else {
            self.days = try container.decode([HistoryDaySummaryDTO].self, forKey: .monthSummary)
        }
        prnCountByDay = try container.decodeIfPresent([String: Int].self, forKey: .prnCountByDay)
    }
}

struct HistoryDayItemDTO: Decodable, Equatable {
    let medicationId: String
    let medicationName: String
    let dosageText: String
    let doseCountPerIntake: Double
    let scheduledAt: Date
    let slot: HistorySlotDTO
    let effectiveStatus: HistoryDoseStatusDTO
}

struct PrnHistoryItemDTO: Decodable, Equatable {
    let medicationId: String
    let medicationName: String
    let takenAt: Date
    let quantityTaken: Double
    let actorType: PrnActorTypeDTO
}

struct HistoryDayResponseDTO: Decodable, Equatable {
    let date: String
    let doses: [HistoryDayItemDTO]
    let prnItems: [PrnHistoryItemDTO]

    enum CodingKeys: String, CodingKey {
        case date
        case doses
        case dayDetails
        case prnItems
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        date = try container.decode(String.self, forKey: .date)
        if let doses = try container.decodeIfPresent([HistoryDayItemDTO].self, forKey: .doses) {
            self.doses = doses
        } else {
            self.doses = try container.decode([HistoryDayItemDTO].self, forKey: .dayDetails)
        }
        prnItems = try container.decodeIfPresent([PrnHistoryItemDTO].self, forKey: .prnItems) ?? []
    }
}
