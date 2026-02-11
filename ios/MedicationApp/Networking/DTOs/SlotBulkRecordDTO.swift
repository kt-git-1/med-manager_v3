import Foundation

struct SlotBulkRecordRequestDTO: Encodable {
    let date: String
    let slot: String
}

struct SlotBulkRecordResponseDTO: Decodable {
    let updatedCount: Int
    let remainingCount: Int
    let totalPills: Double
    let medCount: Int
    let slotTime: String
    let slotSummary: [String: String]
    let recordingGroupId: String?
}
