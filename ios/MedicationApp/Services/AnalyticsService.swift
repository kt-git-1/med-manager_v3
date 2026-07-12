import Foundation
import FirebaseAnalytics

enum PremiumFeature: String, Sendable {
    case reminderAdvanced = "reminder_advanced"
    case caregiverAlerts = "caregiver_alerts"
    case widget = "widget"
    case prescriptionAI = "prescription_ai"
    case multipleCaregivers = "multiple_caregivers"
    case multiplePatients = "multiple_patients"
    case alertCustomization = "alert_customization"
    case unlimitedHistory = "unlimited_history"
    case pdfExport = "pdf_export"
    case scheduledReports = "scheduled_reports"
    case calendarIntegration = "calendar_integration"
}

enum AnalyticsSurface: String, Sendable {
    case today
    case history
    case patientManagement = "patient_management"
    case notifications
    case settings
    case widgetSetup = "widget_setup"
    case calendar
    case prescriptionRegistration = "prescription_registration"
}

enum PurchaseAnalyticsResult: String, Sendable {
    case success
    case cancelled
    case pending
    case failed
    case notFound = "not_found"
}

enum PremiumActivationSource: String, Sendable {
    case purchase
    case restore
    case refresh
}

enum AnalyticsAppMode: String, Sendable {
    case caregiver
    case patient
}

enum AnalyticsAuthMethod: String, Sendable {
    case email
    case apple
    case google
}

enum AnalyticsFailureReason: String, Sendable {
    case invalidInput = "invalid_input"
    case weakPassword = "weak_password"
    case passwordMismatch = "password_mismatch"
    case duplicateAccount = "duplicate_account"
    case invalidCredentials = "invalid_credentials"
    case credentialConflict = "credential_conflict"
    case cancelled
    case notFound = "not_found"
    case network
    case server
    case unknown
}

enum AnalyticsScreen: String, Sendable {
    case modeSelect = "mode_select"
    case caregiverAuthChoice = "caregiver_auth_choice"
    case caregiverSignup = "caregiver_signup"
    case caregiverLogin = "caregiver_login"
    case patientLink = "patient_link"
}

enum AnalyticsAuthEvent: String, Sendable {
    case signupMethodSelected = "signup_method_selected"
    case signupStarted = "signup_started"
    case signupConfirmationRequired = "signup_confirmation_required"
    case signupCompleted = "signup_completed"
    case signupFailed = "signup_failed"
    case confirmationEmailResent = "confirmation_email_resent"
    case emailConfirmationCompleted = "email_confirmation_completed"
    case loginMethodSelected = "login_method_selected"
    case loginStarted = "login_started"
    case loginCompleted = "login_completed"
    case loginFailed = "login_failed"
}

enum AnalyticsCaregiverTab: String, Sendable {
    case today
    case medications
    case history
    case inventory
    case settings
}

enum AnalyticsPatientTab: String, Sendable {
    case today
    case history
    case settings
}

enum AnalyticsCoreAction: String, Sendable {
    case caregiverPatientCreated = "caregiver_patient_created"
    case linkCodeIssued = "link_code_issued"
    case medicationCreated = "medication_created"
    case doseRecorded = "dose_recorded"
}

/// Privacy-first analytics facade.
///
/// Only fixed enum values are accepted. Never add patient IDs, caregiver IDs,
/// medication data, dates, notification contents, free text, or auth tokens.
@MainActor
final class AnalyticsService: ObservableObject {
    static let shared = AnalyticsService()

    private static let preferenceKey = "analytics.collection.enabled"
    private static let consentDecisionKey = "analytics.collection.consentDecided"

    @Published private(set) var isEnabled: Bool
    @Published private(set) var hasConsentDecision: Bool
    private var isConfigured = false

    private init(defaults: UserDefaults = .standard) {
        isEnabled = defaults.bool(forKey: Self.preferenceKey)
        hasConsentDecision = defaults.bool(forKey: Self.consentDecisionKey)
            || defaults.object(forKey: Self.preferenceKey) != nil
    }

    func configure() {
        guard !isConfigured else { return }
        isConfigured = true

        if isSuppressedEnvironment {
            Analytics.setAnalyticsCollectionEnabled(false)
            return
        }

        Analytics.setUserID(nil)
        Analytics.setUserProperty("false", forName: AnalyticsUserPropertyAllowAdPersonalizationSignals)
        Analytics.setAnalyticsCollectionEnabled(isEnabled)
    }

    func setCollectionEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: Self.preferenceKey)
        UserDefaults.standard.set(true, forKey: Self.consentDecisionKey)
        isEnabled = enabled
        hasConsentDecision = true

        guard !isSuppressedEnvironment else {
            Analytics.setAnalyticsCollectionEnabled(false)
            return
        }

        Analytics.setAnalyticsCollectionEnabled(enabled)
        if !enabled {
            Analytics.resetAnalyticsData()
        }
    }

    func logFeatureInterest(_ feature: PremiumFeature, surface: AnalyticsSurface) {
        log("premium_feature_interest", feature: feature, surface: surface)
    }

    func logPremiumNeed(_ feature: PremiumFeature, surface: AnalyticsSurface) {
        log("premium_need_encountered", feature: feature, surface: surface)
    }

    func logPaywallViewed(feature: PremiumFeature, surface: AnalyticsSurface) {
        log("paywall_viewed", feature: feature, surface: surface)
    }

    func logPaywallDismissed(feature: PremiumFeature, surface: AnalyticsSurface) {
        log("paywall_dismissed", feature: feature, surface: surface)
    }

    func logPurchaseStarted(feature: PremiumFeature, surface: AnalyticsSurface) {
        log("purchase_started", feature: feature, surface: surface)
    }

    func logPurchaseResult(
        _ result: PurchaseAnalyticsResult,
        feature: PremiumFeature,
        surface: AnalyticsSurface
    ) {
        log(
            "purchase_result",
            parameters: [
                "result": result.rawValue,
                "feature": feature.rawValue,
                "surface": surface.rawValue
            ]
        )
    }

    func logPremiumActivated(source: PremiumActivationSource) {
        log("premium_activated", parameters: ["source": source.rawValue])
    }

    func logRestoreStarted(surface: AnalyticsSurface) {
        log("restore_started", parameters: ["surface": surface.rawValue])
    }

    func logRestoreResult(_ result: PurchaseAnalyticsResult, surface: AnalyticsSurface) {
        log(
            "restore_result",
            parameters: ["result": result.rawValue, "surface": surface.rawValue]
        )
    }

    func logScreenViewed(_ screen: AnalyticsScreen) {
        log("screen_viewed", parameters: ["screen_name": screen.rawValue])
    }

    func logModeSelected(_ mode: AnalyticsAppMode) {
        log("app_mode_selected", parameters: ["mode": mode.rawValue])
    }

    func logAuth(
        _ event: AnalyticsAuthEvent,
        method: AnalyticsAuthMethod,
        reason: AnalyticsFailureReason? = nil
    ) {
        var parameters = ["auth_method": method.rawValue]
        if let reason {
            parameters["reason"] = reason.rawValue
        }
        log(event.rawValue, parameters: parameters)
    }

    func logPatientLinkStarted() {
        log("patient_link_started", parameters: ["surface": AnalyticsSurface.patientManagement.rawValue])
    }

    func logPatientLinkCompleted() {
        log("patient_link_completed", parameters: ["surface": AnalyticsSurface.patientManagement.rawValue])
    }

    func logPatientLinkFailed(reason: AnalyticsFailureReason) {
        log(
            "patient_link_failed",
            parameters: [
                "surface": AnalyticsSurface.patientManagement.rawValue,
                "reason": reason.rawValue
            ]
        )
    }

    func logCaregiverTabViewed(_ tab: AnalyticsCaregiverTab) {
        log("caregiver_tab_viewed", parameters: ["tab_name": tab.rawValue])
    }

    func logPatientTabViewed(_ tab: AnalyticsPatientTab) {
        log("patient_tab_viewed", parameters: ["tab_name": tab.rawValue])
    }

    func logCoreActionCompleted(_ action: AnalyticsCoreAction) {
        log("core_action_completed", parameters: ["action_name": action.rawValue])
    }

    func logTutorialStarted(mode: AnalyticsAppMode) {
        log("tutorial_started", parameters: ["mode": mode.rawValue])
    }

    func logTutorialStepViewed(mode: AnalyticsAppMode, step: Int) {
        guard (1...20).contains(step) else { return }
        log("tutorial_step_viewed", parameters: ["mode": mode.rawValue, "step": String(step)])
    }

    func logTutorialFinished(mode: AnalyticsAppMode, skipped: Bool) {
        log(
            skipped ? "tutorial_skipped" : "tutorial_completed",
            parameters: ["mode": mode.rawValue]
        )
    }

    static func failureReason(for error: Error) -> AnalyticsFailureReason {
        guard let apiError = error as? APIError else { return .unknown }
        switch apiError {
        case .validation:
            return .invalidInput
        case .network:
            return .network
        case .notFound:
            return .notFound
        case .conflict:
            return .credentialConflict
        case .unauthorized, .forbidden:
            return .invalidCredentials
        case .unknown:
            return .unknown
        case .insufficientInventory, .patientLimitExceeded, .historyRetentionLimit:
            return .server
        }
    }

    #if DEBUG
    /// Explicit DebugView connectivity check. Never compiled into Release builds.
    func logDebugVerificationIfRequested() {
        guard ProcessInfo.processInfo.arguments.contains("-analyticsSmokeTest") else { return }
        log("analytics_debug_verification", parameters: ["source": "simulator"])
    }
    #endif

    private func log(_ name: String, feature: PremiumFeature, surface: AnalyticsSurface) {
        log(
            name,
            parameters: ["feature": feature.rawValue, "surface": surface.rawValue]
        )
    }

    private func log(_ name: String, parameters: [String: String]) {
        guard isConfigured, isEnabled, !isSuppressedEnvironment else { return }
        Analytics.logEvent(name, parameters: parameters)
    }

    private var isSuppressedEnvironment: Bool {
        let process = ProcessInfo.processInfo
        return process.environment["XCTestConfigurationFilePath"] != nil
            || process.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1"
            || process.arguments.contains("-disableAnalytics")
    }
}
