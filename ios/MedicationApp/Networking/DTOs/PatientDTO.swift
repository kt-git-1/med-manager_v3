import Foundation

struct PatientDTO: Decodable, Identifiable {
    let id: String
    let displayName: String
    let slotTimes: PatientSlotTimesDTO?
}

struct PatientSlotTimesDTO: Codable, Equatable {
    let morning: String
    let noon: String
    let evening: String
    let bedtime: String
}

struct PatientListResponseDTO: Decodable {
    let data: [PatientDTO]
}

struct CreatePatientResponseDTO: Decodable {
    let data: PatientDTO
}

struct LinkingCodeResponseDTO: Decodable {
    let data: LinkingCodeDTO
}

struct LinkingCodeDTO: Decodable, Identifiable {
    var id: String { code }
    let code: String
    let expiresAt: Date
}

struct RevokeResponseDTO: Decodable {
    let data: RevokeResultDTO
}

struct RevokeResultDTO: Decodable {
    let revoked: Bool
}

struct DeletePatientResponseDTO: Decodable {
    let data: DeletePatientResultDTO
}

struct DeletePatientResultDTO: Decodable {
    let deleted: Bool
}

struct PatientSlotTimesUpdateRequestDTO: Encodable {
    let slotTimes: PatientSlotTimesDTO
}

struct PatientSlotTimesResponseDTO: Decodable {
    let data: PatientSlotTimesDataDTO
}

struct PatientSlotTimesDataDTO: Decodable {
    let slotTimes: PatientSlotTimesDTO
}

struct PatientSessionResponseDTO: Decodable {
    let data: PatientSessionTokenDTO
}

struct PatientSessionTokenDTO: Decodable {
    let patientSessionToken: String
    let expiresAt: Date?
}
