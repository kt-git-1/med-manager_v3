import Foundation

enum APIError: Error {
    case unauthorized
    case forbidden
    case notFound
    case conflict
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
        case .validation(let message):
            return message
        case .network(let message):
            return message
        case .patientLimitExceeded:
            return NSLocalizedString(
                "billing.gate.patientLimit.body",
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
