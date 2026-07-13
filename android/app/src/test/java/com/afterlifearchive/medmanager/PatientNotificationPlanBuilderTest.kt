package com.afterlifearchive.medmanager

import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PatientNotificationPlanBuilderTest {
    private val now = Instant.parse("2026-07-13T00:00:00Z") // 09:00 JST
    private val slotTimes = PatientSlotTimes("08:00", "13:00", "19:00", "22:00")

    @Test
    fun disabledMasterProducesNoPlan() {
        val plan = PatientNotificationPlanBuilder.build(listOf(day("2026-07-13")), slotTimes, PatientNotificationSettings(), now)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildsOnlyEnabledPendingFutureSlotsForSevenDays() {
        val settings = PatientNotificationSettings(masterEnabled = true, enabledSlots = setOf(MedicationSlot.NOON, MedicationSlot.EVENING))
        val plan = PatientNotificationPlanBuilder.build(
            listOf(
                day("2026-07-13", noon = HistoryStatus.PENDING, evening = HistoryStatus.TAKEN),
                day("2026-07-19", noon = HistoryStatus.PENDING),
                day("2026-07-20", noon = HistoryStatus.PENDING),
            ),
            slotTimes,
            settings,
            now,
        )

        assertEquals(2, plan.size)
        assertTrue(plan.all { it.slot == MedicationSlot.NOON && it.sequence == 1 })
        assertEquals("2026-07-13T04:00:00Z", plan.first().scheduledAt.toString())
    }

    @Test
    fun rereminderAddsExactlyFifteenMinuteSecondary() {
        val settings = PatientNotificationSettings(masterEnabled = true, rereminderEnabled = true, enabledSlots = setOf(MedicationSlot.NOON))
        val plan = PatientNotificationPlanBuilder.build(listOf(day("2026-07-13", noon = HistoryStatus.PENDING)), slotTimes, settings, now)

        assertEquals(listOf(1, 2), plan.map(PatientNotificationPlanEntry::sequence))
        assertEquals(15 * 60, plan[1].scheduledAt.epochSecond - plan[0].scheduledAt.epochSecond)
    }

    private fun day(
        date: String,
        morning: HistoryStatus = HistoryStatus.NONE,
        noon: HistoryStatus = HistoryStatus.NONE,
        evening: HistoryStatus = HistoryStatus.NONE,
        bedtime: HistoryStatus = HistoryStatus.NONE,
    ) = HistoryDay(date, morning, noon, evening, bedtime, 0)
}
