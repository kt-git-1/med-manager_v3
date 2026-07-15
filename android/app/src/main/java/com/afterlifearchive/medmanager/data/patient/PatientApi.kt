package com.afterlifearchive.medmanager.data.patient

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface PatientDataSource {
    suspend fun today(): List<PatientDose>
    suspend fun slotTimes(): PatientSlotTimes
    suspend fun medications(): List<PatientMedication> = emptyList()
    suspend fun recordSlot(date: String, slot: MedicationSlot): SlotBulkRecordResult = error("recordSlot not implemented")
    suspend fun recordPrn(medication: PatientMedication) = Unit
    suspend fun recordDose(dose: PatientDose)
    suspend fun history(year: Int, month: Int): List<HistoryDay>
    suspend fun historyStreak(): PatientHistoryStreak = error("historyStreak not implemented")
    suspend fun historyDay(date: String): HistoryDayDetail = error("historyDay not implemented")
    suspend fun revokeSession()
}

class PatientApi(private val client: ApiClient) : PatientDataSource {
    override suspend fun today(): List<PatientDose> {
        val body = client.getBody("api/patient/today", RequestAuthPolicy.PATIENT)
        return PatientWireJson.decodeFromString<PatientDataListDto<PatientTodayDoseDto>>(body)
            .data
            .map(PatientTodayDoseDto::toDomain)
    }

    override suspend fun slotTimes(): PatientSlotTimes {
        val body = client.getBody("api/patient/slot-times", RequestAuthPolicy.PATIENT)
        return PatientWireJson.decodeFromString<PatientSlotTimesEnvelopeDto>(body).data.slotTimes.toDomain()
    }

    override suspend fun medications(): List<PatientMedication> {
        val body = client.getBody("api/medications", RequestAuthPolicy.PATIENT)
        return PatientWireJson.decodeFromString<PatientDataListDto<PatientMedicationDto>>(body)
            .data
            .map(PatientMedicationDto::toDomain)
    }

    override suspend fun recordSlot(date: String, slot: MedicationSlot): SlotBulkRecordResult {
        val response = client.postBody(
            "api/patient/dose-records/slot",
            PatientWireJson.encodeToString(PatientSlotBulkRequestDto(date, slot.name.lowercase())),
            RequestAuthPolicy.PATIENT,
        )
        return PatientWireJson.decodeFromString<PatientSlotBulkResponseDto>(response).toDomain()
    }

    override suspend fun recordPrn(medication: PatientMedication) {
        client.postBody(
            "api/patients/${medication.patientId}/prn-dose-records",
            PatientWireJson.encodeToString(PatientPrnRecordRequestDto(medication.id)),
            RequestAuthPolicy.PATIENT,
        )
    }

    override suspend fun recordDose(dose: PatientDose) {
        client.postBody(
            "api/patient/dose-records",
            PatientWireJson.encodeToString(PatientDoseRecordRequestDto(dose.medicationId, dose.scheduledAt.toString())),
            RequestAuthPolicy.PATIENT,
        )
    }

    override suspend fun history(year: Int, month: Int): List<HistoryDay> {
        val response = client.getBody(
            "api/patient/history/month?year=$year&month=$month",
            RequestAuthPolicy.PATIENT,
        )
        return PatientWireJson.decodeFromString<PatientHistoryMonthResponseDto>(response).toDomain()
    }

    override suspend fun historyStreak(): PatientHistoryStreak {
        val response = client.getBody("api/patient/history/streak", RequestAuthPolicy.PATIENT)
        return PatientWireJson.decodeFromString<PatientHistoryStreakResponseDto>(response).toDomain()
    }

    override suspend fun historyDay(date: String): HistoryDayDetail {
        val response = client.getBody("api/patient/history/day?date=$date", RequestAuthPolicy.PATIENT)
        return PatientWireJson.decodeFromString<PatientHistoryDayResponseDto>(response).toDomain()
    }

    override suspend fun revokeSession() {
        client.deleteBody(
            "api/patient/session",
            RequestAuthPolicy.PATIENT,
            allowsAuthRefresh = false,
        )
    }
}
