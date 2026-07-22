import XCTest

@MainActor
final class PatientTodayExpandableSummaryUITests: XCTestCase {
    func testMedicationListExpandsAndCollapses() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-PatientTodayV105Preview"]
        app.launch()

        XCTAssertTrue(app.staticTexts["夜のお薬"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.staticTexts["昼のお薬"].exists)

        let toggle = app.buttons["PatientTodaySummaryToggle-morning"]
        for _ in 0..<4 where !toggle.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(toggle.waitForExistence(timeout: 3))
        XCTAssertTrue(toggle.isHittable)
        XCTAssertTrue(app.staticTexts["飲み遅れのお薬"].exists)
        let lateRecordButton = app.buttons["PatientTodayLateRecordButton-noon"]
        XCTAssertTrue(lateRecordButton.exists)
        XCTAssertTrue(lateRecordButton.isHittable)
        XCTAssertFalse(app.staticTexts["次は 眠前 23:00"].exists)

        lateRecordButton.tap()
        XCTAssertTrue(app.alerts["昼のお薬を記録"].waitForExistence(timeout: 2))
        app.alerts.buttons["キャンセル"].tap()

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
