package com.afterlifearchive.medmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-only emitter for the three privacy-reviewed iOS parity events.
 *
 * The receiver is absent from Release, requires the shell-only DUMP permission and refuses to
 * change consent. It emits only fixed enum values after the user has already enabled Analytics.
 */
class AnalyticsDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "${context.packageName}.debug.EMIT_ANALYTICS_PARITY") return
        val analytics = (context.applicationContext as? MedicationApplication)?.analyticsService
        if (analytics == null) {
            Log.i(TAG, RESULT_UNAVAILABLE)
            return
        }
        val consent = analytics.state.value
        if (!consent.decided || !consent.enabled) {
            Log.i(TAG, RESULT_CONSENT_OFF)
            return
        }

        analytics.logCoreActionFailed(
            AnalyticsCoreAction.DOSE_RECORDED,
            AnalyticsFailureReason.SERVER,
        )
        analytics.logPatientLinkCodeShareTapped()
        analytics.logNotificationPermissionResult(
            AnalyticsNotificationPermissionResult.AUTHORIZED,
            AnalyticsSurface.NOTIFICATIONS,
        )
        Log.i(TAG, RESULT_EMITTED)
    }

    companion object {
        const val TAG = "MedManagerAnalyticsDiagnostic"
        const val RESULT_UNAVAILABLE = "ANALYTICS_UNAVAILABLE"
        const val RESULT_CONSENT_OFF = "CONSENT_OFF"
        const val RESULT_EMITTED = "PARITY_EVENTS_EMITTED"
    }
}
