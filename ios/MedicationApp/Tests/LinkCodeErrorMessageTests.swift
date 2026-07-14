import XCTest
@testable import MedicationApp

final class LinkCodeErrorMessageTests: XCTestCase {
    func testUnauthorizedIsShownInJapanese() {
        XCTAssertEqual(
            LinkCodeErrorMessage.message(for: APIError.unauthorized),
            "連携できませんでした。コードを確認して、もう一度お試しください"
        )
    }

    func testServerNetworkMessageIsNotExposedDirectly() {
        XCTAssertEqual(
            LinkCodeErrorMessage.message(for: APIError.network("Invalid server response")),
            "通信に失敗しました。接続を確認して、もう一度お試しください"
        )
    }

    func testMissingOrUsedCodeUsesExistingGuidance() {
        XCTAssertEqual(
            LinkCodeErrorMessage.message(for: APIError.notFound),
            "コードが見つからないか期限切れです"
        )
        XCTAssertEqual(
            LinkCodeErrorMessage.message(for: APIError.conflict),
            "コードが見つからないか期限切れです"
        )
    }
}
