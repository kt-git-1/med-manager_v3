import Foundation

final class AuthService {
    private let supabaseURL: URL
    private let supabaseAnonKey: String

    init(supabaseURL: URL? = nil, supabaseAnonKey: String? = nil) {
        let env = ProcessInfo.processInfo.environment
        let info = Bundle.main.infoDictionary
        let urlString = supabaseURL?.absoluteString
            ?? env["SUPABASE_URL"]
            ?? info?["SUPABASE_URL"] as? String
        let key = supabaseAnonKey
            ?? env["SUPABASE_ANON_KEY"]
            ?? info?["SUPABASE_ANON_KEY"] as? String
        guard let urlString, let url = URL(string: urlString), let key else {
            self.supabaseURL = URL(string: "https://invalid.local")!
            self.supabaseAnonKey = ""
            print("AuthService: missing SUPABASE_URL or SUPABASE_ANON_KEY")
            return
        }
        self.supabaseURL = url
        self.supabaseAnonKey = key
    }

    func login(email: String, password: String) async throws -> String {
        return try await authenticate(
            path: "auth/v1/token",
            queryItems: [URLQueryItem(name: "grant_type", value: "password")],
            email: email,
            password: password,
            allowMissingAccessToken: false
        )
    }

    func signup(email: String, password: String) async throws -> String {
        return try await authenticate(
            path: "auth/v1/signup",
            email: email,
            password: password,
            allowMissingAccessToken: true
        )
    }

    private func authenticate(
        path: String,
        queryItems: [URLQueryItem] = [],
        email: String,
        password: String,
        allowMissingAccessToken: Bool
    ) async throws -> String {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, !password.isEmpty else {
            throw APIError.validation("email/password required")
        }
        guard !supabaseAnonKey.isEmpty else {
            throw APIError.validation("missing supabase config")
        }
        let baseURL = supabaseURL.appendingPathComponent(path)
        var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
        if !queryItems.isEmpty {
            components?.queryItems = queryItems
        }
        guard let url = components?.url else {
            throw APIError.validation("invalid supabase url")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue(supabaseAnonKey, forHTTPHeaderField: "apikey")
        request.addValue("Bearer \(supabaseAnonKey)", forHTTPHeaderField: "Authorization")
        let payload = ["email": trimmedEmail, "password": password]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])

        print("AuthService: login request to \(url.absoluteString)")
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError.network("Network error: \(error.localizedDescription)")
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.unknown
        }
        if (200...299).contains(http.statusCode) {
            let decoder = JSONDecoder()
            let authResponse = try decoder.decode(SupabaseAuthResponse.self, from: data)
            guard let token = authResponse.accessToken else {
                if allowMissingAccessToken {
                    return ""
                }
                throw APIError.validation("missing access token")
            }
            return token
        }
        let parsedMessage = parseSupabaseErrorMessage(from: data)
        let serverMessage = (parsedMessage?.isEmpty == false)
            ? parsedMessage!
            : "Request failed (status: \(http.statusCode))"
        print("AuthService: login failed \(http.statusCode) \(serverMessage)")
        switch http.statusCode {
        case 400, 422:
            throw APIError.validation(serverMessage)
        case 401:
            throw APIError.unauthorized
        case 403:
            throw APIError.forbidden
        case 404:
            throw APIError.notFound
        case 409:
            throw APIError.conflict
        default:
            throw APIError.network("\(serverMessage) (status: \(http.statusCode))")
        }
    }
}

private struct SupabaseAuthResponse: Decodable {
    let accessToken: String?

    private enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
    }
}

private struct SupabaseErrorResponse: Decodable {
    let error: String?
    let errorDescription: String?
    let message: String?

    private enum CodingKeys: String, CodingKey {
        case error
        case errorDescription = "error_description"
        case message
    }
}

private func parseSupabaseErrorMessage(from data: Data) -> String? {
    let decoder = JSONDecoder()
    if let payload = try? decoder.decode(SupabaseErrorResponse.self, from: data) {
        return payload.errorDescription ?? payload.message ?? payload.error
    }
    if let text = String(data: data, encoding: .utf8), !text.isEmpty {
        return text
    }
    return nil
}
