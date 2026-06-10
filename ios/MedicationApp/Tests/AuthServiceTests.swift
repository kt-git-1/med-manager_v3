import XCTest
@testable import MedicationApp

final class AuthServiceTests: XCTestCase {
    func testDecodeSessionReadsAccessRefreshAndExpiresIn() throws {
        let data = """
        {
          "access_token": "access-token",
          "refresh_token": "refresh-token",
          "expires_in": 3600
        }
        """.data(using: .utf8)!

        let session = try AuthService.decodeSession(from: data)

        XCTAssertEqual(session.accessToken, "access-token")
        XCTAssertEqual(session.refreshToken, "refresh-token")
        XCTAssertEqual(session.expiresIn, 3600)
        XCTAssertTrue(session.hasAccessToken)
    }

    func testDecodeSessionSupportsMissingAccessTokenForSignupConfirmation() throws {
        let data = "{}".data(using: .utf8)!

        let session = try AuthService.decodeSession(from: data)

        XCTAssertNil(session.accessToken)
        XCTAssertFalse(session.hasAccessToken)
    }

    func testRefreshRequestUsesRefreshTokenGrant() throws {
        let service = AuthService(
            supabaseURL: URL(string: "https://example.supabase.co")!,
            supabaseAnonKey: "anon-key"
        )

        let request = try service.makeRefreshRequest(refreshToken: "refresh-token")
        let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)
        let body = try XCTUnwrap(request.httpBody)
        let payload = try JSONSerialization.jsonObject(with: body) as? [String: String]

        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(components?.path, "/auth/v1/token")
        XCTAssertEqual(
            components?.queryItems,
            [URLQueryItem(name: "grant_type", value: "refresh_token")]
        )
        XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "anon-key")
        XCTAssertEqual(payload?["refresh_token"], "refresh-token")
    }

    func testSignupRequestUsesEmailConfirmationRedirectURL() throws {
        let service = AuthService(
            supabaseURL: URL(string: "https://example.supabase.co")!,
            supabaseAnonKey: "anon-key",
            emailConfirmationRedirectURL: URL(string: "https://okusuri-mimamori.com/auth/confirmed")!
        )

        let request = try service.makeSignupRequest(email: " user@example.com ", password: "password")
        let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)
        let body = try XCTUnwrap(request.httpBody)
        let payload = try JSONSerialization.jsonObject(with: body) as? [String: String]

        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(components?.path, "/auth/v1/signup")
        XCTAssertEqual(
            components?.queryItems,
            [URLQueryItem(name: "redirect_to", value: "https://okusuri-mimamori.com/auth/confirmed")]
        )
        XCTAssertEqual(payload?["email"], "user@example.com")
        XCTAssertEqual(payload?["password"], "password")
    }

    func testResendSignupConfirmationRequestUsesSignupType() throws {
        let service = AuthService(
            supabaseURL: URL(string: "https://example.supabase.co")!,
            supabaseAnonKey: "anon-key",
            emailConfirmationRedirectURL: URL(string: "https://okusuri-mimamori.com/auth/confirmed")!
        )

        let request = try service.makeResendSignupConfirmationRequest(email: " user@example.com ")
        let body = try XCTUnwrap(request.httpBody)
        let payload = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        let options = payload?["options"] as? [String: String]

        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.url?.path, "/auth/v1/resend")
        XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "anon-key")
        XCTAssertEqual(payload?["type"] as? String, "signup")
        XCTAssertEqual(payload?["email"] as? String, "user@example.com")
        XCTAssertEqual(options?["email_redirect_to"], "https://okusuri-mimamori.com/auth/confirmed")
    }

    func testUserFacingAuthErrorMapsInvalidCredentials() {
        let message = AuthService.userFacingAuthErrorMessage(
            statusCode: 400,
            serverMessage: "Invalid login credentials"
        )

        XCTAssertEqual(message, "メールアドレスまたはパスワードが正しくありません。")
    }

    func testUserFacingAuthErrorMapsStatusOnlyBadRequest() {
        let message = AuthService.userFacingAuthErrorMessage(
            statusCode: 400,
            serverMessage: "Request failed (status: 400)"
        )

        XCTAssertEqual(message, "メールアドレスとパスワードを確認してください。")
    }

    func testUserFacingAuthErrorMapsSignupAlreadyRegistered() {
        let message = AuthService.userFacingAuthErrorMessage(
            statusCode: 409,
            serverMessage: "User already registered"
        )

        XCTAssertEqual(message, "このメールアドレスはすでに登録されています。ログインしてください。")
    }

    func testUserFacingAuthErrorMapsNetworkUnavailable() {
        let message = AuthService.userFacingAuthErrorMessage(
            statusCode: 500,
            serverMessage: "Internal server error"
        )

        XCTAssertEqual(message, "ログイン機能が一時的に利用できません。しばらくしてからもう一度お試しください。")
    }
}
