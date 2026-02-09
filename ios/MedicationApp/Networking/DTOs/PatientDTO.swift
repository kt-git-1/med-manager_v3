import Foundation

struct PatientDTO: Decodable, Identifiable {
    let id: String
    let displayName: String
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

struct PatientSessionResponseDTO: Decodable {
    let data: PatientSessionTokenDTO
}

struct PatientSessionTokenDTO: Decodable {
    let patientSessionToken: String
    let expiresAt: Date?
}
