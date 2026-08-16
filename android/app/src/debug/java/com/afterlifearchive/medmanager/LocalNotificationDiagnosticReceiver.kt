package com.afterlifearchive.medmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal const val MIN_DIAGNOSTIC_DELAY_SECONDS = 15L
internal const val MAX_DIAGNOSTIC_DELAY_SECONDS = 600L

internal fun diagnosticDelaySeconds(raw: Long): Long? =
    raw.takeIf { it in MIN_DIAGNOSTIC_DELAY_SECONDS..MAX_DIAGNOSTIC_DELAY_SECONDS }

internal fun diagnosticMedicationSlot(raw: String?): MedicationSlot? =
    runCatching { MedicationSlot.valueOf(raw.orEmpty().uppercase(Locale.ROOT)) }.getOrNull()

internal fun diagnosticNotificationSequence(raw: Int): Int? = raw.takeIf { it == 1 || it == 2 }

class LocalNotificationDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CANCEL -> PatientNotificationScheduler.cancelAll(context)
            ACTION_SCHEDULE -> {
                val delaySeconds = diagnosticDelaySeconds(intent.getLongExtra(EXTRA_DELAY_SECONDS, -1)) ?: return
                val slot = diagnosticMedicationSlot(intent.getStringExtra(EXTRA_SLOT)) ?: return
                val sequence = diagnosticNotificationSequence(intent.getIntExtra(EXTRA_SEQUENCE, 1)) ?: return
                val now = Instant.now()
                val date = now.atZone(TOKYO).toLocalDate()
                ReminderScheduler.createNotificationChannel(context)
                PatientNotificationScheduler.replace(
                    context,
                    listOf(PatientNotificationPlanEntry(date, slot, sequence, now.plusSeconds(delaySeconds))),
                )
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE = "com.afterlifearchive.medmanager.debug.SCHEDULE_PATIENT_REMINDER"
        const val ACTION_CANCEL = "com.afterlifearchive.medmanager.debug.CANCEL_PATIENT_REMINDERS"
        const val EXTRA_DELAY_SECONDS = "delay_seconds"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_SEQUENCE = "sequence"
        private val TOKYO = ZoneId.of("Asia/Tokyo")
    }
}
