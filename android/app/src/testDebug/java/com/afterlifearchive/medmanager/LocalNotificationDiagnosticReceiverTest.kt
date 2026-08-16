package com.afterlifearchive.medmanager

import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalNotificationDiagnosticReceiverTest {
    @Test
    fun delayIsStrictlyBounded() {
        assertNull(diagnosticDelaySeconds(MIN_DIAGNOSTIC_DELAY_SECONDS - 1))
        assertEquals(MIN_DIAGNOSTIC_DELAY_SECONDS, diagnosticDelaySeconds(MIN_DIAGNOSTIC_DELAY_SECONDS))
        assertEquals(MAX_DIAGNOSTIC_DELAY_SECONDS, diagnosticDelaySeconds(MAX_DIAGNOSTIC_DELAY_SECONDS))
        assertNull(diagnosticDelaySeconds(MAX_DIAGNOSTIC_DELAY_SECONDS + 1))
    }

    @Test
    fun slotAcceptsOnlyCanonicalMedicationSlots() {
        assertEquals(MedicationSlot.MORNING, diagnosticMedicationSlot("morning"))
        assertEquals(MedicationSlot.NOON, diagnosticMedicationSlot("NOON"))
        assertEquals(MedicationSlot.EVENING, diagnosticMedicationSlot("evening"))
        assertEquals(MedicationSlot.BEDTIME, diagnosticMedicationSlot("bedtime"))
        assertNull(diagnosticMedicationSlot(null))
        assertNull(diagnosticMedicationSlot("medication-name"))
    }

    @Test
    fun sequenceAcceptsOnlyPrimaryAndRereminder() {
        assertEquals(1, diagnosticNotificationSequence(1))
        assertEquals(2, diagnosticNotificationSequence(2))
        assertNull(diagnosticNotificationSequence(0))
        assertNull(diagnosticNotificationSequence(3))
    }
}
