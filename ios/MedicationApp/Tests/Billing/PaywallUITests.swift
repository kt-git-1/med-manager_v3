import XCTest

@MainActor
final class PaywallUITests: XCTestCase {

    func testInitialReleaseDoesNotShowBillingEntryPoints() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertFalse(app.buttons["billing.premium.upgrade"].exists)
        XCTAssertFalse(app.buttons["billing.premium.restore"].exists)
        XCTAssertFalse(app.buttons["billing.paywall.purchase"].exists)
        XCTAssertFalse(app.buttons["billing.paywall.restore"].exists)
        XCTAssertFalse(app.buttons["pdfexport.lock.upgrade"].exists)
        XCTAssertFalse(app.buttons["history.retention.lock.upgrade"].exists)
    }
}
