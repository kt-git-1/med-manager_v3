package com.afterlifearchive.medmanager

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ReminderScheduler {
    const val CHANNEL_ID = "patient_reminders"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_patient_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notification_channel_patient_reminders_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, doseKey: String, delayMinutes: Long = 10) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            doseKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMinutes * 60_000,
            pending,
        )
    }

}

internal fun patientNotificationBodyResource(slot: String?): Int = when (slot?.lowercase()) {
    "morning" -> R.string.notification_patient_body_morning
    "noon" -> R.string.notification_patient_body_noon
    "evening" -> R.string.notification_patient_body_evening
    "bedtime" -> R.string.notification_patient_body_bedtime
    else -> R.string.notification_patient_body
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("notification_date", intent.getStringExtra("notification_date"))
            putExtra("notification_slot", intent.getStringExtra("notification_slot"))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            "${intent.getStringExtra("notification_date")}:${intent.getStringExtra("notification_slot")}".hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.notification_patient_title))
            .setContentText(context.getString(patientNotificationBodyResource(intent.getStringExtra("notification_slot"))))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(intent.hashCode(), notification)
    }
}
