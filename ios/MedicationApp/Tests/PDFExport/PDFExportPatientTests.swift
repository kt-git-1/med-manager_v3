import XCTest

// ---------------------------------------------------------------------------
// T008: UI smoke test — patient mode has zero export UI elements
// ---------------------------------------------------------------------------

final class PDFExportPatientTests: XCTestCase {

    // MARK: - Patient Mode — Zero Export UI

    func testPatientHistoryTabHasNoPDFExportButton() throws {
        throw XCTSkip("PDFExportButton not yet implemented (Phase 3 T020).")
        // Given: App launched in patient mode
        // When: User navigates to the history tab
        // Then: No element with accessibilityIdentifier "PDFExportButton" exists
        //
        // Verification approach:
        //   let app = XCUIApplication()
        //   app.launch()
        //   let historyTab = app.tabBars.buttons["履歴"]
        //   historyTab.tap()
        //   let exportButton = app.buttons["PDFExportButton"]
        //   XCTAssertFalse(exportButton.exists, "Patient mode should have no PDF export button")
    }

    func testPatientMonthViewHasNoExportUI() throws {
        throw XCTSkip("PDFExportButton not yet implemented (Phase 3 T020).")
        // Given: Patient on history tab viewing month view
        // When: Patient navigates through month view and day detail
        // Then: No export-related UI element is present
        //
        // Verification approach:
        //   let app = XCUIApplication()
        //   app.launch()
        //   // Navigate to history tab
        //   // Tap through month cells / day detail
        //   // Assert no "PDF出力" text or "PDFExportButton" exists
    }

    func testNoPDFExportTextInPatientHistoryScreens() throws {
        throw XCTSkip("PDFExportButton not yet implemented (Phase 3 T020).")
        // Given: Patient on any history screen
        // Then: No "PDF出力" text appears anywhere
        //
        // Verification approach:
        //   let app = XCUIApplication()
        //   app.launch()
        //   let historyTab = app.tabBars.buttons["履歴"]
        //   historyTab.tap()
        //   let pdfText = app.staticTexts["PDF出力"]
        //   XCTAssertFalse(pdfText.exists, "Patient mode should have no 'PDF出力' text")
    }
}
