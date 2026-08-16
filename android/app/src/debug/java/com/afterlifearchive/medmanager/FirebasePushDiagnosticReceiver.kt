package com.afterlifearchive.medmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Debug-only physical preflight for the Firebase Messaging installation/token handshake.
 *
 * The token value is never logged or returned. A successful request is immediately followed by
 * token deletion, auto-init disablement and app-owned token-state cleanup. The receiver is absent
 * from Release and its manifest entry requires the shell-only android.permission.DUMP permission.
 */
class FirebasePushDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VERIFY && intent.action != ACTION_CLEANUP) return
        val pendingResult = goAsync()
        val application = context.applicationContext as? MedicationApplication
        if (!FirebaseRuntime.ensureInitialized(context)) {
            application?.caregiverPushRepository?.clearAfterAccountDeletion()
            Log.i(TAG, RESULT_CONFIGURATION_MISSING)
            pendingResult.finish()
            return
        }

        val messaging = FirebaseMessaging.getInstance()
        if (intent.action == ACTION_CLEANUP) {
            cleanup(messaging, application, pendingResult)
            return
        }

        messaging.isAutoInitEnabled = true
        messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                Log.i(TAG, RESULT_TOKEN_READY)
            } else {
                Log.i(TAG, RESULT_TOKEN_FAILED)
            }
            cleanup(messaging, application, pendingResult)
        }
    }

    private fun cleanup(
        messaging: FirebaseMessaging,
        application: MedicationApplication?,
        pendingResult: PendingResult,
    ) {
        messaging.isAutoInitEnabled = false
        messaging.deleteToken().addOnCompleteListener { task ->
            application?.caregiverPushRepository?.clearAfterAccountDeletion()
            Log.i(TAG, if (task.isSuccessful) RESULT_CLEANUP_READY else RESULT_CLEANUP_FAILED)
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_VERIFY = "com.afterlifearchive.medmanager.debug.VERIFY_FCM_TOKEN"
        const val ACTION_CLEANUP = "com.afterlifearchive.medmanager.debug.CLEANUP_FCM_TOKEN"
        const val TAG = "MedManagerFcmDiagnostic"
        const val RESULT_CONFIGURATION_MISSING = "CONFIGURATION_MISSING"
        const val RESULT_TOKEN_READY = "TOKEN_READY"
        const val RESULT_TOKEN_FAILED = "TOKEN_FAILED"
        const val RESULT_CLEANUP_READY = "CLEANUP_READY"
        const val RESULT_CLEANUP_FAILED = "CLEANUP_FAILED"
    }
}
