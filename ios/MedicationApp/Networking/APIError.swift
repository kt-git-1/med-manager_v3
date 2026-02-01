import Foundation

enum APIError: Error {
    case unauthorized
    case forbidden
    case notFound
    case conflict
    case validation(String)
    case unknown
}
