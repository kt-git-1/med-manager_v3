package com.afterlifearchive.medmanager.ui

import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientNotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PatientTabRoutingTest {
    @Test
    fun localReminderAlwaysRoutesPatientToTodayAndExactSlot() {
        val target = PatientNotificationTarget(
            date = LocalDate.parse("2026-07-01"),
            slot = MedicationSlot.BEDTIME,
        )

        val route = patientRouteFor(target)

        assertEquals(PatientTab.TODAY, route.tab)
        assertEquals(MedicationSlot.BEDTIME, route.highlightedSlot)
    }
}
