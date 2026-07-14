package com.afterlifearchive.medmanager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.afterlifearchive.medmanager.data.push.CaregiverPushTokenSource
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseCaregiverPushTokenSource(private val context: Context) : CaregiverPushTokenSource {
    override val configured: Boolean = listOf(
        BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_PROJECT_ID,
        BuildConfig.FIREBASE_SENDER_ID,
    ).all(String::isNotBlank)

    private fun messaging(): FirebaseMessaging? {
        if (!configured) return null
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            ) ?: return null
        }
        return FirebaseMessaging.getInstance()
    }

    override fun setAutoInitEnabled(enabled: Boolean) {
        messaging()?.isAutoInitEnabled = enabled
    }

    override suspend fun token(): String {
        val messaging = messaging() ?: error("Firebase is not configured")
        return suspendCancellableCoroutine { continuation ->
            messaging.token.addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                val error = task.exception
                if (task.isSuccessful && task.result != null) continuation.resume(task.result)
                else continuation.resumeWithException(error ?: IllegalStateException("FCM token is unavailable"))
            }
        }
    }
}

class CaregiverFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? MedicationApplication)?.handleCaregiverPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as? MedicationApplication ?: return
        if (!app.caregiverPushRepository.acceptsMessages()) return
        val data = message.data
        if (data["type"] != "DOSE_TAKEN") return
        val patientId = data["patientId"]?.takeIf(String::isNotBlank) ?: return
        val date = data["date"]?.takeIf { DATE_PATTERN.matches(it) } ?: return
        val slot = data["slot"]?.takeIf { it in VALID_SLOTS } ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        createChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("type", "DOSE_TAKEN")
            putExtra("patientId", patientId)
            putExtra("date", date)
            putExtra("slot", slot)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            "$patientId:$date:$slot".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_caregiver_title))
            .setContentText(getString(R.string.notification_caregiver_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify("$patientId:$date:$slot".hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_caregiver), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = getString(R.string.notification_channel_caregiver_description)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "caregiver_updates"
        private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val VALID_SLOTS = setOf("morning", "noon", "evening", "bedtime")
    }
}
