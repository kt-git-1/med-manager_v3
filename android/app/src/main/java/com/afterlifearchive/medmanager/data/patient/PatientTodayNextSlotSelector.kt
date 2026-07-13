package com.afterlifearchive.medmanager.data.patient

import java.time.Instant

data class NextSlotCandidate(
    val slot: MedicationSlot,
    val scheduledAt: Instant,
    val remainingCount: Int,
    val isWithinRecordingWindow: Boolean,
    val hasRecordableInventory: Boolean,
)

object PatientTodayNextSlotSelector {
    fun select(candidates: List<NextSlotCandidate>, now: Instant = Instant.now()): MedicationSlot? =
        candidates.asSequence()
            .filter { candidate ->
                candidate.remainingCount > 0 &&
                    candidate.hasRecordableInventory &&
                    (candidate.isWithinRecordingWindow || !candidate.scheduledAt.isBefore(now))
            }
            .minByOrNull(NextSlotCandidate::scheduledAt)
            ?.slot
}
