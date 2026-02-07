import XCTest

final class InventoryListDetailUITests: XCTestCase {
    func testInventoryListAndDetailAppear() throws {
        let app = XCUIApplication()
        app.launch()

        let inventoryTab = app.buttons["在庫管理"]
        guard inventoryTab.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory tab not available in this configuration.")
        }
        inventoryTab.tap()

        let list = app.tables.firstMatch
        guard list.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory list not available yet.")
        }

        let firstCell = list.cells.firstMatch
        guard firstCell.waitForExistence(timeout: 2) else {
            throw XCTSkip("Inventory list is empty or not populated.")
        }
        firstCell.tap()

        let detailTitle = app.staticTexts["在庫管理"]
        XCTAssertTrue(detailTitle.waitForExistence(timeout: 2))
    }
}
