package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.freshness.FreshnessCursor
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.patient.PatientWireJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class CaregiverInventoryItem(
    val medicationId: String,
    val name: String,
    val isPrn: Boolean,
    val doseCountPerIntake: Double,
    val inventoryEnabled: Boolean,
    val inventoryQuantity: Double,
    val inventoryLowThreshold: Int,
    val periodEnded: Boolean,
    val low: Boolean,
    val out: Boolean,
    val dailyPlannedUnits: Double?,
    val nextSevenDaysPlannedUnits: Double?,
    val nextFourteenDaysPlannedUnits: Double?,
    val nextTwentyOneDaysPlannedUnits: Double?,
    val daysRemaining: Int?,
    val refillDueDate: String?,
) {
    val needsAction: Boolean get() = inventoryEnabled && !periodEnded && (low || out)
}

data class CaregiverInventoryState(
    val patientId: String? = null,
    val hasLoaded: Boolean = false,
    val items: List<CaregiverInventoryItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
    val updatingMedicationId: String? = null,
    val mutationFailed: Boolean = false,
    val mutationMessage: CaregiverInventoryMutationMessage? = null,
)

enum class CaregiverInventoryMutationMessage { SAVED, REFILLED, CORRECTED }

interface CaregiverInventoryDataSource {
    suspend fun list(patientId: String): List<CaregiverInventoryItem>
    suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?): CaregiverInventoryItem
    suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?): CaregiverInventoryItem
}

class CaregiverInventoryApi(private val client: ApiClient) : CaregiverInventoryDataSource {
    override suspend fun list(patientId: String): List<CaregiverInventoryItem> {
        val body = client.getBody("api/patients/$patientId/inventory", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<InventoryListEnvelopeDto>(body).data.medications.map(InventoryItemDto::toDomain)
    }

    override suspend fun update(
        patientId: String,
        medicationId: String,
        enabled: Boolean,
        quantity: Double?,
    ): CaregiverInventoryItem {
        val body = client.patchBody(
            "api/patients/$patientId/medications/$medicationId/inventory",
            PatientWireJson.encodeToString(InventoryUpdateDto(enabled, quantity)),
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<InventoryItemEnvelopeDto>(body).data.toDomain()
    }

    override suspend fun adjust(
        patientId: String,
        medicationId: String,
        reason: String,
        delta: Double?,
        absoluteQuantity: Double?,
    ): CaregiverInventoryItem {
        val body = client.postBody(
            "api/patients/$patientId/medications/$medicationId/inventory/adjust",
            PatientWireJson.encodeToString(InventoryAdjustDto(reason, delta, absoluteQuantity)),
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<InventoryItemEnvelopeDto>(body).data.toDomain()
    }
}

@Serializable
private data class InventoryListEnvelopeDto(val data: InventoryListDataDto)

@Serializable
private data class InventoryListDataDto(val patientId: String, val medications: List<InventoryItemDto>)

@Serializable
private data class InventoryItemEnvelopeDto(val data: InventoryItemDto)

@Serializable
private data class InventoryUpdateDto(val inventoryEnabled: Boolean? = null, val inventoryQuantity: Double? = null)

@Serializable
private data class InventoryAdjustDto(val reason: String, val delta: Double? = null, val absoluteQuantity: Double? = null)

@Serializable
private data class InventoryItemDto(
    val medicationId: String,
    val name: String,
    val isPrn: Boolean = false,
    val doseCountPerIntake: Double = 1.0,
    val inventoryEnabled: Boolean = false,
    val inventoryQuantity: Double = 0.0,
    val inventoryLowThreshold: Int = 3,
    val periodEnded: Boolean = false,
    val low: Boolean = false,
    val out: Boolean = false,
    val dailyPlannedUnits: Double? = null,
    val nextSevenDaysPlannedUnits: Double? = null,
    val nextFourteenDaysPlannedUnits: Double? = null,
    val nextTwentyOneDaysPlannedUnits: Double? = null,
    val daysRemaining: Int? = null,
    val refillDueDate: String? = null,
) {
    fun toDomain() = CaregiverInventoryItem(
        medicationId, name, isPrn, doseCountPerIntake, inventoryEnabled, inventoryQuantity,
        inventoryLowThreshold, periodEnded, low, out, dailyPlannedUnits, nextSevenDaysPlannedUnits,
        nextFourteenDaysPlannedUnits, nextTwentyOneDaysPlannedUnits, daysRemaining, refillDueDate,
    )
}

class CaregiverInventoryRepository(
    private val dataSource: CaregiverInventoryDataSource,
    private val freshnessStore: MutationFreshnessStore,
) {
    private val mutableState = MutableStateFlow(CaregiverInventoryState())
    val state: StateFlow<CaregiverInventoryState> = mutableState.asStateFlow()
    val freshness = freshnessStore.revisions

    suspend fun load(patientId: String) {
        if ((mutableState.value.loading || mutableState.value.refreshing) && mutableState.value.patientId == patientId) return
        val samePatient = mutableState.value.patientId == patientId
        val hasContent = samePatient && mutableState.value.hasLoaded
        mutableState.value = if (samePatient) mutableState.value.copy(
            loading = !hasContent,
            refreshing = hasContent,
            loadFailed = false,
            refreshFailed = false,
        )
        else CaregiverInventoryState(patientId = patientId, loading = true)
        try {
            val items = sorted(dataSource.list(patientId))
            val previous = mutableState.value
            mutableState.value = CaregiverInventoryState(
                patientId = patientId,
                hasLoaded = true,
                items = items,
                mutationMessage = previous.mutationMessage,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val current = mutableState.value
            mutableState.value = if (current.patientId == patientId && current.hasLoaded) {
                current.copy(loading = false, refreshing = false, loadFailed = false, refreshFailed = true)
            } else CaregiverInventoryState(patientId = patientId, loadFailed = true)
        }
    }

    suspend fun updateSettings(patientId: String, item: CaregiverInventoryItem, enabled: Boolean): Boolean = mutate(
        patientId,
        item,
        CaregiverInventoryMutationMessage.SAVED,
    ) { dataSource.update(patientId, item.medicationId, enabled, null) }

    suspend fun refill(patientId: String, item: CaregiverInventoryItem, amount: Double): Boolean {
        if (!amount.isFinite() || amount <= 0) return false
        return mutate(patientId, item, CaregiverInventoryMutationMessage.REFILLED) {
            dataSource.adjust(patientId, item.medicationId, "REFILL", amount, null)
        }
    }

    suspend fun correct(patientId: String, item: CaregiverInventoryItem, quantity: Double): Boolean {
        if (!quantity.isFinite() || quantity < 0) return false
        return mutate(patientId, item, CaregiverInventoryMutationMessage.CORRECTED) {
            dataSource.adjust(patientId, item.medicationId, "SET", null, quantity)
        }
    }

    private suspend fun mutate(
        patientId: String,
        item: CaregiverInventoryItem,
        message: CaregiverInventoryMutationMessage,
        request: suspend () -> CaregiverInventoryItem,
    ): Boolean {
        val current = mutableState.value
        if (current.patientId != patientId || current.refreshFailed || current.updatingMedicationId != null) return false
        mutableState.value = current.copy(updatingMedicationId = item.medicationId, mutationFailed = false, mutationMessage = null)
        return try {
            val updated = request()
            mutableState.value = mutableState.value.copy(
                items = sorted(mutableState.value.items.map { if (it.medicationId == updated.medicationId) updated else it }),
                updatingMedicationId = null,
                mutationMessage = message,
            )
            freshnessStore.markInventoryChanged()
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(updatingMedicationId = null, mutationFailed = true)
            false
        }
    }

    fun clear() { mutableState.value = CaregiverInventoryState() }
    fun newFreshnessCursor(): FreshnessCursor = freshnessStore.newCursor(FreshnessConsumer.CAREGIVER_INVENTORY)

    private fun sorted(items: List<CaregiverInventoryItem>) = items.sortedWith(
        compareBy<CaregiverInventoryItem>({ it.periodEnded }, { !it.out }, { !it.low }, { it.daysRemaining ?: Int.MAX_VALUE }, { it.inventoryQuantity }, { it.name }),
    )
}
