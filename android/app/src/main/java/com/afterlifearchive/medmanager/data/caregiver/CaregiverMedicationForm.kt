package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.patient.PatientMedication
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

private val Tokyo = ZoneId.of("Asia/Tokyo")

data class CaregiverMedicationDraft(
    val name: String = "",
    val dosageStrengthValue: String = "",
    val dosageStrengthUnit: String = "",
    val doseCountPerIntake: String = "1",
    val startDate: LocalDate = LocalDate.now(Tokyo),
    val endDate: LocalDate? = null,
    val notes: String = "",
    val isPrn: Boolean = false,
    val prnInstructions: String = "",
    val inventoryCount: String = "",
    val scheduleFrequency: CaregiverScheduleFrequency = CaregiverScheduleFrequency.DAILY,
    val selectedDays: Set<CaregiverScheduleDay> = emptySet(),
    val selectedSlots: Set<CaregiverScheduleSlot> = emptySet(),
) {
    companion object {
        fun from(medication: PatientMedication, slotTimes: CaregiverSlotTimes? = null) = CaregiverMedicationDraft(
            name = medication.name,
            dosageStrengthValue = medication.dosageStrengthValue.takeUnless { it == 0.0 }?.formValue().orEmpty(),
            dosageStrengthUnit = medication.dosageStrengthUnit,
            doseCountPerIntake = medication.doseCountPerIntake.takeUnless { it == 0.0 }?.formValue().orEmpty(),
            startDate = medication.startDate.atZone(Tokyo).toLocalDate(),
            endDate = medication.endDate?.atZone(Tokyo)?.toLocalDate(),
            notes = medication.notes.orEmpty(),
            isPrn = medication.isPrn,
            prnInstructions = medication.prnInstructions.orEmpty(),
            inventoryCount = medication.inventoryCount?.formValue().orEmpty(),
            scheduleFrequency = if (medication.regimenDaysOfWeek.isNullOrEmpty()) CaregiverScheduleFrequency.DAILY else CaregiverScheduleFrequency.WEEKLY,
            selectedDays = medication.regimenDaysOfWeek.orEmpty().mapNotNull { value ->
                CaregiverScheduleDay.entries.firstOrNull { it.apiValue == value }
            }.toSet(),
            selectedSlots = medication.regimenTimes.orEmpty().mapNotNull { value ->
                CaregiverScheduleSlot.entries.firstOrNull { slot ->
                    slot.apiValue == value || slotTimes?.value(slot) == value
                }
            }.toSet(),
        )
    }
}

enum class CaregiverScheduleFrequency { DAILY, WEEKLY }

enum class CaregiverScheduleDay(val apiValue: String) {
    MON("MON"), TUE("TUE"), WED("WED"), THU("THU"), FRI("FRI"), SAT("SAT"), SUN("SUN"),
}

enum class CaregiverScheduleSlot(val apiValue: String) {
    MORNING("morning"), NOON("noon"), EVENING("evening"), BEDTIME("bedtime"),
}

private fun CaregiverSlotTimes.value(slot: CaregiverScheduleSlot): String = when (slot) {
    CaregiverScheduleSlot.MORNING -> morning
    CaregiverScheduleSlot.NOON -> noon
    CaregiverScheduleSlot.EVENING -> evening
    CaregiverScheduleSlot.BEDTIME -> bedtime
}

enum class CaregiverMedicationField {
    NAME,
    DOSAGE_VALUE,
    DOSAGE_UNIT,
    DOSE_COUNT,
    END_DATE,
    INVENTORY_COUNT,
    SCHEDULE_DAY,
    SCHEDULE_SLOT,
}

data class CaregiverMedicationValidationError(
    val field: CaregiverMedicationField,
    val message: String,
)

fun CaregiverMedicationDraft.validate(unknownDosageLabel: String = "不明"): List<CaregiverMedicationValidationError> = buildList {
    if (name.isBlank()) add(CaregiverMedicationValidationError(CaregiverMedicationField.NAME, "薬の名前を入力してください"))
    if (dosageStrengthUnit.isBlank()) {
        add(CaregiverMedicationValidationError(CaregiverMedicationField.DOSAGE_UNIT, "規格の単位を選択してください"))
    } else if (dosageStrengthUnit != unknownDosageLabel) {
        if (dosageStrengthValue.isBlank()) {
            add(CaregiverMedicationValidationError(CaregiverMedicationField.DOSAGE_VALUE, "規格の数値を入力してください"))
        } else if (dosageStrengthValue.toPositiveDoubleOrNull() == null) {
            add(CaregiverMedicationValidationError(CaregiverMedicationField.DOSAGE_VALUE, "規格は0より大きい数値で入力してください"))
        }
    }
    if (doseCountPerIntake.isNotBlank() && doseCountPerIntake.toPositiveDoubleOrNull() == null) {
        add(CaregiverMedicationValidationError(CaregiverMedicationField.DOSE_COUNT, "1回量は0より大きい数値で入力してください"))
    }
    if (endDate?.isBefore(startDate) == true) {
        add(CaregiverMedicationValidationError(CaregiverMedicationField.END_DATE, "終了日は開始日以降にしてください"))
    }
    if (inventoryCount.isNotBlank()) {
        val value = inventoryCount.toDoubleOrNull()
        if (value == null || !value.isFinite() || value < 0) {
            add(CaregiverMedicationValidationError(CaregiverMedicationField.INVENTORY_COUNT, "在庫数は0以上の数値で入力してください"))
        }
    }
    if (!isPrn) {
        if (selectedSlots.isEmpty()) {
            add(CaregiverMedicationValidationError(CaregiverMedicationField.SCHEDULE_SLOT, "服用する時間帯を1つ以上選択してください"))
        }
        if (scheduleFrequency == CaregiverScheduleFrequency.WEEKLY && selectedDays.isEmpty()) {
            add(CaregiverMedicationValidationError(CaregiverMedicationField.SCHEDULE_DAY, "服用する曜日を1つ以上選択してください"))
        }
    }
}

internal fun CaregiverMedicationDraft.toWire(patientId: String) = CaregiverMedicationWriteDto(
    patientId = patientId,
    name = name.trim(),
    dosageText = if (dosageStrengthUnit == "不明") "不明" else dosageStrengthValue.trim() + dosageStrengthUnit.trim(),
    doseCountPerIntake = doseCountPerIntake.toDoubleOrNull() ?: 0.0,
    dosageStrengthValue = dosageStrengthValue.toDoubleOrNull() ?: 0.0,
    dosageStrengthUnit = dosageStrengthUnit.trim(),
    notes = notes.trim().ifEmpty { null },
    isPrn = isPrn,
    prnInstructions = prnInstructions.trim().ifEmpty { null },
    startDate = startDate.atStartOfDay(Tokyo).toInstant().toString(),
    endDate = endDate?.atStartOfDay(Tokyo)?.toInstant()?.toString(),
    inventoryCount = inventoryCount.toDoubleOrNull(),
    inventoryUnit = inventoryCount.takeIf(String::isNotBlank)?.let { "錠" },
)

@Serializable
internal data class CaregiverMedicationWriteDto(
    val patientId: String? = null,
    val name: String,
    val dosageText: String,
    val doseCountPerIntake: Double,
    val dosageStrengthValue: Double,
    val dosageStrengthUnit: String,
    val notes: String? = null,
    val isPrn: Boolean,
    val prnInstructions: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val inventoryCount: Double? = null,
    val inventoryUnit: String? = null,
)

@Serializable
internal data class CaregiverRegimenWriteDto(
    val timezone: String = "Asia/Tokyo",
    val startDate: String,
    val endDate: String? = null,
    val times: List<String>,
    val daysOfWeek: List<String>,
    val enabled: Boolean? = null,
)

internal fun CaregiverMedicationDraft.toRegimenWire(enabled: Boolean? = null) = CaregiverRegimenWriteDto(
    startDate = startDate.atStartOfDay(Tokyo).toInstant().toString(),
    endDate = endDate?.atStartOfDay(Tokyo)?.toInstant()?.toString(),
    times = CaregiverScheduleSlot.entries.filter(selectedSlots::contains).map { it.apiValue },
    daysOfWeek = if (scheduleFrequency == CaregiverScheduleFrequency.WEEKLY) {
        CaregiverScheduleDay.entries.filter(selectedDays::contains).map { it.apiValue }
    } else emptyList(),
    enabled = enabled,
)

private fun String.toPositiveDoubleOrNull(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
private fun Double.formValue(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
