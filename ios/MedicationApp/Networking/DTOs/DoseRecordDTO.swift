import Foundation

enum DoseStatusDTO: String, Decodable {
    case pending
    case taken
    case missed
}

enum RecordedByTypeDTO: String, Decodable {
    case patient
    case caregiver
}

struct MedicationSnapshotDTO: Decodable, Equatable {
    let name: String
    let dosageText: String
    let doseCountPerIntake: Int
    let dosageStrengthValue: Double
    let dosageStrengthUnit: String
}

struct ScheduleDoseDTO: Decodable, Identifiable, Equatable {
    let key: String
    let patientId: String
    let medicationId: String
    let scheduledAt: Date
    let effectiveStatus: DoseStatusDTO?
    let medicationSnapshot: MedicationSnapshotDTO

    var id: String { key }
}

struct ScheduleResponseDTO: Decodable {
    let data: [ScheduleDoseDTO]
}

struct DoseRecordDTO: Decodable {
    let medicationId: String
    let scheduledAt: Date
    let takenAt: Date
    let recordedByType: RecordedByTypeDTO
}

struct DoseRecordResponseDTO: Decodable {
    let data: DoseRecordDTO
}

struct DoseRecordCreateRequestDTO: Encodable {
    let medicationId: String
    let scheduledAt: Date
}
