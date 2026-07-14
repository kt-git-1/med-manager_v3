package com.afterlifearchive.medmanager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PatientNotificationSettings(
    val masterEnabled: Boolean = false,
    val rereminderEnabled: Boolean = false,
    val enabledSlots: Set<MedicationSlot> = MedicationSlot.entries.toSet(),
)

data class PatientNotificationPlanEntry(
    val date: LocalDate,
    val slot: MedicationSlot,
    val sequence: Int,
    val scheduledAt: Instant,
)

class PatientNotificationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("patient_notification_preferences", Context.MODE_PRIVATE)

    fun load() = PatientNotificationSettings(
        masterEnabled = preferences.getBoolean("master_enabled", false),
        rereminderEnabled = preferences.getBoolean("rereminder_enabled", false),
        enabledSlots = MedicationSlot.entries.filterTo(mutableSetOf()) { preferences.getBoolean("slot_${it.name.lowercase()}", true) },
    )

    fun save(settings: PatientNotificationSettings) {
        preferences.edit().apply {
            putBoolean("master_enabled", settings.masterEnabled)
            putBoolean("rereminder_enabled", settings.rereminderEnabled)
            MedicationSlot.entries.forEach { putBoolean("slot_${it.name.lowercase()}", it in settings.enabledSlots) }
        }.apply()
    }
}

object PatientNotificationPlanBuilder {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    fun build(
        days: List<HistoryDay>,
        slotTimes: PatientSlotTimes,
        settings: PatientNotificationSettings,
        now: Instant,
    ): List<PatientNotificationPlanEntry> {
        if (!settings.masterEnabled) return emptyList()
        val start = now.atZone(tokyo).toLocalDate()
        val allowedDates = (0 until 7).mapTo(mutableSetOf()) { start.plusDays(it.toLong()) }
        return buildList {
            days.forEach { day ->
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@forEach
                if (date !in allowedDates) return@forEach
                MedicationSlot.entries.forEach { slot ->
                    if (slot !in settings.enabledSlots || day.status(slot) != HistoryStatus.PENDING) return@forEach
                    val scheduled = date.atTime(LocalTime.parse(slotTimes.value(slot))).atZone(tokyo).toInstant()
                    if (scheduled > now) add(PatientNotificationPlanEntry(date, slot, 1, scheduled))
                    val secondary = scheduled.plusSeconds(15 * 60)
                    if (settings.rereminderEnabled && secondary > now) add(PatientNotificationPlanEntry(date, slot, 2, secondary))
                }
            }
        }.sortedBy(PatientNotificationPlanEntry::scheduledAt)
    }

    private fun HistoryDay.status(slot: MedicationSlot) = when (slot) {
        MedicationSlot.MORNING -> morning
        MedicationSlot.NOON -> noon
        MedicationSlot.EVENING -> evening
        MedicationSlot.BEDTIME -> bedtime
    }
}

object PatientNotificationScheduler {
    private const val ACTION = "com.afterlifearchive.medmanager.PATIENT_SLOT_REMINDER"

    fun replace(context: Context, entries: List<PatientNotificationPlanEntry>) {
        cancelAll(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        entries.forEach { entry ->
            val pending = pendingIntent(context, entry)
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.scheduledAt.toEpochMilli(), pending)
        }
        context.getSharedPreferences("patient_notification_plan", Context.MODE_PRIVATE).edit()
            .putStringSet("request_codes", entries.mapTo(mutableSetOf()) { requestCode(it).toString() }).apply()
    }

    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences("patient_notification_plan", Context.MODE_PRIVATE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        prefs.getStringSet("request_codes", emptySet()).orEmpty().forEach { code ->
            val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
            PendingIntent.getBroadcast(context, code.toInt(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let(alarm::cancel)
        }
        prefs.edit().clear().apply()
    }

    private fun pendingIntent(context: Context, entry: PatientNotificationPlanEntry): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
            .putExtra("notification_slot", entry.slot.name.lowercase())
            .putExtra("notification_date", entry.date.toString())
            .putExtra("notification_sequence", entry.sequence)
        return PendingIntent.getBroadcast(context, requestCode(entry), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun requestCode(entry: PatientNotificationPlanEntry) = "${entry.date}:${entry.slot}:${entry.sequence}".hashCode()
}

class PatientReminderMaintenanceCoordinator(
    private val isPatientSessionActive: () -> Boolean,
    private val settingsProvider: () -> PatientNotificationSettings,
    private val historyProvider: suspend () -> Pair<List<HistoryDay>, PatientSlotTimes>,
    private val replacePlan: (List<PatientNotificationPlanEntry>) -> Unit,
    private val nowProvider: () -> Instant = Instant::now,
    private val onSuccess: () -> Unit = {},
    private val onFailure: (Throwable) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun rebuildIfEnabled() = mutex.withLock {
        if (!isPatientSessionActive()) return@withLock
        val settings = settingsProvider()
        if (!settings.masterEnabled) return@withLock

        runCatching {
            val (days, slotTimes) = historyProvider()
            replacePlan(PatientNotificationPlanBuilder.build(days, slotTimes, settings, nowProvider()))
        }.onSuccess {
            onSuccess()
        }.onFailure(onFailure)
    }
}
