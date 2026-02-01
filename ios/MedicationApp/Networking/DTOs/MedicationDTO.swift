import Foundation

struct MedicationDTO: Decodable {
    let id: String
    let name: String
    let startDate: Date
    let nextScheduledAt: Date?
}

struct MedicationListResponseDTO: Decodable {
    let data: [MedicationDTO]
}
