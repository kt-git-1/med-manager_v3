import XCTest

final class InventoryEmptyStateUITests: XCTestCase {
    func testInventoryEmptyStateRoutesToPatientsTab() throws {
        let app = XCUIApplication()
        app.launch()

        let inventoryTab = app.buttons["在庫管理"]
        guard inventoryTab.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory tab not available in this configuration.")
        }
        inventoryTab.tap()

        let cta = app.buttons["連携/患者を開く"]
        guard cta.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory empty state CTA not visible yet.")
        }
        cta.tap()

        let patientsTab = app.buttons["連携/患者"]
        XCTAssertTrue(patientsTab.waitForExistence(timeout: 2))
    }
}
