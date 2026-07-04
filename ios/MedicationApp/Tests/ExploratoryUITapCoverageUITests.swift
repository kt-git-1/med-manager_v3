import XCTest

@MainActor
final class ExploratoryUITapCoverageTests: XCTestCase {
    private var qaContext: QAContext!

    override func setUp() async throws {
        continueAfterFailure = false
        qaContext = try await QAContext.build()
    }

    func testCaregiverMajorScreensAreHumanTappable() throws {
        let app = launchedApp(mode: "caregiver")

        handleSystemPermissionPrompts(in: app)
        XCTAssertTrue(waitForCaregiverToday(in: app), app.debugDescription)

        tapTab("薬", in: app)
        XCTAssertTrue(app.staticTexts["薬を管理"].waitForExistence(timeout: 10))

        tapTab("履歴", in: app)
        XCTAssertTrue(app.staticTexts["服薬履歴"].waitForExistence(timeout: 10))

        tapTab("在庫", in: app)
        XCTAssertTrue(app.staticTexts["在庫を確認"].waitForExistence(timeout: 10))

        tapTab("設定", in: app)
        XCTAssertTrue(app.otherElements["PatientManagementView"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts[qaContext.patientName].waitForExistence(timeout: 10))

        let pushToggle = app.switches["PushNotificationToggle"]
        if pushToggle.waitForExistence(timeout: 3) {
            XCTAssertTrue(pushToggle.exists)
        }

        tapTab("今日", in: app)
        XCTAssertTrue(app.staticTexts["今日の服薬"].waitForExistence(timeout: 10))
    }

    func testPatientMajorScreensAreHumanTappable() throws {
        let app = launchedApp(mode: "patient")

        handleSystemPermissionPrompts(in: app)
        XCTAssertTrue(app.staticTexts["今日のお薬"].waitForExistence(timeout: 10))

        tapTab("履歴", in: app)
        XCTAssertTrue(app.staticTexts["履歴"].waitForExistence(timeout: 10))

        tapTab("設定", in: app)
        XCTAssertTrue(app.staticTexts["お薬の通知"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["billing.premium.upgrade"].exists)
        XCTAssertFalse(app.buttons["billing.paywall.purchase"].exists)

        tapTab("今日", in: app)
        XCTAssertTrue(app.staticTexts["今日のお薬"].waitForExistence(timeout: 10))
    }

    func testCaregiverCanRegisterForPushNotifications() throws {
        let app = launchedApp(mode: "caregiver")

        handleSystemPermissionPrompts(in: app)
        XCTAssertTrue(waitForCaregiverToday(in: app), app.debugDescription)

        tapTab("設定", in: app)
        XCTAssertTrue(app.otherElements["PatientManagementView"].waitForExistence(timeout: 10))

        let pushToggle = app.switches["PushNotificationToggle"]
        XCTAssertTrue(pushToggle.waitForExistence(timeout: 10), app.debugDescription)
        if pushToggle.frame.maxY > app.frame.height - 140 {
            app.swipeUp()
        }
        if (pushToggle.value as? String) != "1" {
            pushToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
            handleSystemPermissionPrompts(in: app)
        }

        XCTAssertTrue(
            waitForSwitch(pushToggle, value: "1", timeout: 30),
            "Push toggle did not become enabled. Current UI: \(app.debugDescription)"
        )
        XCTAssertFalse(app.alerts.firstMatch.exists, app.debugDescription)
    }

    func testCaregiverReceivesAndOpensDoseTakenPushNotification() async throws {
        let app = launchedApp(mode: "caregiver")

        handleSystemPermissionPrompts(in: app)
        XCTAssertTrue(waitForCaregiverToday(in: app), app.debugDescription)
        try enableCaregiverPushIfNeeded(in: app)

        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCTAssertTrue(springboard.wait(for: .runningForeground, timeout: 10))

        try await qaContext.recordPatientDoseForPush()
        let notification = waitForDoseTakenNotification(in: springboard, timeout: 45)
        XCTAssertNotNil(notification, springboard.debugDescription)
        notification?.tap()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))
        XCTAssertTrue(app.staticTexts["服薬履歴"].waitForExistence(timeout: 20), app.debugDescription)
    }

    private func launchedApp(mode: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment = [
            "API_BASE_URL": qaContext.apiBaseURL,
            "SUPABASE_URL": qaContext.supabaseURL,
            "SUPABASE_ANON_KEY": qaContext.supabaseAnonKey,
            "UITEST_SESSION_BOOTSTRAP": "1",
            "UITEST_MODE": mode,
            "UITEST_CAREGIVER_TOKEN": mode == "caregiver" ? qaContext.caregiverJWT : "",
            "UITEST_PATIENT_TOKEN": mode == "patient" ? qaContext.patientToken : "",
            "UITEST_CURRENT_PATIENT_ID": qaContext.patientId,
            "UITEST_MARK_TUTORIALS_SEEN": "1"
        ]
        app.launch()
        return app
    }

    private func waitForCaregiverToday(in app: XCUIApplication) -> Bool {
        if app.staticTexts["今日の服薬"].waitForExistence(timeout: 15) {
            return true
        }
        handleSystemPermissionPrompts(in: app)
        let todayTab = app.buttons["今日"].firstMatch
        if todayTab.waitForExistence(timeout: 5) {
            todayTab.tap()
        }
        handleSystemPermissionPrompts(in: app)
        return app.staticTexts["今日の服薬"].waitForExistence(timeout: 15)
    }

    private func handleSystemPermissionPrompts(in app: XCUIApplication) {
        addUIInterruptionMonitor(withDescription: "System permission prompt") { alert in
            for label in ["許可", "Allow", "許可しない", "Don’t Allow", "Don't Allow"] {
                let button = alert.buttons[label]
                if button.exists {
                    button.tap()
                    return true
                }
            }
            return false
        }
        app.tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for label in ["許可", "Allow"] {
            let button = springboard.buttons[label]
            if button.waitForExistence(timeout: 1) {
                button.tap()
                break
            }
        }
    }

    private func tapTab(_ label: String, in app: XCUIApplication) {
        let tab = app.buttons[label].firstMatch
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "Missing tab: \(label)")
        tab.tap()
    }

    private func enableCaregiverPushIfNeeded(in app: XCUIApplication) throws {
        tapTab("設定", in: app)
        XCTAssertTrue(app.otherElements["PatientManagementView"].waitForExistence(timeout: 10))

        let pushToggle = app.switches["PushNotificationToggle"]
        XCTAssertTrue(pushToggle.waitForExistence(timeout: 10), app.debugDescription)
        if pushToggle.frame.maxY > app.frame.height - 140 {
            app.swipeUp()
        }
        if (pushToggle.value as? String) != "1" {
            pushToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
            handleSystemPermissionPrompts(in: app)
        }
        XCTAssertTrue(waitForSwitch(pushToggle, value: "1", timeout: 30), app.debugDescription)
    }

    private func waitForDoseTakenNotification(
        in springboard: XCUIApplication,
        timeout: TimeInterval
    ) -> XCUIElement? {
        let title = springboard.staticTexts["服薬記録"].firstMatch
        if title.waitForExistence(timeout: 8) {
            return title
        }

        let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.01))
        let middle = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        top.press(forDuration: 0.15, thenDragTo: middle)

        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let title = springboard.staticTexts["服薬記録"].firstMatch
            if title.exists {
                return title
            }
            let body = springboard.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "お薬を記録")).firstMatch
            if body.exists {
                return body
            }
            RunLoop.current.run(until: Date().addingTimeInterval(1.0))
        }
        return nil
    }

    private func waitForSwitch(_ toggle: XCUIElement, value expectedValue: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if (toggle.value as? String) == expectedValue {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        return (toggle.value as? String) == expectedValue
    }
}

private struct QAContext {
    let apiBaseURL: String
    let supabaseURL: String
    let supabaseAnonKey: String
    let caregiverJWT: String
    let patientId: String
    let patientName: String
    let medicationId: String
    let medicationName: String
    let patientToken: String

    static func build() async throws -> QAContext {
        let apiBaseURL = try env("API_BASE_URL")
        let supabaseURL = try env("SUPABASE_URL")
        let supabaseAnonKey = try env("SUPABASE_ANON_KEY")
        let email = try env("SUPABASE_TEST_EMAIL")
        let password = try env("SUPABASE_TEST_PASSWORD")

        let jwt = try await login(
            supabaseURL: supabaseURL,
            anonKey: supabaseAnonKey,
            email: email,
            password: password
        )
        let caregiverToken = "caregiver-\(jwt)"
        let patientName = "UITap QA Patient"
        let medicationName = "UITap QA Medication \(Int(Date().timeIntervalSince1970))"
        let patientId = try await getOrCreatePatient(
            apiBaseURL: apiBaseURL,
            caregiverToken: caregiverToken,
            patientName: patientName
        )
        let medicationId = try await createMedication(
            apiBaseURL: apiBaseURL,
            caregiverToken: caregiverToken,
            patientId: patientId,
            medicationName: medicationName
        )
        try await createRegimen(
            apiBaseURL: apiBaseURL,
            caregiverToken: caregiverToken,
            medicationId: medicationId,
            startDate: todayString(),
            dayOfWeek: todayDayOfWeek()
        )
        let code = try await issueLinkingCode(
            apiBaseURL: apiBaseURL,
            caregiverToken: caregiverToken,
            patientId: patientId
        )
        let patientToken = try await exchangeLinkingCode(apiBaseURL: apiBaseURL, code: code)

        return QAContext(
            apiBaseURL: apiBaseURL,
            supabaseURL: supabaseURL,
            supabaseAnonKey: supabaseAnonKey,
            caregiverJWT: jwt,
            patientId: patientId,
            patientName: patientName,
            medicationId: medicationId,
            medicationName: medicationName,
            patientToken: patientToken
        )
    }

    func recordPatientDoseForPush() async throws {
        _ = try await Self.jsonRequest(
            url: "\(apiBaseURL)/api/patient/dose-records",
            method: "POST",
            headers: ["authorization": "Bearer \(patientToken)"],
            body: [
                "medicationId": medicationId,
                "scheduledAt": ISO8601DateFormatter().string(from: Date().addingTimeInterval(-60))
            ]
        )
    }

    private static func env(_ key: String) throws -> String {
        let env = ProcessInfo.processInfo.environment
        if let value = env[key], !value.isEmpty {
            return value
        }
        let testRunnerKey = "TEST_RUNNER_\(key)"
        if let value = env[testRunnerKey], !value.isEmpty {
            return value
        }
        if let value = try qaEnvironmentFile()[key], !value.isEmpty {
            return value
        }
        throw XCTSkip("Missing \(key) for live UI QA.")
    }

    private static func qaEnvironmentFile() throws -> [String: String] {
        let url = URL(fileURLWithPath: "/tmp/med-manager-ios-ui-qa-env.json")
        guard FileManager.default.fileExists(atPath: url.path) else {
            return [:]
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode([String: String].self, from: data)
    }

    private static func login(
        supabaseURL: String,
        anonKey: String,
        email: String,
        password: String
    ) async throws -> String {
        let response = try await jsonRequest(
            url: "\(supabaseURL)/auth/v1/token?grant_type=password",
            method: "POST",
            headers: [
                "apikey": anonKey,
                "authorization": "Bearer \(anonKey)"
            ],
            body: [
                "email": email,
                "password": password
            ]
        )
        guard let token = response["access_token"] as? String else {
            throw QAError.invalidResponse("Supabase login missing access_token")
        }
        return token
    }

    private static func getOrCreatePatient(
        apiBaseURL: String,
        caregiverToken: String,
        patientName: String
    ) async throws -> String {
        let list = try await jsonRequest(
            url: "\(apiBaseURL)/api/patients",
            method: "GET",
            headers: ["authorization": "Bearer \(caregiverToken)"]
        )
        if let patients = list["data"] as? [[String: Any]],
           let existing = patients.first(where: { ($0["displayName"] as? String) == patientName }),
           let id = existing["id"] as? String {
            return id
        }

        let created = try await jsonRequest(
            url: "\(apiBaseURL)/api/patients",
            method: "POST",
            headers: ["authorization": "Bearer \(caregiverToken)"],
            body: ["displayName": patientName]
        )
        guard let data = created["data"] as? [String: Any],
              let id = data["id"] as? String else {
            throw QAError.invalidResponse("Patient create missing id")
        }
        return id
    }

    private static func createMedication(
        apiBaseURL: String,
        caregiverToken: String,
        patientId: String,
        medicationName: String
    ) async throws -> String {
        let created = try await jsonRequest(
            url: "\(apiBaseURL)/api/medications",
            method: "POST",
            headers: ["authorization": "Bearer \(caregiverToken)"],
            body: [
                "patientId": patientId,
                "name": medicationName,
                "dosageText": "1 tablet",
                "doseCountPerIntake": 1,
                "dosageStrengthValue": 10,
                "dosageStrengthUnit": "mg",
                "startDate": todayString(),
                "inventoryEnabled": true,
                "inventoryQuantity": 4,
                "inventoryLowThreshold": 5,
                "inventoryUnit": "錠"
            ]
        )
        guard let data = created["data"] as? [String: Any],
              let id = data["id"] as? String else {
            throw QAError.invalidResponse("Medication create missing id")
        }
        return id
    }

    private static func createRegimen(
        apiBaseURL: String,
        caregiverToken: String,
        medicationId: String,
        startDate: String,
        dayOfWeek: String
    ) async throws {
        _ = try await jsonRequest(
            url: "\(apiBaseURL)/api/medications/\(medicationId)/regimens",
            method: "POST",
            headers: ["authorization": "Bearer \(caregiverToken)"],
            body: [
                "timezone": "Asia/Tokyo",
                "startDate": startDate,
                "times": ["23:50"],
                "daysOfWeek": [dayOfWeek]
            ]
        )
    }

    private static func issueLinkingCode(
        apiBaseURL: String,
        caregiverToken: String,
        patientId: String
    ) async throws -> String {
        let response = try await jsonRequest(
            url: "\(apiBaseURL)/api/patients/\(patientId)/linking-codes",
            method: "POST",
            headers: ["authorization": "Bearer \(caregiverToken)"]
        )
        guard let data = response["data"] as? [String: Any],
              let code = data["code"] as? String else {
            throw QAError.invalidResponse("Linking code response missing code")
        }
        return code
    }

    private static func exchangeLinkingCode(apiBaseURL: String, code: String) async throws -> String {
        let response = try await jsonRequest(
            url: "\(apiBaseURL)/api/patient/link",
            method: "POST",
            headers: ["x-forwarded-for": "10.44.55.\(Int.random(in: 1...240))"],
            body: ["code": code]
        )
        guard let data = response["data"] as? [String: Any],
              let token = data["patientSessionToken"] as? String else {
            throw QAError.invalidResponse("Patient link response missing token")
        }
        return token
    }

    private static func jsonRequest(
        url: String,
        method: String,
        headers: [String: String],
        body: [String: Any]? = nil
    ) async throws -> [String: Any] {
        guard let url = URL(string: url) else {
            throw QAError.invalidResponse("Invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        if let body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw QAError.invalidResponse("Missing HTTP response")
        }
        guard (200...299).contains(http.statusCode) else {
            let text = String(data: data, encoding: .utf8) ?? ""
            throw QAError.invalidResponse("HTTP \(http.statusCode): \(text)")
        }
        if data.isEmpty {
            return [:]
        }
        let object = try JSONSerialization.jsonObject(with: data)
        guard let dictionary = object as? [String: Any] else {
            throw QAError.invalidResponse("Expected JSON object")
        }
        return dictionary
    }

    private static func todayString() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(identifier: "Asia/Tokyo")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    private static func todayDayOfWeek() -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        switch calendar.component(.weekday, from: Date()) {
        case 1: return "SUN"
        case 2: return "MON"
        case 3: return "TUE"
        case 4: return "WED"
        case 5: return "THU"
        case 6: return "FRI"
        default: return "SAT"
        }
    }
}

private enum QAError: Error, CustomStringConvertible {
    case invalidResponse(String)

    var description: String {
        switch self {
        case .invalidResponse(let message):
            return message
        }
    }
}
