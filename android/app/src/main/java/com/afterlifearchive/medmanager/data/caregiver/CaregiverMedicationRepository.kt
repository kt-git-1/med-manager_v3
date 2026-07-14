package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.freshness.FreshnessCursor
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.patient.PatientDataListDto
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientMedicationDto
import com.afterlifearchive.medmanager.data.patient.PatientWireJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString

data class CaregiverMedicationState(
    val patientId: String? = null,
    val items: List<PatientMedication> = emptyList(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
)

fun interface CaregiverMedicationDataSource {
    suspend fun listMedications(patientId: String): List<PatientMedication>
}

class CaregiverMedicationApi(private val client: ApiClient) : CaregiverMedicationDataSource {
    override suspend fun listMedications(patientId: String): List<PatientMedication> {
        val body = client.getBody("api/medications?patientId=$patientId", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<PatientDataListDto<PatientMedicationDto>>(body)
            .data
            .map(PatientMedicationDto::toDomain)
    }
}

class CaregiverMedicationRepository(
    private val dataSource: CaregiverMedicationDataSource,
    private val freshnessStore: MutationFreshnessStore,
) {
    private val mutableState = MutableStateFlow(CaregiverMedicationState())
    val state: StateFlow<CaregiverMedicationState> = mutableState.asStateFlow()
    val freshness = freshnessStore.revisions

    suspend fun load(patientId: String) {
        if (mutableState.value.loading && mutableState.value.patientId == patientId) return
        if (mutableState.value.patientId != patientId) {
            mutableState.value = CaregiverMedicationState(patientId = patientId, loading = true)
        } else {
            mutableState.value = mutableState.value.copy(loading = true, loadFailed = false)
        }
        try {
            mutableState.value = CaregiverMedicationState(
                patientId = patientId,
                items = dataSource.listMedications(patientId),
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = CaregiverMedicationState(patientId = patientId, loadFailed = true)
        }
    }

    fun clear() {
        mutableState.value = CaregiverMedicationState()
    }

    fun newFreshnessCursor(): FreshnessCursor = freshnessStore.newCursor(FreshnessConsumer.CAREGIVER_MEDICATIONS)
}
