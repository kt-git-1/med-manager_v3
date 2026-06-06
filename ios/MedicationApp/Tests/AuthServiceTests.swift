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
}
