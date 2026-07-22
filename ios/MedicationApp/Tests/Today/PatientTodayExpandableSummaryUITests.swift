import XCTest

@MainActor
final class PatientTodayExpandableSummaryUITests: XCTestCase {
    func testMedicationListExpandsAndCollapses() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-PatientTodayV105Preview"]
        app.launch()

        let toggle = app.buttons["PatientTodaySummaryToggle-morning"]
        for _ in 0..<4 where !toggle.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(toggle.waitForExistence(timeout: 3))
        XCTAssertTrue(toggle.isHittable)

        toggle.tap()
        let medicationName = app.staticTexts["整腸剤 50 mg"]
        XCTAssertTrue(medicationName.waitForExistence(timeout: 2))
        let expandedScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        expandedScreenshot.name = "Patient today summary expanded"
        expandedScreenshot.lifetime = .keepAlways
        add(expandedScreenshot)

        toggle.tap()
        XCTAssertFalse(medicationName.waitForExistence(timeout: 1))
    }
}
