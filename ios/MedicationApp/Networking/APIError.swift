import Foundation

enum APIError: Error {
    case unauthorized
    case forbidden
    case notFound
    case conflict
    case insufficientInventory
    case validation(String)
    case network(String)
    case patientLimitExceeded(limit: Int, current: Int)
    case historyRetentionLimit(cutoffDate: String, retentionDays: Int)
    case unknown
}

extension APIError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .unauthorized:
            return "Unauthorized"
        case .forbidden:
            return "Forbidden"
        case .notFound:
            return "Not found"
        case .conflict:
            return "Conflict"
        case .insufficientInventory:
            return NSLocalizedString("patient.today.inventory.insufficient", comment: "Insufficient inventory")
        case .validation(let message):
            return message
        case .network(let message):
            return message
        case .patientLimitExceeded:
            return NSLocalizedString(
                "caregiver.patients.limit.initialRelease",
                comment: "Patient limit exceeded"
            )
        case .historyRetentionLimit:
            return NSLocalizedString(
                "history.retention.lock.caregiver.body",
                comment: "History retention limit"
            )
        case .unknown:
            return "Unexpected error"
        }
    }
}
