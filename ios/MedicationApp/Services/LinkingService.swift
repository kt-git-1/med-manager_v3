import Foundation

final class LinkingService {
    func link(code: String) async throws -> String {
        guard !code.isEmpty else {
            throw APIError.validation("code required")
        }
        // TODO: replace with real link-code exchange.
        return "patient-token-\(code)"
    }
}
