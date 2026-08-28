import XCTest

@MainActor
final class NotificationTapUITests: XCTestCase {
    func testNotificationTapPreviewOpensPatientToday() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-PatientNotificationTapPreview", "-disableAnalytics"]
        app.launch()

        let nextExpectedSlot = app.descendants(matching: .any)["PatientTodayNextExpectedSlot"]
        XCTAssertTrue(nextExpectedSlot.waitForExistence(timeout: 3))
        XCTAssertTrue(nextExpectedSlot.label.contains("次は 夜 19:00"))
        XCTAssertTrue(app.buttons["PatientTodayLateRecordButton-noon"].exists)
        XCTAssertTrue(
            app.staticTexts["PatientNotificationDeepLinkApplied"]
                .waitForExistence(timeout: 3)
        )
    }
}
