package com.afterlifearchive.medmanager

import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PatientReminderMaintenanceCoordinatorTest {
    private val now = Instant.parse("2026-07-13T00:00:00Z")
    private val settings = PatientNotificationSettings(
        masterEnabled = true,
        enabledSlots = setOf(MedicationSlot.NOON),
    )
    private val history = listOf(
        HistoryDay(
            date = "2026-07-13",
            morning = HistoryStatus.NONE,
            noon = HistoryStatus.PENDING,
            evening = HistoryStatus.NONE,
            bedtime = HistoryStatus.NONE,
            prnCount = 0,
        ),
    )

    @Test
    fun activeEnabledSessionReplacesPlanAndClearsPriorWarning() = runTest {
        var replacement: List<PatientNotificationPlanEntry>? = null
        var success = false
        var failed = false
        val coordinator = coordinator(
            replacePlan = { replacement = it },
            onSuccess = { success = true },
            onFailure = { failed = true },
        )

        coordinator.rebuildIfEnabled()

        assertEquals(1, replacement?.size)
        assertTrue(success)
        assertFalse(failed)
    }

    @Test
    fun disabledNotificationsDoNotFetchOrReplace() = runTest {
        var fetched = false
        var replaced = false
        val coordinator = PatientReminderMaintenanceCoordinator(
            isPatientSessionActive = { true },
            settingsProvider = { settings.copy(masterEnabled = false) },
            historyProvider = { fetched = true; history to PatientSlotTimes.DEFAULT },
            replacePlan = { replaced = true },
            onFailure = { error("unexpected") },
        )

        coordinator.rebuildIfEnabled()

        assertFalse(fetched)
        assertFalse(replaced)
    }

    @Test
    fun maintenanceFailureIsReportedWithoutThrowing() = runTest {
        var reported: Throwable? = null
        var replaced = false
        val coordinator = PatientReminderMaintenanceCoordinator(
            isPatientSessionActive = { true },
            settingsProvider = { settings },
            historyProvider = { error("history unavailable") },
            replacePlan = { replaced = true },
            nowProvider = { now },
            onFailure = { reported = it },
        )

        coordinator.rebuildIfEnabled()

        assertEquals("history unavailable", reported?.message)
        assertFalse(replaced)
    }

    private fun coordinator(
        replacePlan: (List<PatientNotificationPlanEntry>) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) = PatientReminderMaintenanceCoordinator(
        isPatientSessionActive = { true },
        settingsProvider = { settings },
        historyProvider = { history to PatientSlotTimes("08:00", "13:00", "19:00", "22:00") },
        replacePlan = replacePlan,
        nowProvider = { now },
        onSuccess = onSuccess,
        onFailure = onFailure,
    )
}
