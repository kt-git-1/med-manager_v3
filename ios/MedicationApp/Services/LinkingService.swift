import Foundation

@MainActor
final class LinkingService {
    private let apiClient: APIClient

    init(sessionStore: SessionStore? = nil) {
        let store = sessionStore ?? SessionStore()
        let baseURL = SessionStore.resolveBaseURL()
        self.apiClient = APIClient(baseURL: baseURL, sessionStore: store)
    }

    func link(code: String) async throws -> String {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw APIError.validation("code required")
        }
        let regex = try NSRegularExpression(pattern: "^\\d{6}$")
        let range = NSRange(location: 0, length: trimmed.utf16.count)
        if regex.firstMatch(in: trimmed, range: range) == nil {
            throw APIError.validation("code invalid")
        }
        return try await apiClient.exchangeLinkCode(code: trimmed)
    }
}
