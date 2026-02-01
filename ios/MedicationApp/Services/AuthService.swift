import Foundation

final class AuthService {
    private let supabaseURL: URL
    private let supabaseAnonKey: String

    init(supabaseURL: URL? = nil, supabaseAnonKey: String? = nil) {
        let env = ProcessInfo.processInfo.environment
        let urlString = supabaseURL?.absoluteString ?? env["SUPABASE_URL"]
        let key = supabaseAnonKey ?? env["SUPABASE_ANON_KEY"]
        guard let urlString, let url = URL(string: urlString), let key else {
            self.supabaseURL = URL(string: "https://invalid.local")!
            self.supabaseAnonKey = ""
            return
        }
        self.supabaseURL = url
        self.supabaseAnonKey = key
    }

    func login(email: String, password: String) async throws -> String {
        return try await authenticate(
            path: "auth/v1/token?grant_type=password",
            email: email,
            password: password
        )
    }

    func signup(email: String, password: String) async throws -> String {
        return try await authenticate(path: "auth/v1/signup", email: email, password: password)
    }

    private func authenticate(path: String, email: String, password: String) async throws -> String {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, !password.isEmpty else {
            throw APIError.validation("email/password required")
        }
        guard !supabaseAnonKey.isEmpty else {
            throw APIError.validation("missing supabase config")
        }
        let url = supabaseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue(supabaseAnonKey, forHTTPHeaderField: "apikey")
        request.addValue("Bearer \(supabaseAnonKey)", forHTTPHeaderField: "Authorization")
        let payload = ["email": trimmedEmail, "password": password]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError.unknown
        }
        if (200...299).contains(http.statusCode) {
            let decoder = JSONDecoder()
            let authResponse = try decoder.decode(SupabaseAuthResponse.self, from: data)
            guard let token = authResponse.accessToken else {
                throw APIError.validation("missing access token")
            }
            return token
        }
        if http.statusCode == 400 {
            throw APIError.validation("invalid credentials")
        }
        throw APIError.unknown
    }
}

private struct SupabaseAuthResponse: Decodable {
    let accessToken: String?

    private enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
    }
}
