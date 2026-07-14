package com.afterlifearchive.medmanager.data.patient

import java.time.Instant
import java.time.LocalDate

enum class DoseStatus { PENDING, TAKEN, MISSED }
enum class RecordedByType { PATIENT, CAREGIVER }
enum class MedicationSlot { MORNING, NOON, EVENING, BEDTIME }

data class PatientSlotTimes(
    val morning: String,
    val noon: String,
    val evening: String,
    val bedtime: String,
) {
    fun resolve(scheduledAt: Instant): MedicationSlot {
        val localTime = scheduledAt.atZone(TOKYO).toLocalTime()
        entries().firstOrNull { (_, value) -> parseTime(value) == localTime }?.let { return it.first }
        return when (localTime.hour) {
            in 4..10 -> MedicationSlot.MORNING
            in 11..15 -> MedicationSlot.NOON
            in 16..20 -> MedicationSlot.EVENING
            else -> MedicationSlot.BEDTIME
        }
    }

    fun value(slot: MedicationSlot): String = when (slot) {
        MedicationSlot.MORNING -> morning
        MedicationSlot.NOON -> noon
        MedicationSlot.EVENING -> evening
        MedicationSlot.BEDTIME -> bedtime
    }

    private fun entries() = listOf(
        MedicationSlot.MORNING to morning,
        MedicationSlot.NOON to noon,
        MedicationSlot.EVENING to evening,
        MedicationSlot.BEDTIME to bedtime,
    )

    companion object {
        val DEFAULT = PatientSlotTimes("08:00", "13:00", "19:00", "22:00")
        private val TOKYO = java.time.ZoneId.of("Asia/Tokyo")
        private val TIME_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

        fun requireValid(value: String): String {
            require(TIME_PATTERN.matches(value)) { "Invalid slot time: $value" }
            return value
        }

        private fun parseTime(value: String) = java.time.LocalTime.parse(requireValid(value))
    }
}

data class PatientDose(
    val key: String,
    val medicationId: String,
    val scheduledAt: Instant,
    val status: DoseStatus,
    val medicationName: String,
    val dosageText: String,
    val doseCount: Double,
    val patientId: String = "",
    val recordedByType: RecordedByType? = null,
    val dosageStrengthValue: Double = 0.0,
    val dosageStrengthUnit: String = "",
    val notes: String? = null,
    val slot: MedicationSlot? = null,
)

data class PatientMedication(
    val id: String,
    val patientId: String,
    val name: String,
    val dosageText: String,
    val doseCountPerIntake: Double,
    val dosageStrengthValue: Double,
    val dosageStrengthUnit: String,
    val notes: String?,
    val isPrn: Boolean,
    val prnInstructions: String?,
    val startDate: Instant,
    val endDate: Instant?,
    val inventoryCount: Double?,
    val inventoryUnit: String?,
    val inventoryEnabled: Boolean,
    val inventoryQuantity: Double,
    val inventoryOut: Boolean,
    val isActive: Boolean,
    val isArchived: Boolean,
    val nextScheduledAt: Instant?,
    val regimenTimes: List<String>?,
    val regimenDaysOfWeek: List<String>?,
) {
    val isOutOfStock: Boolean get() = inventoryEnabled && inventoryOut
    val isInsufficientForDose: Boolean get() = inventoryEnabled && inventoryQuantity < doseCountPerIntake
}

data class SlotBulkRecordResult(
    val updatedCount: Int,
    val remainingCount: Int,
    val insufficientCount: Int,
    val totalPills: Double,
    val medCount: Int,
    val slotTime: String,
    val slotSummary: Map<MedicationSlot, HistoryStatus>,
    val recordingGroupId: String?,
)

enum class HistoryStatus { PENDING, TAKEN, MISSED, NONE }

data class HistoryDay(
    val date: String,
    val morning: HistoryStatus,
    val noon: HistoryStatus,
    val evening: HistoryStatus,
    val bedtime: HistoryStatus,
    val prnCount: Int,
)

data class HistoryScheduledDose(
    val medicationId: String,
    val medicationName: String,
    val dosageText: String,
    val doseCountPerIntake: Double,
    val scheduledAt: Instant,
    val slot: MedicationSlot,
    val status: DoseStatus,
    val recordedByType: RecordedByType?,
)

enum class PrnActorType { PATIENT, CAREGIVER }

data class PrnHistoryItem(
    val medicationId: String,
    val medicationName: String,
    val takenAt: Instant,
    val quantityTaken: Double,
    val actorType: PrnActorType,
)

data class HistoryDayDetail(
    val date: String,
    val doses: List<HistoryScheduledDose>,
    val prnItems: List<PrnHistoryItem>,
)

data class PatientNotificationTarget(val date: LocalDate, val slot: MedicationSlot)

sealed interface PatientUserMessage {
    data class Raw(val value: String) : PatientUserMessage
    data object InventoryInsufficient : PatientUserMessage
    data object DoseRecorded : PatientUserMessage
    data class SlotPartial(val updatedCount: Int, val insufficientCount: Int) : PatientUserMessage
    data class SlotRecorded(val updatedCount: Int) : PatientUserMessage
    data object NoRecordableMedication : PatientUserMessage
    data object PrnRecorded : PatientUserMessage
    data object PrnRecordFailed : PatientUserMessage
    data class Validation(val safeMessage: String?) : PatientUserMessage
    data object Unauthorized : PatientUserMessage
    data object Forbidden : PatientUserMessage
    data object NotFound : PatientUserMessage
    data class Conflict(val safeMessage: String?) : PatientUserMessage
    data object InsufficientInventory : PatientUserMessage
    data class PatientLimit(val limit: Int) : PatientUserMessage
    data object RateLimited : PatientUserMessage
    data object Network : PatientUserMessage
    data object Server : PatientUserMessage
}
