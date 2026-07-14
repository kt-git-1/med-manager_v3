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

data class CaregiverRegimen(val id: String, val enabled: Boolean)

fun interface CaregiverMedicationDataSource {
    suspend fun listMedications(patientId: String): List<PatientMedication>

    suspend fun createMedication(patientId: String, draft: CaregiverMedicationDraft): PatientMedication =
        error("createMedication is not implemented")

    suspend fun updateMedication(patientId: String, medicationId: String, draft: CaregiverMedicationDraft): PatientMedication =
        error("updateMedication is not implemented")

    suspend fun listRegimens(medicationId: String): List<CaregiverRegimen> = error("listRegimens is not implemented")
    suspend fun createRegimen(medicationId: String, draft: CaregiverMedicationDraft): CaregiverRegimen = error("createRegimen is not implemented")
    suspend fun updateRegimen(regimenId: String, draft: CaregiverMedicationDraft, enabled: Boolean): CaregiverRegimen =
        error("updateRegimen is not implemented")
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

    override suspend fun listRegimens(medicationId: String): List<CaregiverRegimen> {
        val body = client.getBody("api/medications/$medicationId/regimens", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<CaregiverRegimenListEnvelopeDto>(body).data.map(CaregiverRegimenDto::toDomain)
    }

    override suspend fun createRegimen(medicationId: String, draft: CaregiverMedicationDraft): CaregiverRegimen {
        val input = JSONObject(PatientWireJson.encodeToString(draft.toRegimenWire()))
            .apply { remove("enabled") }
            .toString()
        val body = client.postBody(
            "api/medications/$medicationId/regimens",
            input,
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<CaregiverRegimenEnvelopeDto>(body).data.toDomain()
    }

    override suspend fun updateRegimen(
        regimenId: String,
        draft: CaregiverMedicationDraft,
        enabled: Boolean,
    ): CaregiverRegimen {
        val body = client.patchBody(
            "api/regimens/$regimenId",
            PatientWireJson.encodeToString(draft.toRegimenWire(enabled)),
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<CaregiverRegimenEnvelopeDto>(body).data.toDomain()
    }
}

@Serializable
private data class CaregiverMedicationEnvelopeDto(val data: PatientMedicationDto)

@Serializable
private data class CaregiverRegimenListEnvelopeDto(val data: List<CaregiverRegimenDto>)

@Serializable
private data class CaregiverRegimenEnvelopeDto(val data: CaregiverRegimenDto)

@Serializable
private data class CaregiverRegimenDto(val id: String, val enabled: Boolean = true) {
    fun toDomain() = CaregiverRegimen(id, enabled)
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

    suspend fun save(patientId: String, medicationId: String?, draft: CaregiverMedicationDraft): Result<PatientMedication> {
        if (draft.validate().isNotEmpty()) return Result.failure(IllegalArgumentException("invalid medication"))
        val saved = try {
            val saved = if (medicationId == null) dataSource.createMedication(patientId, draft)
            else dataSource.updateMedication(patientId, medicationId, draft)
            val current = mutableState.value
            val items = if (medicationId == null) current.items + saved
            else current.items.map { if (it.id == saved.id) saved else it }
            mutableState.value = CaregiverMedicationState(patientId = patientId, items = items)
            saved
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        val regimenResult = try {
            persistRegimen(saved.id, draft)
            Result.success(saved)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
        freshnessStore.markMedicationChanged(inventoryChanged = true, notificationPlanChanged = true)
        return regimenResult
    }

    private suspend fun persistRegimen(medicationId: String, draft: CaregiverMedicationDraft) {
        val regimens = dataSource.listRegimens(medicationId)
        if (draft.isPrn) {
            regimens.filter { it.enabled }.forEach { dataSource.updateRegimen(it.id, draft, enabled = false) }
            return
        }
        val existing = regimens.firstOrNull { it.enabled } ?: regimens.firstOrNull()
        if (existing == null) dataSource.createRegimen(medicationId, draft)
        else dataSource.updateRegimen(existing.id, draft, enabled = true)
    }

    fun clear() {
        mutableState.value = CaregiverMedicationState()
    }

    fun newFreshnessCursor(): FreshnessCursor = freshnessStore.newCursor(FreshnessConsumer.CAREGIVER_MEDICATIONS)
}
