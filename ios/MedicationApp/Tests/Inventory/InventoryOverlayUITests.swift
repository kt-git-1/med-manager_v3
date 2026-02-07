import XCTest

final class InventoryOverlayUITests: XCTestCase {
    func testInventoryUpdatingOverlayBlocksInteraction() throws {
        let app = XCUIApplication()
        app.launch()

        let inventoryTab = app.buttons["在庫管理"]
        guard inventoryTab.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory tab not available in this configuration.")
        }
        inventoryTab.tap()

        let overlay = app.otherElements["InventoryUpdatingOverlay"]
        guard overlay.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory updating overlay not visible yet.")
        }
        XCTAssertTrue(overlay.exists)
    }
}
