import XCTest

@MainActor
final class CaregiverPushUITests: XCTestCase {
    func testCaregiverSettingsShowsPushToggleOff() throws {
        let app = launchCaregiver()
        let toggle = openPushSettings(in: app)

        XCTAssertEqual(toggle.value as? String, "0")
    }

    func testMockPushToggleCanEnableAndDisable() throws {
        let app = launchCaregiver()
        let toggle = openPushSettings(in: app)

        toggle.tap()
        XCTAssertTrue(waitForToggle(toggle, value: "1"))

        toggle.tap()
        XCTAssertTrue(waitForToggle(toggle, value: "0"))
    }

    func testRemotePushRoutesCaregiverToHistory() throws {
        let app = launchCaregiver(remotePush: true)

        XCTAssertTrue(
            app.descendants(matching: .any)["CaregiverHistoryView"]
                .waitForExistence(timeout: 5)
        )
    }

    func testPatientPreviewHasNoCaregiverPushToggle() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-PatientTodayV105Preview", "-disableAnalytics"]
        app.launch()

        XCTAssertFalse(app.switches["PushNotificationToggle"].waitForExistence(timeout: 1))
    }

    private func launchCaregiver(remotePush: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-disableAnalytics"]
        app.launchEnvironment = [
            "UITEST_SESSION_BOOTSTRAP": "1",
            "UITEST_CAREGIVER_TOKEN": "uitest-caregiver-token",
            "UITEST_CURRENT_PATIENT_ID": "uitest-patient",
            "UITEST_MARK_TUTORIALS_SEEN": "1",
            "UITEST_MODE": "caregiver",
            "UITEST_MOCK_PUSH": "1"
        ]
        if remotePush {
            app.launchEnvironment["UITEST_REMOTE_PUSH_DATE"] = "2026-08-28"
            app.launchEnvironment["UITEST_REMOTE_PUSH_SLOT"] = "noon"
            app.launchEnvironment["UITEST_REMOTE_PUSH_PATIENT_ID"] = "uitest-patient"
        }
        app.launch()
        return app
    }

    private func openPushSettings(in app: XCUIApplication) -> XCUIElement {
        let settingsTab = app.buttons["設定"]
        XCTAssertTrue(settingsTab.waitForExistence(timeout: 5))
        settingsTab.tap()

        let toggle = app.switches.element(boundBy: 1)
        for _ in 0..<8 where !toggle.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertTrue(toggle.isHittable)
        return toggle
    }

    private func waitForToggle(_ toggle: XCUIElement, value: String) -> Bool {
        let predicate = NSPredicate(format: "value == %@", value)
        return XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: predicate, object: toggle)],
            timeout: 5
        ) == .completed
    }
}
