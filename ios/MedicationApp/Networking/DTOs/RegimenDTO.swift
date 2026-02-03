import Foundation

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
