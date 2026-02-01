import Foundation

enum APIError: Error {
    case unauthorized
    case forbidden
    case notFound
    case conflict
    case validation(String)
    case network(String)
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
        case .unknown:
            return "Unexpected error"
        }
    }
}
