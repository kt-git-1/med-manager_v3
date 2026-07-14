package com.afterlifearchive.medmanager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    override val configured: Boolean get() = FirebaseRuntime.configured

    private fun messaging(): FirebaseMessaging? {
        if (!FirebaseRuntime.ensureInitialized(context)) return null
        return FirebaseMessaging.getInstance()
    }

    override fun setAutoInitEnabled(enabled: Boolean) {
        if (enabled) {
            messaging()?.isAutoInitEnabled = true
        } else if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseMessaging.getInstance().isAutoInitEnabled = false
        }
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

object FirebaseRuntime {
    val configured: Boolean get() = listOf(
        BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_PROJECT_ID,
        BuildConfig.FIREBASE_SENDER_ID,
    ).all(String::isNotBlank)

    fun ensureInitialized(context: Context): Boolean {
        if (!configured) return false
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        return runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
            true
        }.getOrDefault(false)
    }
}

class CaregiverFirebaseMessagingService : FirebaseMessagingService() {
    private val deduplicator by lazy {
        PushMessageDeduplicator(AndroidPushMessageIdStorage(getSharedPreferences("caregiver_push_messages", MODE_PRIVATE)))
    }

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
        if (!deduplicator.shouldDisplay(message.messageId)) return
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

interface PushMessageIdStorage {
    fun read(): List<String>
    fun write(ids: List<String>)
}

class AndroidPushMessageIdStorage(private val preferences: SharedPreferences) : PushMessageIdStorage {
    override fun read(): List<String> = preferences.getString("recent_ids", null)
        ?.split('\n')
        ?.filter(String::isNotBlank)
        .orEmpty()

    override fun write(ids: List<String>) {
        preferences.edit().putString("recent_ids", ids.joinToString("\n")).apply()
    }
}

class PushMessageDeduplicator(
    private val storage: PushMessageIdStorage,
    private val capacity: Int = 100,
) {
    @Synchronized
    fun shouldDisplay(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return true
        val current = storage.read()
        if (messageId in current) return false
        storage.write((current + messageId).takeLast(capacity))
        return true
    }
}
