import Foundation

// ---------------------------------------------------------------------------
// 011-pdf-export: Decodable DTOs for GET /api/patients/{patientId}/history/report
// ---------------------------------------------------------------------------

struct HistoryReportResponseDTO: Decodable {
    let patient: HistoryReportPatientDTO
    let range: HistoryReportRangeDTO
    let days: [HistoryReportDayDTO]
}

struct HistoryReportPatientDTO: Decodable {
    let id: String
    let displayName: String
}

struct HistoryReportRangeDTO: Decodable {
    let from: String
    let to: String
    let timezone: String
    let days: Int
}

struct HistoryReportDayDTO: Decodable {
    let date: String
    let slots: HistoryReportSlotsDTO
    let prn: [HistoryReportPrnItemDTO]
}

struct HistoryReportSlotsDTO: Decodable {
    let morning: [HistoryReportSlotItemDTO]
    let noon: [HistoryReportSlotItemDTO]
    let evening: [HistoryReportSlotItemDTO]
    let bedtime: [HistoryReportSlotItemDTO]
}

struct HistoryReportSlotItemDTO: Decodable {
    let medicationId: String
    let name: String
    let dosageText: String
    let doseCount: Double
    let status: String
    let recordedAt: String?
}

struct HistoryReportPrnItemDTO: Decodable {
    let medicationId: String
    let name: String
    let dosageText: String
    let quantity: Double
    let recordedAt: String
    let recordedBy: String
}
