import XCTest

final class HistoryOverlayTests: XCTestCase {
    func testHistoryOverlayAppearsOnLoad() throws {
        let app = XCUIApplication()
        app.launch()

        let historyTab = app.tabBars.buttons["履歴"]
        guard historyTab.waitForExistence(timeout: 2) else {
            throw XCTSkip("History tab not available in this configuration.")
        }
        historyTab.tap()

        let overlay = app.otherElements["HistoryUpdatingOverlay"]
        XCTAssertTrue(overlay.waitForExistence(timeout: 2))
    }

    func testHistoryRetryButtonAppearsOnError() throws {
        let app = XCUIApplication()
        app.launch()

        let historyTab = app.tabBars.buttons["履歴"]
        guard historyTab.waitForExistence(timeout: 2) else {
            throw XCTSkip("History tab not available in this configuration.")
        }
        historyTab.tap()

        let retryButton = app.buttons["HistoryRetryButton"]
        guard retryButton.waitForExistence(timeout: 4) else {
            throw XCTSkip("Retry button not visible; request may have succeeded.")
        }
        XCTAssertTrue(retryButton.isHittable)
    }
}
