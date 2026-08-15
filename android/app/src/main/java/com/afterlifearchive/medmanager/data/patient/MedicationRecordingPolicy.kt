package com.afterlifearchive.medmanager.data.patient

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object MedicationRecordingPolicy {
    const val OPENS_BEFORE_SCHEDULED_SECONDS = 30L * 60L
    const val LATE_THRESHOLD_SECONDS = 60L * 60L
    private const val NEXT_DAY_DEADLINE_HOUR = 4L
    private val TOKYO = ZoneId.of("Asia/Tokyo")

    fun deadline(scheduledAt: Instant): Instant = scheduledAt
        .atZone(TOKYO)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(TOKYO)
        .plusHours(NEXT_DAY_DEADLINE_HOUR)
        .toInstant()

    fun isRecordable(scheduledAt: Instant, now: Instant): Boolean =
        !now.isBefore(scheduledAt.minusSeconds(OPENS_BEFORE_SCHEDULED_SECONDS)) &&
            now.isBefore(deadline(scheduledAt))

    fun isLate(scheduledAt: Instant, takenAt: Instant): Boolean =
        !takenAt.isBefore(scheduledAt) && delaySeconds(scheduledAt, takenAt) >= LATE_THRESHOLD_SECONDS

    fun delaySeconds(scheduledAt: Instant, takenAt: Instant): Long =
        Duration.between(scheduledAt, takenAt).seconds.coerceAtLeast(0L)
}
