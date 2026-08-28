import XCTest

@MainActor
final class SlotBulkRecordUITests: XCTestCase {
    func testMissedPatientSlotShowsEnabledBulkRecordButton() throws {
        let app = launchPatientPreview()
        let button = patientNoonRecordButton(in: app)

        XCTAssertTrue(button.exists)
        XCTAssertTrue(button.isEnabled)
        XCTAssertTrue(button.isHittable)
    }

    func testPatientBulkRecordShowsConfirmationDialog() throws {
        let app = launchPatientPreview()
        patientNoonRecordButton(in: app).tap()

        let alert = app.alerts["昼のお薬を記録"]
        XCTAssertTrue(alert.waitForExistence(timeout: 2))
        XCTAssertTrue(alert.buttons["記録する"].exists)
        XCTAssertTrue(alert.buttons["キャンセル"].exists)
    }

    func testPatientBulkRecordCancelKeepsSlotRecordable() throws {
        let app = launchPatientPreview()
        let button = patientNoonRecordButton(in: app)
        button.tap()

        let alert = app.alerts["昼のお薬を記録"]
        XCTAssertTrue(alert.waitForExistence(timeout: 2))
        alert.buttons["キャンセル"].tap()

        XCTAssertFalse(alert.waitForExistence(timeout: 1))
        XCTAssertTrue(button.exists)
        XCTAssertTrue(button.isEnabled)
    }

    func testCaregiverBulkProxyRecordShowsConfirmationDialog() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-CaregiverTodayPreview", "-disableAnalytics"]
        app.launch()

        let button = app.buttons["CaregiverTodayRecordSlotButton.noon"]
        for _ in 0..<3 where !button.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(button.waitForExistence(timeout: 3))
        XCTAssertTrue(button.isHittable)
        XCTAssertEqual(button.label, "2件をまとめて代理で記録")

        button.tap()
        let alert = app.alerts["時間帯の服薬を記録"]
        XCTAssertTrue(alert.waitForExistence(timeout: 2))
        XCTAssertTrue(alert.buttons["代理で記録する"].exists)
        XCTAssertTrue(alert.buttons["キャンセル"].exists)
    }

    private func launchPatientPreview() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-PatientTodayV105Preview", "-disableAnalytics"]
        app.launch()
        return app
    }

    private func patientNoonRecordButton(in app: XCUIApplication) -> XCUIElement {
        let button = app.buttons["PatientTodayLateRecordButton-noon"]
        for _ in 0..<4 where !button.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(button.waitForExistence(timeout: 3))
        return button
    }
}
