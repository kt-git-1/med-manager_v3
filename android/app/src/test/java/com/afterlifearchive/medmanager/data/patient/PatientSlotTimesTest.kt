package com.afterlifearchive.medmanager.data.patient

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PatientSlotTimesTest {
    private val custom = PatientSlotTimes("07:30", "12:15", "18:45", "21:30")

    @Test
    fun exactCustomTimesResolveToConfiguredSlotsInTokyo() {
        assertEquals(MedicationSlot.MORNING, custom.resolve(Instant.parse("2026-07-12T22:30:00Z")))
        assertEquals(MedicationSlot.NOON, custom.resolve(Instant.parse("2026-07-13T03:15:00Z")))
        assertEquals(MedicationSlot.EVENING, custom.resolve(Instant.parse("2026-07-13T09:45:00Z")))
        assertEquals(MedicationSlot.BEDTIME, custom.resolve(Instant.parse("2026-07-13T12:30:00Z")))
    }

    @Test
    fun nonExactTimesUseCanonicalIosFallbackRanges() {
        assertEquals(MedicationSlot.MORNING, custom.resolve(Instant.parse("2026-07-12T20:00:00Z")))
        assertEquals(MedicationSlot.NOON, custom.resolve(Instant.parse("2026-07-13T06:00:00Z")))
        assertEquals(MedicationSlot.EVENING, custom.resolve(Instant.parse("2026-07-13T11:00:00Z")))
        assertEquals(MedicationSlot.BEDTIME, custom.resolve(Instant.parse("2026-07-13T16:00:00Z")))
    }
}
