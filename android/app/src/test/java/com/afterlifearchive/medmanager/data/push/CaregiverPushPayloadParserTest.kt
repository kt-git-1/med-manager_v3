package com.afterlifearchive.medmanager.data.push

import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaregiverPushPayloadParserTest {
    @Test
    fun acceptsTakenAndMissedWithTheSameStrictTargetFields() {
        listOf(CaregiverPushEventType.DOSE_TAKEN, CaregiverPushEventType.DOSE_MISSED).forEach { type ->
            assertEquals(
                CaregiverPushTarget(type, "patient-1", LocalDate.of(2026, 7, 15), MedicationSlot.EVENING),
                CaregiverPushPayloadParser.parse(validPayload(type.wireValue)),
            )
        }
    }

    @Test
    fun rejectsUnknownTypeAndMalformedRequiredFields() {
        assertNull(CaregiverPushPayloadParser.parse(validPayload("OTHER")))
        assertNull(CaregiverPushPayloadParser.parse(validPayload("DOSE_MISSED") - "patientId"))
        assertNull(CaregiverPushPayloadParser.parse(validPayload("DOSE_MISSED") + ("patientId" to " ")))
        assertNull(CaregiverPushPayloadParser.parse(validPayload("DOSE_MISSED") + ("date" to "2026-02-30")))
        assertNull(CaregiverPushPayloadParser.parse(validPayload("DOSE_MISSED") + ("slot" to "breakfast")))
        assertNull(CaregiverPushPayloadParser.parse(validPayload("dose_missed")))
    }

    private fun validPayload(type: String) = mapOf(
        "type" to type,
        "patientId" to "patient-1",
        "date" to "2026-07-15",
        "slot" to "evening",
    )
}
