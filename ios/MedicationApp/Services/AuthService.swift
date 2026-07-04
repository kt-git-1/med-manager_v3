import Foundation

struct SupabaseSession: Sendable {
    let accessToken: String?
    let refreshToken: String?
    let expiresIn: Int?

    var hasAccessToken: Bool {
        accessToken?.isEmpty == false
    }
}

final class AuthService: Sendable {
    private let supabaseURL: URL
    private let supabaseAnonKey: String
    private let emailConfirmationRedirectURL: URL

    init(
        supabaseURL: URL? = nil,
        supabaseAnonKey: String? = nil,
        emailConfirmationRedirectURL: URL? = nil
    ) {
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
            self.emailConfirmationRedirectURL = Self.resolveEmailConfirmationRedirectURL(
                explicitURL: emailConfirmationRedirectURL,
                env: env,
                info: info
            )
            print("AuthService: missing SUPABASE_URL or SUPABASE_ANON_KEY")
            return
        }
        self.supabaseURL = url
        self.supabaseAnonKey = key
        self.emailConfirmationRedirectURL = Self.resolveEmailConfirmationRedirectURL(
            explicitURL: emailConfirmationRedirectURL,
            env: env,
            info: info
        )
    }

    func login(email: String, password: String) async throws -> SupabaseSession {
        return try await authenticate(
            path: "auth/v1/token",
            queryItems: [URLQueryItem(name: "grant_type", value: "password")],
            email: email,
            password: password,
            allowMissingAccessToken: false
        )
    }

    func signup(email: String, password: String) async throws -> SupabaseSession {
        let request = try makeSignupRequest(email: email, password: password)
        return try await sendAuthRequest(request, allowMissingAccessToken: true)
    }

    func resendSignupConfirmation(email: String) async throws {
        guard !supabaseAnonKey.isEmpty else {
            throw APIError.validation("missing supabase config")
        }
        let request = try makeResendSignupConfirmationRequest(email: email)
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError.network(NSLocalizedString("auth.error.network", comment: "Auth network error"))
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.unknown
        }
        if (200...299).contains(http.statusCode) {
            return
        }
        if http.statusCode == 429 {
            throw APIError.network(
                NSLocalizedString(
                    "caregiver.signup.resend.tooManyRequests",
                    comment: "Too many confirmation email resend requests"
                )
            )
        }
        throw mapSupabaseError(statusCode: http.statusCode, data: data)
    }

    func refreshSession(refreshToken: String) async throws -> SupabaseSession {
        guard !refreshToken.isEmpty else {
            throw APIError.validation("refresh token required")
        }
        guard !supabaseAnonKey.isEmpty else {
            throw APIError.validation("missing supabase config")
        }
        let request = try makeRefreshRequest(refreshToken: refreshToken)
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError.network(NSLocalizedString("auth.error.network", comment: "Auth network error"))
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.unknown
        }
        if (200...299).contains(http.statusCode) {
            let session = try Self.decodeSession(from: data)
            guard session.hasAccessToken else {
                throw APIError.validation("missing access token")
            }
            return session
        }
        throw mapSupabaseError(statusCode: http.statusCode, data: data)
    }

    private func authenticate(
        path: String,
        queryItems: [URLQueryItem] = [],
        email: String,
        password: String,
        allowMissingAccessToken: Bool
    ) async throws -> SupabaseSession {
        let request = try makeAuthRequest(
            path: path,
            queryItems: queryItems,
            email: email,
            password: password
        )
        return try await sendAuthRequest(request, allowMissingAccessToken: allowMissingAccessToken)
    }

    func makeSignupRequest(email: String, password: String) throws -> URLRequest {
        try makeAuthRequest(
            path: "auth/v1/signup",
            queryItems: [URLQueryItem(name: "redirect_to", value: emailConfirmationRedirectURL.absoluteString)],
            email: email,
            password: password
        )
    }

    private func makeAuthRequest(
        path: String,
        queryItems: [URLQueryItem] = [],
        email: String,
        password: String
    ) throws -> URLRequest {
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
        return request
    }

    private func sendAuthRequest(
        _ request: URLRequest,
        allowMissingAccessToken: Bool
    ) async throws -> SupabaseSession {
        print("AuthService: auth request to \(request.url?.absoluteString ?? "unknown")")
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError.network(NSLocalizedString("auth.error.network", comment: "Auth network error"))
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.unknown
        }
        if (200...299).contains(http.statusCode) {
            let session = try Self.decodeSession(from: data)
            guard session.hasAccessToken else {
                if allowMissingAccessToken {
                    return session
                }
                throw APIError.validation("missing access token")
            }
            return session
        }
        throw mapSupabaseError(statusCode: http.statusCode, data: data)
    }

    func makeRefreshRequest(refreshToken: String) throws -> URLRequest {
        let baseURL = supabaseURL.appendingPathComponent("auth/v1/token")
        var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "grant_type", value: "refresh_token")]
        guard let url = components?.url else {
            throw APIError.validation("invalid supabase url")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue(supabaseAnonKey, forHTTPHeaderField: "apikey")
        request.addValue("Bearer \(supabaseAnonKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["refresh_token": refreshToken],
            options: []
        )
        return request
    }

    func makeResendSignupConfirmationRequest(email: String) throws -> URLRequest {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty else {
            throw APIError.validation("email required")
        }
        let url = supabaseURL.appendingPathComponent("auth/v1/resend")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue(supabaseAnonKey, forHTTPHeaderField: "apikey")
        request.addValue("Bearer \(supabaseAnonKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: [
                "type": "signup",
                "email": trimmedEmail,
                "options": [
                    "email_redirect_to": emailConfirmationRedirectURL.absoluteString
                ]
            ],
            options: []
        )
        return request
    }

    private static func resolveEmailConfirmationRedirectURL(
        explicitURL: URL?,
        env: [String: String],
        info: [String: Any]?
    ) -> URL {
        if let explicitURL {
            return explicitURL
        }

        let redirectURLString = env["EMAIL_CONFIRMATION_REDIRECT_URL"]
            ?? info?["EMAIL_CONFIRMATION_REDIRECT_URL"] as? String
        if let redirectURLString, let redirectURL = URL(string: redirectURLString) {
            return redirectURL
        }

        let baseURLString = env["API_BASE_URL"] ?? info?["API_BASE_URL"] as? String
        let baseURL = baseURLString.flatMap(URL.init(string:)) ?? AppConstants.defaultAPIBaseURL
        return baseURL.appendingPathComponent("auth/confirmed")
    }

    static func decodeSession(from data: Data) throws -> SupabaseSession {
        let decoder = JSONDecoder()
        let authResponse = try decoder.decode(SupabaseAuthResponse.self, from: data)
        return SupabaseSession(
            accessToken: authResponse.accessToken,
            refreshToken: authResponse.refreshToken,
            expiresIn: authResponse.expiresIn
        )
    }

    private func mapSupabaseError(statusCode: Int, data: Data) -> APIError {
        let parsedMessage = parseSupabaseErrorMessage(from: data)
        let serverMessage = (parsedMessage?.isEmpty == false)
            ? parsedMessage!
            : "Request failed (status: \(statusCode))"
        print("AuthService: request failed \(statusCode) \(serverMessage)")
        let userMessage = Self.userFacingAuthErrorMessage(
            statusCode: statusCode,
            serverMessage: serverMessage
        )
        switch statusCode {
        case 400, 422:
            return APIError.validation(userMessage)
        case 401:
            return APIError.validation(userMessage)
        case 403:
            return APIError.validation(userMessage)
        case 404:
            return APIError.validation(userMessage)
        case 409:
            return APIError.validation(userMessage)
        default:
            return APIError.network(userMessage)
        }
    }

    static func userFacingAuthErrorMessage(statusCode: Int, serverMessage: String) -> String {
        let normalized = serverMessage.lowercased()

        if normalized.contains("invalid login credentials") ||
            normalized.contains("invalid credentials") ||
            normalized.contains("invalid_grant") {
            return NSLocalizedString("auth.error.invalidCredentials", comment: "Invalid credentials")
        }

        if normalized.contains("sending confirmation email") ||
            normalized.contains("send confirmation email") ||
            normalized.contains("confirmation email") && normalized.contains("failed") {
            return NSLocalizedString("auth.error.confirmationEmailFailed", comment: "Confirmation email failed")
        }

        if normalized.contains("email not confirmed") ||
            normalized.contains("email_not_confirmed") ||
            normalized.contains("confirm") {
            return NSLocalizedString("auth.error.emailNotConfirmed", comment: "Email not confirmed")
        }

        if normalized.contains("already registered") ||
            normalized.contains("already exists") ||
            normalized.contains("user_already_exists") {
            return NSLocalizedString("auth.error.emailAlreadyRegistered", comment: "Email already registered")
        }

        if normalized.contains("password") &&
            (normalized.contains("at least") ||
             normalized.contains("weak") ||
             normalized.contains("length") ||
             normalized.contains("short")) {
            return NSLocalizedString("auth.error.weakPassword", comment: "Weak password")
        }

        if normalized.contains("email") &&
            (normalized.contains("invalid") ||
             normalized.contains("format")) {
            return NSLocalizedString("auth.error.invalidEmail", comment: "Invalid email")
        }

        switch statusCode {
        case 400, 401, 422:
            return NSLocalizedString("auth.error.checkCredentials", comment: "Check credentials")
        case 403:
            return NSLocalizedString("auth.error.forbidden", comment: "Forbidden auth error")
        case 404:
            return NSLocalizedString("auth.error.notFound", comment: "Auth endpoint not found")
        case 409:
            return NSLocalizedString("auth.error.emailAlreadyRegistered", comment: "Email already registered")
        case 429:
            return NSLocalizedString("auth.error.tooManyRequests", comment: "Too many requests")
        default:
            return NSLocalizedString("auth.error.unavailable", comment: "Auth service unavailable")
        }
    }
}

private struct SupabaseAuthResponse: Decodable {
    let accessToken: String?
    let refreshToken: String?
    let expiresIn: Int?

    private enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
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
