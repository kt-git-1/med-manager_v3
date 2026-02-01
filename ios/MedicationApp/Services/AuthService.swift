import Foundation

final class AuthService {
    func login(email: String, password: String) async throws -> String {
        guard !email.isEmpty, !password.isEmpty else {
            throw APIError.validation("email/password required")
        }
        // TODO: integrate Supabase Auth.
        return "caregiver-token-stub"
    }
}
