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
    let date: Date
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
}

struct HistoryDayItemDTO: Decodable, Equatable {
    let medicationId: String
    let medicationName: String
    let dosageText: String
    let doseCountPerIntake: Int
    let scheduledAt: Date
    let slot: HistorySlotDTO
    let effectiveStatus: HistoryDoseStatusDTO
}

struct HistoryDayResponseDTO: Decodable, Equatable {
    let date: Date
    let doses: [HistoryDayItemDTO]
}
