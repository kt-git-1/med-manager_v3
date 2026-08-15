package com.afterlifearchive.medmanager.data.patient

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationRecordingPolicyTest {
    @Test
    fun doseBecomesLateAtOneHourButRemainsRecordable() {
        val scheduledAt = Instant.parse("2026-07-22T03:30:00Z")
        val now = Instant.parse("2026-07-22T04:30:00Z")

        assertTrue(MedicationRecordingPolicy.isLate(scheduledAt, now))
        assertTrue(MedicationRecordingPolicy.isRecordable(scheduledAt, now))
    }

    @Test
    fun doseIsRecordableImmediatelyBeforeNextDayFourAmInTokyo() {
        val scheduledAt = Instant.parse("2026-07-22T14:00:00Z")
        val now = Instant.parse("2026-07-22T18:59:59Z")

        assertTrue(MedicationRecordingPolicy.isRecordable(scheduledAt, now))
        assertEquals(Instant.parse("2026-07-22T19:00:00Z"), MedicationRecordingPolicy.deadline(scheduledAt))
    }

    @Test
    fun doseIsNoLongerRecordableAtNextDayFourAmInTokyo() {
        val scheduledAt = Instant.parse("2026-07-22T14:00:00Z")

        assertFalse(MedicationRecordingPolicy.isRecordable(scheduledAt, Instant.parse("2026-07-22T19:00:00Z")))
    }

    @Test
    fun recordingWindowStillOpensThirtyMinutesBeforeSchedule() {
        val scheduledAt = Instant.parse("2026-07-22T03:30:00Z")

        assertFalse(MedicationRecordingPolicy.isRecordable(scheduledAt, Instant.parse("2026-07-22T02:59:59Z")))
        assertTrue(MedicationRecordingPolicy.isRecordable(scheduledAt, Instant.parse("2026-07-22T03:00:00Z")))
    }

    @Test
    fun delayIsClampedBeforeSchedule() {
        assertEquals(
            0L,
            MedicationRecordingPolicy.delaySeconds(
                Instant.parse("2026-07-22T03:30:00Z"),
                Instant.parse("2026-07-22T03:20:00Z"),
            ),
        )
    }
}
