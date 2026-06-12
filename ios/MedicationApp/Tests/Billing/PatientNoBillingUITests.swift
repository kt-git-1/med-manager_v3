import XCTest

@MainActor
final class PatientNoBillingUITests: XCTestCase {

    func testPatientModeHasNoBillingIdentifiersInInitialRelease() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertFalse(app.buttons["billing.premium.upgrade"].exists)
        XCTAssertFalse(app.buttons["billing.premium.restore"].exists)
        XCTAssertFalse(app.buttons["billing.paywall.purchase"].exists)
        XCTAssertFalse(app.buttons["billing.paywall.restore"].exists)
    }
}
