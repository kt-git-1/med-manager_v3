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

    func testCaregiverTodayShowsLateActualTimeAndKeepsProxyRecording() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-CaregiverTodayPreview"]
        app.launch()

        XCTAssertTrue(app.descendants(matching: .any)["CaregiverTodayLateRecordAlertCard"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["実際 13:21"].exists)
        XCTAssertTrue(app.staticTexts["本人が記録"].exists)
        XCTAssertFalse(app.buttons["CaregiverTodayRecordSlotButton.morning"].exists)

        let noonRecordButton = app.buttons["CaregiverTodayRecordSlotButton.noon"]
        for _ in 0..<3 where !noonRecordButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(noonRecordButton.waitForExistence(timeout: 2))
        XCTAssertTrue(noonRecordButton.isHittable)

        let caregiverTodayScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        caregiverTodayScreenshot.name = "Caregiver today late record and proxy action"
        caregiverTodayScreenshot.lifetime = .keepAlways
        add(caregiverTodayScreenshot)
    }

    func testCaregiverHistoryGroupsMedicinesByTimeSlotAndExpands() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-CaregiverHistoryV105Preview"]
        app.launch()

        let morningHeader = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "朝、予定 08:00")
        ).firstMatch
        let noonHeader = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "昼、予定 12:30")
        ).firstMatch
        let eveningHeader = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "夜、予定 19:00")
        ).firstMatch
        let bedtimeHeader = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "眠前、予定 23:50")
        ).firstMatch
        XCTAssertTrue(morningHeader.waitForExistence(timeout: 3))
        XCTAssertTrue(noonHeader.exists)
        XCTAssertTrue(eveningHeader.exists)
        XCTAssertTrue(bedtimeHeader.exists)

        XCTAssertTrue(noonHeader.isHittable)
        noonHeader.tap()

        XCTAssertTrue(app.staticTexts["カルボシステイン 500 mg"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["整腸剤 50 mg"].exists)
        XCTAssertTrue(app.staticTexts["実際 17:51"].exists)
        XCTAssertTrue(app.staticTexts["5時間21分遅れ"].exists)

        let caregiverHistoryScreenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        caregiverHistoryScreenshot.name = "Caregiver history noon slot expanded"
        caregiverHistoryScreenshot.lifetime = .keepAlways
        add(caregiverHistoryScreenshot)
    }
}
