package com.afterlifearchive.medmanager.data.patient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PatientTodayNextSlotSelectorTest {
    private val now = Instant.parse("2026-06-08T10:24:00Z")

    @Test
    fun currentWindowWinsOverPastClosedSlot() {
        val selected = PatientTodayNextSlotSelector.select(
            listOf(
                candidate(MedicationSlot.NOON, "2026-06-08T04:00:00Z", withinWindow = false),
                candidate(MedicationSlot.EVENING, "2026-06-08T10:00:00Z", withinWindow = true),
            ),
            now,
        )

        assertEquals(MedicationSlot.EVENING, selected)
    }

    @Test
    fun skipsPastClosedSlotAndSelectsFutureSlot() {
        val selected = PatientTodayNextSlotSelector.select(
            listOf(
                candidate(MedicationSlot.NOON, "2026-06-08T04:00:00Z", withinWindow = false),
                candidate(MedicationSlot.BEDTIME, "2026-06-08T13:00:00Z", withinWindow = false),
            ),
            now,
        )

        assertEquals(MedicationSlot.BEDTIME, selected)
    }

    @Test
    fun skipsSlotWithoutRecordableInventory() {
        val selected = PatientTodayNextSlotSelector.select(
            listOf(
                candidate(MedicationSlot.NOON, "2026-06-08T11:00:00Z", inventory = false),
                candidate(MedicationSlot.EVENING, "2026-06-08T12:00:00Z"),
            ),
            now,
        )

        assertEquals(MedicationSlot.EVENING, selected)
    }

    @Test
    fun returnsNullWhenNothingRemainingOrRecordable() {
        val selected = PatientTodayNextSlotSelector.select(
            listOf(
                candidate(MedicationSlot.NOON, "2026-06-08T11:00:00Z", remaining = 0),
                candidate(MedicationSlot.EVENING, "2026-06-08T12:00:00Z", inventory = false),
            ),
            now,
        )

        assertNull(selected)
    }

    private fun candidate(
        slot: MedicationSlot,
        at: String,
        remaining: Int = 1,
        withinWindow: Boolean = false,
        inventory: Boolean = true,
    ) = NextSlotCandidate(slot, Instant.parse(at), remaining, withinWindow, inventory)
}
