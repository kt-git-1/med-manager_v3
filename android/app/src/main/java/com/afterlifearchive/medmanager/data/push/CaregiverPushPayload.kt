package com.afterlifearchive.medmanager.data.push

import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import java.time.LocalDate

enum class CaregiverPushEventType(val wireValue: String) {
    DOSE_TAKEN("DOSE_TAKEN"),
    DOSE_MISSED("DOSE_MISSED"),
}

data class CaregiverPushTarget(
    val type: CaregiverPushEventType,
    val patientId: String,
    val date: LocalDate,
    val slot: MedicationSlot,
)

object CaregiverPushPayloadParser {
    fun parse(data: Map<String, String>): CaregiverPushTarget? {
        val type = CaregiverPushEventType.entries.firstOrNull { it.wireValue == data["type"] } ?: return null
        val patientId = data["patientId"]?.takeIf(String::isNotBlank) ?: return null
        val date = runCatching { LocalDate.parse(data["date"]) }.getOrNull() ?: return null
        val slot = when (data["slot"]) {
            "morning" -> MedicationSlot.MORNING
            "noon" -> MedicationSlot.NOON
            "evening" -> MedicationSlot.EVENING
            "bedtime" -> MedicationSlot.BEDTIME
            else -> return null
        }
        return CaregiverPushTarget(type, patientId, date, slot)
    }
}
