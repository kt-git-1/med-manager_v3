package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.freshness.FreshnessCursor
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.PatientDataListDto
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientMedicationDto
import com.afterlifearchive.medmanager.data.patient.PatientTodayDoseDto
import com.afterlifearchive.medmanager.data.patient.PatientWireJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

data class CaregiverInventorySummary(
    val medicationId: String,
    val inventoryEnabled: Boolean,
    val inventoryQuantity: Double,
    val doseCountPerIntake: Double,
    val low: Boolean,
    val out: Boolean,
) {
    val insufficientForDose: Boolean get() = inventoryEnabled && inventoryQuantity < doseCountPerIntake
}

data class CaregiverTodayState(
    val patientId: String? = null,
    val doses: List<PatientDose> = emptyList(),
    val prnMedications: List<PatientMedication> = emptyList(),
    val outOfStockMedicationIds: Set<String> = emptySet(),
    val hasLowStock: Boolean = false,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
)

interface CaregiverTodayDataSource {
    suspend fun today(patientId: String): List<PatientDose>
    suspend fun medications(patientId: String): List<PatientMedication>
    suspend fun inventory(patientId: String): List<CaregiverInventorySummary>
}

class CaregiverTodayApi(private val client: ApiClient) : CaregiverTodayDataSource {
    override suspend fun today(patientId: String): List<PatientDose> {
        val body = client.getBody("api/patients/$patientId/today", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<PatientDataListDto<PatientTodayDoseDto>>(body)
            .data.map(PatientTodayDoseDto::toDomain)
    }

    override suspend fun medications(patientId: String): List<PatientMedication> {
        val body = client.getBody("api/medications?patientId=$patientId", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<PatientDataListDto<PatientMedicationDto>>(body)
            .data.map(PatientMedicationDto::toDomain)
    }

    override suspend fun inventory(patientId: String): List<CaregiverInventorySummary> {
        val body = client.getBody("api/patients/$patientId/inventory", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<CaregiverInventoryEnvelopeDto>(body)
            .data.medications.map(CaregiverInventoryItemDto::toDomain)
    }
}

@Serializable
private data class CaregiverInventoryEnvelopeDto(val data: CaregiverInventoryDataDto)

@Serializable
private data class CaregiverInventoryDataDto(val patientId: String, val medications: List<CaregiverInventoryItemDto>)

@Serializable
private data class CaregiverInventoryItemDto(
    val medicationId: String,
    val doseCountPerIntake: Double = 1.0,
    val inventoryEnabled: Boolean = false,
    val inventoryQuantity: Double = 0.0,
    val low: Boolean = false,
    val out: Boolean = false,
) {
    fun toDomain() = CaregiverInventorySummary(
        medicationId,
        inventoryEnabled,
        inventoryQuantity,
        doseCountPerIntake,
        low,
        out,
    )
}

class CaregiverTodayRepository(
    private val dataSource: CaregiverTodayDataSource,
    private val freshnessStore: MutationFreshnessStore,
) {
    private val mutableState = MutableStateFlow(CaregiverTodayState())
    val state: StateFlow<CaregiverTodayState> = mutableState.asStateFlow()
    val freshness = freshnessStore.revisions

    suspend fun load(patientId: String) {
        if (mutableState.value.loading && mutableState.value.patientId == patientId) return
        mutableState.value = if (mutableState.value.patientId == patientId) {
            mutableState.value.copy(loading = true, loadFailed = false)
        } else {
            CaregiverTodayState(patientId = patientId, loading = true)
        }
        try {
            val loaded = coroutineScope {
                val doses = async { dataSource.today(patientId) }
                val medications = async { dataSource.medications(patientId) }
                val inventory = async { dataSource.inventory(patientId) }
                Triple(doses.await(), medications.await(), inventory.await())
            }
            val inventory = loaded.third
            mutableState.value = CaregiverTodayState(
                patientId = patientId,
                doses = loaded.first.sortedWith(compareBy<PatientDose>({ statusRank(it.status) }, { it.scheduledAt })),
                prnMedications = loaded.second
                    .filter { it.isPrn && it.isActive && !it.isArchived }
                    .sortedBy { it.name },
                outOfStockMedicationIds = inventory.filter { it.insufficientForDose }.mapTo(mutableSetOf()) { it.medicationId },
                hasLowStock = inventory.any { it.inventoryEnabled && (it.low || it.out) },
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = CaregiverTodayState(patientId = patientId, loadFailed = true)
        }
    }

    fun clear() {
        mutableState.value = CaregiverTodayState()
    }

    fun newFreshnessCursor(): FreshnessCursor = freshnessStore.newCursor(FreshnessConsumer.CAREGIVER_TODAY)

    private fun statusRank(status: DoseStatus) = when (status) {
        DoseStatus.PENDING -> 0
        DoseStatus.MISSED -> 1
        DoseStatus.TAKEN -> 2
    }
}
