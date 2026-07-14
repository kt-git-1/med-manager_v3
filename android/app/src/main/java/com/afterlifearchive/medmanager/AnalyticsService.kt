package com.afterlifearchive.medmanager

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnalyticsConsentState(val enabled: Boolean = false, val decided: Boolean = false)

interface AnalyticsConsentStore {
    fun state(): AnalyticsConsentState
    fun save(enabled: Boolean)
}

class AnalyticsConsentPreferences(context: Context) : AnalyticsConsentStore {
    private val preferences = context.getSharedPreferences("analytics_consent", Context.MODE_PRIVATE)
    override fun state() = AnalyticsConsentState(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        decided = preferences.getBoolean(KEY_DECIDED, preferences.contains(KEY_ENABLED)),
    )
    override fun save(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).putBoolean(KEY_DECIDED, true).apply()
    }
    fun isEnabled(): Boolean = state().enabled
    fun setEnabled(enabled: Boolean) = save(enabled)

    private companion object {
        const val KEY_ENABLED = "collection_enabled"
        const val KEY_DECIDED = "consent_decided"
    }
}

interface AnalyticsTransport {
    fun setCollectionEnabled(enabled: Boolean)
    fun reset()
    fun log(name: String, parameters: Map<String, String>)
}

class FirebaseAnalyticsTransport(private val context: Context) : AnalyticsTransport {
    private fun existing(): FirebaseAnalytics? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)

    override fun setCollectionEnabled(enabled: Boolean) {
        val analytics = if (enabled) {
            if (!FirebaseRuntime.ensureInitialized(context)) return
            FirebaseAnalytics.getInstance(context)
        } else existing() ?: return
        analytics.setUserId(null)
        analytics.setUserProperty(FirebaseAnalytics.UserProperty.ALLOW_AD_PERSONALIZATION_SIGNALS, "false")
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    override fun reset() { existing()?.resetAnalyticsData() }

    override fun log(name: String, parameters: Map<String, String>) {
        val analytics = existing() ?: return
        val bundle = Bundle().apply { parameters.forEach { (key, value) -> putString(key, value) } }
        analytics.logEvent(name, bundle)
    }
}

enum class AnalyticsAppMode(val value: String) { CAREGIVER("caregiver"), PATIENT("patient") }
enum class AnalyticsCaregiverTab(val value: String) { TODAY("today"), MEDICATIONS("medications"), HISTORY("history"), INVENTORY("inventory"), SETTINGS("settings") }
enum class AnalyticsPatientTab(val value: String) { TODAY("today"), HISTORY("history"), SETTINGS("settings") }
enum class AnalyticsScreen(val value: String) { MODE_SELECT("mode_select"), CAREGIVER_AUTH_CHOICE("caregiver_auth_choice"), CAREGIVER_SIGNUP("caregiver_signup"), CAREGIVER_LOGIN("caregiver_login"), PATIENT_LINK("patient_link") }
enum class AnalyticsSurface(val value: String) { TODAY("today"), HISTORY("history"), PATIENT_MANAGEMENT("patient_management"), NOTIFICATIONS("notifications"), SETTINGS("settings"), WIDGET_SETUP("widget_setup"), CALENDAR("calendar"), PRESCRIPTION_REGISTRATION("prescription_registration") }
enum class PremiumFeature(val value: String) { REMINDER_ADVANCED("reminder_advanced"), CAREGIVER_ALERTS("caregiver_alerts"), WIDGET("widget"), PRESCRIPTION_AI("prescription_ai"), MULTIPLE_CAREGIVERS("multiple_caregivers"), MULTIPLE_PATIENTS("multiple_patients"), ALERT_CUSTOMIZATION("alert_customization"), UNLIMITED_HISTORY("unlimited_history"), PDF_EXPORT("pdf_export"), SCHEDULED_REPORTS("scheduled_reports"), CALENDAR_INTEGRATION("calendar_integration") }
enum class AnalyticsCoreAction(val value: String) { CAREGIVER_PATIENT_CREATED("caregiver_patient_created"), LINK_CODE_ISSUED("link_code_issued"), MEDICATION_CREATED("medication_created"), DOSE_RECORDED("dose_recorded") }
enum class PurchaseAnalyticsResult(val value: String) { SUCCESS("success"), CANCELLED("cancelled"), PENDING("pending"), FAILED("failed"), NOT_FOUND("not_found") }
enum class PremiumActivationSource(val value: String) { PURCHASE("purchase"), RESTORE("restore"), REFRESH("refresh") }
enum class AnalyticsAuthMethod(val value: String) { EMAIL("email"), APPLE("apple"), GOOGLE("google") }
enum class AnalyticsFailureReason(val value: String) { INVALID_INPUT("invalid_input"), WEAK_PASSWORD("weak_password"), PASSWORD_MISMATCH("password_mismatch"), DUPLICATE_ACCOUNT("duplicate_account"), INVALID_CREDENTIALS("invalid_credentials"), CREDENTIAL_CONFLICT("credential_conflict"), CANCELLED("cancelled"), NOT_FOUND("not_found"), NETWORK("network"), SERVER("server"), UNKNOWN("unknown") }
enum class AnalyticsAuthEvent(val value: String) { SIGNUP_METHOD_SELECTED("signup_method_selected"), SIGNUP_STARTED("signup_started"), SIGNUP_CONFIRMATION_REQUIRED("signup_confirmation_required"), SIGNUP_COMPLETED("signup_completed"), SIGNUP_FAILED("signup_failed"), CONFIRMATION_EMAIL_RESENT("confirmation_email_resent"), EMAIL_CONFIRMATION_COMPLETED("email_confirmation_completed"), LOGIN_METHOD_SELECTED("login_method_selected"), LOGIN_STARTED("login_started"), LOGIN_COMPLETED("login_completed"), LOGIN_FAILED("login_failed") }

class AnalyticsService(
    private val preferences: AnalyticsConsentStore,
    private val transport: AnalyticsTransport,
    private val environmentSuppressed: () -> Boolean = { false },
) {
    private val mutableState = MutableStateFlow(preferences.state())
    val state: StateFlow<AnalyticsConsentState> = mutableState.asStateFlow()
    private var configured = false
    private var sessionSuppressed = false

    fun configure() {
        if (configured) return
        configured = true
        transport.setCollectionEnabled(mutableState.value.enabled && !suppressed())
    }

    fun setSessionSuppressed(suppressed: Boolean) {
        sessionSuppressed = suppressed
        if (configured && suppressed) transport.setCollectionEnabled(false)
    }

    fun setCollectionEnabled(enabled: Boolean) {
        preferences.save(enabled)
        mutableState.value = AnalyticsConsentState(enabled, decided = true)
        val effective = enabled && !suppressed()
        transport.setCollectionEnabled(effective)
        if (!enabled) transport.reset()
    }

    fun logScreenViewed(screen: AnalyticsScreen) = log("screen_viewed", mapOf("screen_name" to screen.value))
    fun logModeSelected(mode: AnalyticsAppMode) = log("app_mode_selected", mapOf("mode" to mode.value))
    fun logCaregiverTabViewed(tab: AnalyticsCaregiverTab) = log("caregiver_tab_viewed", mapOf("tab_name" to tab.value))
    fun logPatientTabViewed(tab: AnalyticsPatientTab) = log("patient_tab_viewed", mapOf("tab_name" to tab.value))
    fun logTutorialStarted(mode: AnalyticsAppMode) = log("tutorial_started", mapOf("mode" to mode.value))
    fun logTutorialStepViewed(mode: AnalyticsAppMode, step: Int) {
        if (step in 1..20) log("tutorial_step_viewed", mapOf("mode" to mode.value, "step" to step.toString()))
    }
    fun logTutorialFinished(mode: AnalyticsAppMode, skipped: Boolean) =
        log(if (skipped) "tutorial_skipped" else "tutorial_completed", mapOf("mode" to mode.value))
    fun logCoreActionCompleted(action: AnalyticsCoreAction) = log("core_action_completed", mapOf("action_name" to action.value))
    fun logFeatureInterest(feature: PremiumFeature, surface: AnalyticsSurface) =
        log("premium_feature_interest", mapOf("feature" to feature.value, "surface" to surface.value))
    fun logPremiumNeed(feature: PremiumFeature, surface: AnalyticsSurface) =
        log("premium_need_encountered", mapOf("feature" to feature.value, "surface" to surface.value))
    fun logPaywallViewed(feature: PremiumFeature, surface: AnalyticsSurface) = log("paywall_viewed", featureSurface(feature, surface))
    fun logPaywallDismissed(feature: PremiumFeature, surface: AnalyticsSurface) = log("paywall_dismissed", featureSurface(feature, surface))
    fun logPurchaseStarted(feature: PremiumFeature, surface: AnalyticsSurface) = log("purchase_started", featureSurface(feature, surface))
    fun logPurchaseResult(result: PurchaseAnalyticsResult, feature: PremiumFeature, surface: AnalyticsSurface) =
        log("purchase_result", featureSurface(feature, surface) + ("result" to result.value))
    fun logPremiumActivated(source: PremiumActivationSource) = log("premium_activated", mapOf("source" to source.value))
    fun logRestoreStarted(surface: AnalyticsSurface) = log("restore_started", mapOf("surface" to surface.value))
    fun logRestoreResult(result: PurchaseAnalyticsResult, surface: AnalyticsSurface) =
        log("restore_result", mapOf("result" to result.value, "surface" to surface.value))
    fun logAuth(event: AnalyticsAuthEvent, method: AnalyticsAuthMethod, reason: AnalyticsFailureReason? = null) =
        log(event.value, mapOf("auth_method" to method.value) + (reason?.let { mapOf("reason" to it.value) } ?: emptyMap()))
    fun logPatientLinkStarted() = log("patient_link_started", mapOf("surface" to AnalyticsSurface.PATIENT_MANAGEMENT.value))
    fun logPatientLinkCompleted() = log("patient_link_completed", mapOf("surface" to AnalyticsSurface.PATIENT_MANAGEMENT.value))
    fun logPatientLinkFailed(reason: AnalyticsFailureReason) = log("patient_link_failed", mapOf("surface" to AnalyticsSurface.PATIENT_MANAGEMENT.value, "reason" to reason.value))

    internal fun logChecked(name: String, parameters: Map<String, String>) = log(name, parameters)

    private fun log(name: String, parameters: Map<String, String>) {
        if (!configured || !mutableState.value.enabled || suppressed()) return
        val checked = AnalyticsEventSchema.accept(name, parameters) ?: return
        transport.log(name, checked)
    }

    private fun suppressed() = sessionSuppressed || environmentSuppressed()
    private fun featureSurface(feature: PremiumFeature, surface: AnalyticsSurface) =
        mapOf("feature" to feature.value, "surface" to surface.value)
}

internal object AnalyticsEventSchema {
    private val exactKeys = mapOf(
        "screen_viewed" to setOf("screen_name"),
        "app_mode_selected" to setOf("mode"),
        "caregiver_tab_viewed" to setOf("tab_name"),
        "patient_tab_viewed" to setOf("tab_name"),
        "tutorial_started" to setOf("mode"),
        "tutorial_step_viewed" to setOf("mode", "step"),
        "tutorial_completed" to setOf("mode"),
        "tutorial_skipped" to setOf("mode"),
        "core_action_completed" to setOf("action_name"),
        "premium_feature_interest" to setOf("feature", "surface"),
        "premium_need_encountered" to setOf("feature", "surface"),
        "paywall_viewed" to setOf("feature", "surface"),
        "paywall_dismissed" to setOf("feature", "surface"),
        "purchase_started" to setOf("feature", "surface"),
        "purchase_result" to setOf("feature", "surface", "result"),
        "premium_activated" to setOf("source"),
        "restore_started" to setOf("surface"),
        "restore_result" to setOf("result", "surface"),
        "signup_method_selected" to setOf("auth_method"),
        "signup_started" to setOf("auth_method"),
        "signup_confirmation_required" to setOf("auth_method"),
        "signup_completed" to setOf("auth_method"),
        "signup_failed" to setOf("auth_method", "reason"),
        "confirmation_email_resent" to setOf("auth_method"),
        "email_confirmation_completed" to setOf("auth_method"),
        "login_method_selected" to setOf("auth_method"),
        "login_started" to setOf("auth_method"),
        "login_completed" to setOf("auth_method"),
        "login_failed" to setOf("auth_method", "reason"),
        "patient_link_started" to setOf("surface"),
        "patient_link_completed" to setOf("surface"),
        "patient_link_failed" to setOf("surface", "reason"),
    )
    private val values = mapOf(
        "screen_name" to AnalyticsScreen.entries.map { it.value }.toSet(),
        "mode" to AnalyticsAppMode.entries.map { it.value }.toSet(),
        "tab_name" to (AnalyticsCaregiverTab.entries.map { it.value } + AnalyticsPatientTab.entries.map { it.value }).toSet(),
        "action_name" to AnalyticsCoreAction.entries.map { it.value }.toSet(),
        "feature" to PremiumFeature.entries.map { it.value }.toSet(),
        "surface" to AnalyticsSurface.entries.map { it.value }.toSet(),
        "result" to PurchaseAnalyticsResult.entries.map { it.value }.toSet(),
        "source" to PremiumActivationSource.entries.map { it.value }.toSet(),
        "auth_method" to AnalyticsAuthMethod.entries.map { it.value }.toSet(),
        "reason" to AnalyticsFailureReason.entries.map { it.value }.toSet(),
    )

    fun accept(name: String, parameters: Map<String, String>): Map<String, String>? {
        if (parameters.keys != exactKeys[name]) return null
        if (parameters.any { (key, value) ->
                if (key == "step") value.toIntOrNull()?.let { it in 1..20 } != true else value !in values.getValue(key)
            }) return null
        return parameters
    }
}
