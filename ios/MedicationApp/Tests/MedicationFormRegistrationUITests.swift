import XCTest

@MainActor
final class MedicationFormRegistrationUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSupplyDaysShowsAutomaticInventoryCalculation() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-MedicationFormMarketingScreenshot"]
        app.launchEnvironment["UITEST_SESSION_BOOTSTRAP"] = "1"
        app.launchEnvironment["UITEST_MODE"] = "caregiver"
        app.launchEnvironment["UITEST_CURRENT_PATIENT_ID"] = "qa-marketing-preview-patient"
        app.launch()

        XCTAssertTrue(app.staticTexts["お薬を登録"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.staticTexts["基本情報"].waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertTrue(app.staticTexts["飲むタイミング"].exists, app.debugDescription)

        let topScreenshot = XCTAttachment(screenshot: app.screenshot())
        topScreenshot.name = "Medication registration reference top"
        topScreenshot.lifetime = .keepAlways
        add(topScreenshot)

        let supplyDaysField = app.textFields["MedicationSupplyDaysField"]
        for _ in 0..<8 where !supplyDaysField.isHittable {
            app.swipeUp()
        }
        app.swipeUp()
        app.swipeUp()

        XCTAssertTrue(supplyDaysField.waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertEqual(supplyDaysField.value as? String, "30")
        XCTAssertTrue(app.staticTexts["1錠"].exists, app.debugDescription)
        XCTAssertTrue(app.staticTexts["2回"].exists, app.debugDescription)
        XCTAssertTrue(app.staticTexts["30日"].exists, app.debugDescription)

        let inventoryField = app.textFields["MedicationCalculatedInventoryField"]
        XCTAssertTrue(inventoryField.waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertEqual(inventoryField.value as? String, "60")
        XCTAssertTrue(app.buttons["この内容で登録"].exists, app.debugDescription)

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Medication registration automatic inventory"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testValidationErrorAppearsBesideDosageAndScrollsToField() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-MedicationFormValidationPreview"]
        app.launchEnvironment["UITEST_SESSION_BOOTSTRAP"] = "1"
        app.launchEnvironment["UITEST_MODE"] = "caregiver"
        app.launchEnvironment["UITEST_CURRENT_PATIENT_ID"] = "qa-validation-preview-patient"
        app.launch()

        XCTAssertTrue(app.staticTexts["お薬を登録"].waitForExistence(timeout: 10), app.debugDescription)

        let submitButton = app.buttons["この内容で登録"]
        for _ in 0..<8 where !submitButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(submitButton.isHittable, app.debugDescription)
        submitButton.tap()

        let inlineError = app.descendants(matching: .any)["MedicationBasicValidationError"]
        XCTAssertTrue(inlineError.waitForExistence(timeout: 3), app.debugDescription)
        XCTAssertTrue(app.staticTexts["用量は必須です"].isHittable, app.debugDescription)
        XCTAssertTrue(app.staticTexts["基本情報"].isHittable, app.debugDescription)
        XCTAssertFalse(app.descendants(matching: .any)["MedicationSubmissionError"].exists)

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Medication dosage inline validation"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testOpenCaregiverHomeWithEnvironmentCredentials() throws {
        let environment = ProcessInfo.processInfo.environment
        guard let email = environment["MED_UI_CAREGIVER_EMAIL"],
              let password = environment["MED_UI_CAREGIVER_PASSWORD"] else {
            throw XCTSkip("Caregiver UI test credentials are not configured")
        }

        let app = XCUIApplication()
        app.launch()

        let authChoice = app.scrollViews["CaregiverAuthChoiceView"]
        if authChoice.waitForExistence(timeout: 5) {
            app.staticTexts["ログイン"].firstMatch.tap()
        }

        let loginView = app.scrollViews["CaregiverLoginView"]
        XCTAssertTrue(loginView.waitForExistence(timeout: 8), app.debugDescription)

        let emailField = app.textFields["メールアドレス"]
        XCTAssertTrue(emailField.waitForExistence(timeout: 5), app.debugDescription)
        emailField.tap()
        emailField.typeText(email)

        let passwordField = app.secureTextFields["パスワード"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5), app.debugDescription)
        passwordField.tap()
        passwordField.typeText(password)

        app.buttons["ログイン"].tap()
        XCTAssertTrue(loginView.waitForNonExistence(timeout: 20), app.debugDescription)
    }
}
