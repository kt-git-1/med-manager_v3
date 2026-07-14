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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import org.json.JSONObject

data class CaregiverMedicationState(
    val patientId: String? = null,
    val items: List<PatientMedication> = emptyList(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
)

fun interface CaregiverMedicationDataSource {
    suspend fun listMedications(patientId: String): List<PatientMedication>

    suspend fun createMedication(patientId: String, draft: CaregiverMedicationDraft): PatientMedication =
        error("createMedication is not implemented")

    suspend fun updateMedication(patientId: String, medicationId: String, draft: CaregiverMedicationDraft): PatientMedication =
        error("updateMedication is not implemented")
}

class CaregiverMedicationApi(private val client: ApiClient) : CaregiverMedicationDataSource {
    override suspend fun listMedications(patientId: String): List<PatientMedication> {
        val body = client.getBody("api/medications?patientId=$patientId", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<PatientDataListDto<PatientMedicationDto>>(body)
            .data
            .map(PatientMedicationDto::toDomain)
    }

    override suspend fun createMedication(patientId: String, draft: CaregiverMedicationDraft): PatientMedication {
        val body = client.postBody(
            "api/medications",
            PatientWireJson.encodeToString(draft.toWire(patientId)),
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<CaregiverMedicationEnvelopeDto>(body).data.toDomain()
    }

    override suspend fun updateMedication(
        patientId: String,
        medicationId: String,
        draft: CaregiverMedicationDraft,
    ): PatientMedication {
        val bodyJson = JSONObject(PatientWireJson.encodeToString(draft.toWire(patientId)))
            .apply { remove("patientId") }
            .toString()
        val body = client.patchBody(
            "api/medications/$medicationId?patientId=$patientId",
            bodyJson,
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<CaregiverMedicationEnvelopeDto>(body).data.toDomain()
    }
}

@Serializable
private data class CaregiverMedicationEnvelopeDto(val data: PatientMedicationDto)

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

    suspend fun save(patientId: String, medicationId: String?, draft: CaregiverMedicationDraft): Result<PatientMedication> {
        if (draft.validate().isNotEmpty()) return Result.failure(IllegalArgumentException("invalid medication"))
        return try {
            val saved = if (medicationId == null) dataSource.createMedication(patientId, draft)
            else dataSource.updateMedication(patientId, medicationId, draft)
            val current = mutableState.value
            val items = if (medicationId == null) current.items + saved
            else current.items.map { if (it.id == saved.id) saved else it }
            mutableState.value = CaregiverMedicationState(patientId = patientId, items = items)
            freshnessStore.markMedicationChanged(inventoryChanged = true, notificationPlanChanged = true)
            Result.success(saved)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    fun clear() {
        mutableState.value = CaregiverMedicationState()
    }

    fun newFreshnessCursor(): FreshnessCursor = freshnessStore.newCursor(FreshnessConsumer.CAREGIVER_MEDICATIONS)
}
