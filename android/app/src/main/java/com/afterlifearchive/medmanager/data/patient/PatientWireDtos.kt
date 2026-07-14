package com.afterlifearchive.medmanager.data.patient

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

internal val PatientWireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}

@Serializable
internal data class PatientDataListDto<T>(val data: List<T>)

@Serializable
internal data class PatientTodayDoseDto(
    val key: String,
    val patientId: String,
    val medicationId: String,
    val scheduledAt: String,
    val effectiveStatus: String = "pending",
    val recordedByType: String? = null,
    val medicationSnapshot: PatientMedicationSnapshotDto,
) {
    fun toDomain() = PatientDose(
        key = key,
        patientId = patientId,
        medicationId = medicationId,
        scheduledAt = Instant.parse(scheduledAt),
        status = effectiveStatus.toDoseStatus(),
        recordedByType = recordedByType?.takeIf(String::isNotBlank)?.toRecordedByType(),
        medicationName = medicationSnapshot.name,
        dosageText = medicationSnapshot.dosageText,
        doseCount = medicationSnapshot.doseCountPerIntake,
        dosageStrengthValue = medicationSnapshot.dosageStrengthValue,
        dosageStrengthUnit = medicationSnapshot.dosageStrengthUnit,
        notes = medicationSnapshot.notes?.takeIf(String::isNotBlank),
    )
}

@Serializable
internal data class PatientMedicationSnapshotDto(
    val name: String,
    val dosageText: String,
    val doseCountPerIntake: Double = 1.0,
    val dosageStrengthValue: Double,
    val dosageStrengthUnit: String,
    val notes: String? = null,
)

@Serializable
internal data class PatientSlotTimesEnvelopeDto(val data: PatientSlotTimesDataDto)

@Serializable
internal data class PatientSlotTimesDataDto(val slotTimes: PatientSlotTimesDto)

@Serializable
internal data class PatientSlotTimesDto(
    val morning: String,
    val noon: String,
    val evening: String,
    val bedtime: String,
) {
    fun toDomain() = PatientSlotTimes(
        morning = PatientSlotTimes.requireValid(morning),
        noon = PatientSlotTimes.requireValid(noon),
        evening = PatientSlotTimes.requireValid(evening),
        bedtime = PatientSlotTimes.requireValid(bedtime),
    )
}

@Serializable
internal data class PatientMedicationDto(
    val id: String,
    val patientId: String,
    val name: String,
    val dosageText: String,
    val doseCountPerIntake: Double,
    val dosageStrengthValue: Double,
    val dosageStrengthUnit: String,
    val notes: String? = null,
    val isPrn: Boolean = false,
    val prnInstructions: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val inventoryCount: Double? = null,
    val inventoryUnit: String? = null,
    val inventoryEnabled: Boolean = false,
    val inventoryQuantity: Double = 0.0,
    val inventoryOut: Boolean = false,
    val isActive: Boolean,
    val isArchived: Boolean,
    val nextScheduledAt: String? = null,
    val regimenTimes: List<String>? = null,
    val regimenDaysOfWeek: List<String>? = null,
) {
    fun toDomain() = PatientMedication(
        id = id,
        patientId = patientId,
        name = name,
        dosageText = dosageText,
        doseCountPerIntake = doseCountPerIntake,
        dosageStrengthValue = dosageStrengthValue,
        dosageStrengthUnit = dosageStrengthUnit,
        notes = notes?.takeIf(String::isNotBlank),
        isPrn = isPrn,
        prnInstructions = prnInstructions?.takeIf(String::isNotBlank),
        startDate = Instant.parse(startDate),
        endDate = endDate?.takeIf(String::isNotBlank)?.let(Instant::parse),
        inventoryCount = inventoryCount,
        inventoryUnit = inventoryUnit?.takeIf(String::isNotBlank),
        inventoryEnabled = inventoryEnabled,
        inventoryQuantity = inventoryQuantity,
        inventoryOut = inventoryOut,
        isActive = isActive,
        isArchived = isArchived,
        nextScheduledAt = nextScheduledAt?.takeIf(String::isNotBlank)?.let(Instant::parse),
        regimenTimes = regimenTimes,
        regimenDaysOfWeek = regimenDaysOfWeek,
    )
}

@Serializable
internal data class PatientSlotBulkRequestDto(val date: String, val slot: String)

@Serializable
internal data class PatientDoseRecordRequestDto(val medicationId: String, val scheduledAt: String)

@Serializable
internal data class PatientPrnRecordRequestDto(
    val medicationId: String,
    val takenAt: String? = null,
    val quantityTaken: Double? = null,
)

@Serializable
internal data class PatientSlotBulkResponseDto(
    val updatedCount: Int,
    val remainingCount: Int,
    val insufficientCount: Int,
    val totalPills: Double,
    val medCount: Int,
    val slotTime: String,
    val slotSummary: PatientSlotSummaryDto,
    val recordingGroupId: String? = null,
) {
    fun toDomain() = SlotBulkRecordResult(
        updatedCount = updatedCount,
        remainingCount = remainingCount,
        insufficientCount = insufficientCount,
        totalPills = totalPills,
        medCount = medCount,
        slotTime = slotTime,
        slotSummary = mapOf(
            MedicationSlot.MORNING to slotSummary.morning.toHistoryStatus(),
            MedicationSlot.NOON to slotSummary.noon.toHistoryStatus(),
            MedicationSlot.EVENING to slotSummary.evening.toHistoryStatus(),
            MedicationSlot.BEDTIME to slotSummary.bedtime.toHistoryStatus(),
        ),
        recordingGroupId = recordingGroupId?.takeIf { it.isNotBlank() && it != "null" },
    )
}

@Serializable
internal data class PatientSlotSummaryDto(
    val morning: String = "none",
    val noon: String = "none",
    val evening: String = "none",
    val bedtime: String = "none",
)

@Serializable
internal data class PatientHistoryMonthResponseDto(
    val days: List<PatientHistoryDayDto>? = null,
    val monthSummary: List<PatientHistoryDayDto>? = null,
    val prnCountByDay: Map<String, Int>? = null,
) {
    fun toDomain(): List<HistoryDay> = (days ?: monthSummary
        ?: throw SerializationException("Patient month history requires days or monthSummary"))
        .map { it.toDomain(prnCountByDay?.get(it.date) ?: 0) }
}

@Serializable
internal data class PatientHistoryDayDto(val date: String, val slotSummary: PatientSlotSummaryDto) {
    fun toDomain(prnCount: Int) = HistoryDay(
        date = date,
        morning = slotSummary.morning.toHistoryStatus(),
        noon = slotSummary.noon.toHistoryStatus(),
        evening = slotSummary.evening.toHistoryStatus(),
        bedtime = slotSummary.bedtime.toHistoryStatus(),
        prnCount = prnCount,
    )
}

@Serializable
internal data class PatientHistoryDayResponseDto(
    val date: String,
    val doses: List<PatientHistoryScheduledDoseDto>? = null,
    val dayDetails: List<PatientHistoryScheduledDoseDto>? = null,
    val prnItems: List<PatientPrnHistoryItemDto>? = null,
) {
    fun toDomain() = HistoryDayDetail(
        date = date,
        doses = (doses ?: dayDetails
            ?: throw SerializationException("Patient day history requires doses or dayDetails"))
            .map(PatientHistoryScheduledDoseDto::toDomain),
        prnItems = prnItems.orEmpty().map(PatientPrnHistoryItemDto::toDomain),
    )
}

@Serializable
internal data class PatientHistoryScheduledDoseDto(
    val medicationId: String,
    val medicationName: String,
    val dosageText: String,
    val doseCountPerIntake: Double,
    val scheduledAt: String,
    val slot: String,
    val effectiveStatus: String,
    val recordedByType: String? = null,
) {
    fun toDomain() = HistoryScheduledDose(
        medicationId = medicationId,
        medicationName = medicationName,
        dosageText = dosageText,
        doseCountPerIntake = doseCountPerIntake,
        scheduledAt = Instant.parse(scheduledAt),
        slot = MedicationSlot.valueOf(slot.uppercase()),
        status = effectiveStatus.toDoseStatus(),
        recordedByType = recordedByType?.takeIf(String::isNotBlank)?.toRecordedByType(),
    )
}

@Serializable
internal data class PatientPrnHistoryItemDto(
    val medicationId: String,
    val medicationName: String,
    val takenAt: String,
    val quantityTaken: Double,
    val actorType: String,
) {
    fun toDomain() = PrnHistoryItem(
        medicationId = medicationId,
        medicationName = medicationName,
        takenAt = Instant.parse(takenAt),
        quantityTaken = quantityTaken,
        actorType = PrnActorType.valueOf(actorType.uppercase()),
    )
}

internal fun String.toDoseStatus() = when (lowercase()) {
    "taken" -> DoseStatus.TAKEN
    "missed" -> DoseStatus.MISSED
    else -> DoseStatus.PENDING
}

internal fun String.toHistoryStatus() = when (lowercase()) {
    "taken" -> HistoryStatus.TAKEN
    "missed" -> HistoryStatus.MISSED
    "pending" -> HistoryStatus.PENDING
    else -> HistoryStatus.NONE
}

internal fun String.toRecordedByType() = when (lowercase()) {
    "patient" -> RecordedByType.PATIENT
    "caregiver" -> RecordedByType.CAREGIVER
    else -> null
}
