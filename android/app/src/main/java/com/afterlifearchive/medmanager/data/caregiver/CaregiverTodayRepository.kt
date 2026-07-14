package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.freshness.FreshnessCursor
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.PatientDataListDto
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientMedicationDto
import com.afterlifearchive.medmanager.data.patient.PatientTodayDoseDto
import com.afterlifearchive.medmanager.data.patient.PatientDoseRecordRequestDto
import com.afterlifearchive.medmanager.data.patient.PatientSlotBulkRequestDto
import com.afterlifearchive.medmanager.data.patient.PatientSlotBulkResponseDto
import com.afterlifearchive.medmanager.data.patient.PatientPrnRecordRequestDto
import com.afterlifearchive.medmanager.data.patient.PatientWireJson
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.SlotBulkRecordResult
import java.net.URLEncoder
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

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
    val hasLoaded: Boolean = false,
    val doses: List<PatientDose> = emptyList(),
    val prnMedications: List<PatientMedication> = emptyList(),
    val outOfStockMedicationIds: Set<String> = emptySet(),
    val hasLowStock: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
    val updatingDoseKey: String? = null,
    val mutationError: CaregiverTodayMutationError? = null,
    val mutationMessage: CaregiverTodayMutationMessage? = null,
    val updatingSlot: MedicationSlot? = null,
    val lastUpdatedCount: Int = 0,
    val lastInsufficientCount: Int = 0,
    val updatingPrnMedicationId: String? = null,
)

enum class CaregiverTodayMutationError { INSUFFICIENT_INVENTORY, FAILED }
enum class CaregiverTodayMutationMessage { RECORDED, DELETED, SLOT_RECORDED, SLOT_PARTIAL, NOTHING_TO_RECORD, PRN_RECORDED }

interface CaregiverTodayDataSource {
    suspend fun today(patientId: String): List<PatientDose>
    suspend fun medications(patientId: String): List<PatientMedication>
    suspend fun inventory(patientId: String): List<CaregiverInventorySummary>
    suspend fun recordDose(patientId: String, dose: PatientDose): Unit = error("recordDose is not implemented")
    suspend fun deleteDose(patientId: String, dose: PatientDose): Unit = error("deleteDose is not implemented")
    suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot): SlotBulkRecordResult = error("recordSlot is not implemented")
    suspend fun recordPrn(patientId: String, medication: PatientMedication): Unit = error("recordPrn is not implemented")
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

    override suspend fun recordDose(patientId: String, dose: PatientDose) {
        client.postBody(
            "api/patients/$patientId/dose-records",
            PatientWireJson.encodeToString(PatientDoseRecordRequestDto(dose.medicationId, dose.scheduledAt.toString())),
            RequestAuthPolicy.CAREGIVER,
        )
    }

    override suspend fun deleteDose(patientId: String, dose: PatientDose) {
        val medicationId = URLEncoder.encode(dose.medicationId, Charsets.UTF_8.name())
        val scheduledAt = URLEncoder.encode(dose.scheduledAt.toString(), Charsets.UTF_8.name())
        client.deleteBody(
            "api/patients/$patientId/dose-records?medicationId=$medicationId&scheduledAt=$scheduledAt",
            RequestAuthPolicy.CAREGIVER,
        )
    }

    override suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot): SlotBulkRecordResult {
        val body = client.postBody(
            "api/patients/$patientId/dose-records/slot",
            PatientWireJson.encodeToString(PatientSlotBulkRequestDto(date, slot.name.lowercase())),
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<PatientSlotBulkResponseDto>(body).toDomain()
    }

    override suspend fun recordPrn(patientId: String, medication: PatientMedication) {
        client.postBody(
            "api/patients/$patientId/prn-dose-records",
            PatientWireJson.encodeToString(PatientPrnRecordRequestDto(medication.id)),
            RequestAuthPolicy.CAREGIVER,
        )
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
        if ((mutableState.value.loading || mutableState.value.refreshing) && mutableState.value.patientId == patientId) return
        mutableState.value = if (mutableState.value.patientId == patientId) {
            val hasContent = mutableState.value.hasLoaded
            mutableState.value.copy(
                loading = !hasContent,
                refreshing = hasContent,
                loadFailed = false,
                refreshFailed = false,
            )
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
            val previous = mutableState.value
            mutableState.value = CaregiverTodayState(
                patientId = patientId,
                hasLoaded = true,
                doses = loaded.first.sortedWith(compareBy<PatientDose>({ statusRank(it.status) }, { it.scheduledAt })),
                prnMedications = loaded.second
                    .filter { it.isPrn && it.isActive && !it.isArchived }
                    .sortedBy { it.name },
                outOfStockMedicationIds = inventory.filter { it.insufficientForDose }.mapTo(mutableSetOf()) { it.medicationId },
                hasLowStock = inventory.any { it.inventoryEnabled && (it.low || it.out) },
                mutationMessage = previous.mutationMessage,
                lastUpdatedCount = previous.lastUpdatedCount,
                lastInsufficientCount = previous.lastInsufficientCount,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val current = mutableState.value
            mutableState.value = if (current.patientId == patientId && current.hasLoaded) {
                current.copy(
                    loading = false,
                    refreshing = false,
                    loadFailed = false,
                    refreshFailed = true,
                )
            } else {
                CaregiverTodayState(patientId = patientId, loadFailed = true)
            }
        }
    }

    suspend fun recordDose(patientId: String, dose: PatientDose): Boolean {
        val current = mutableState.value
        if (current.patientId != patientId || current.refreshFailed || current.updatingDoseKey != null || dose.status == DoseStatus.TAKEN) return false
        if (dose.medicationId in current.outOfStockMedicationIds) {
            mutableState.value = current.copy(mutationError = CaregiverTodayMutationError.INSUFFICIENT_INVENTORY, mutationMessage = null)
            return false
        }
        mutableState.value = current.copy(updatingDoseKey = dose.key, mutationError = null, mutationMessage = null)
        return try {
            dataSource.recordDose(patientId, dose)
            val updated = mutableState.value.doses.map {
                if (it.key == dose.key) it.copy(status = DoseStatus.TAKEN, recordedByType = RecordedByType.CAREGIVER) else it
            }.sortedWith(doseComparator)
            mutableState.value = mutableState.value.copy(
                doses = updated,
                updatingDoseKey = null,
                mutationMessage = CaregiverTodayMutationMessage.RECORDED,
            )
            freshnessStore.markScheduledDoseChanged(inventoryChanged = true)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(
                updatingDoseKey = null,
                mutationError = if (error is ApiException.InsufficientInventory) CaregiverTodayMutationError.INSUFFICIENT_INVENTORY else CaregiverTodayMutationError.FAILED,
            )
            false
        }
    }

    suspend fun deleteDose(patientId: String, dose: PatientDose): Boolean {
        val current = mutableState.value
        if (current.patientId != patientId || current.refreshFailed || current.updatingDoseKey != null || dose.status != DoseStatus.TAKEN) return false
        mutableState.value = current.copy(updatingDoseKey = dose.key, mutationError = null, mutationMessage = null)
        return try {
            dataSource.deleteDose(patientId, dose)
            val updated = mutableState.value.doses.map {
                if (it.key == dose.key) it.copy(status = DoseStatus.PENDING, recordedByType = null) else it
            }.sortedWith(doseComparator)
            mutableState.value = mutableState.value.copy(
                doses = updated,
                updatingDoseKey = null,
                mutationMessage = CaregiverTodayMutationMessage.DELETED,
            )
            freshnessStore.markScheduledDoseChanged(inventoryChanged = true)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(updatingDoseKey = null, mutationError = CaregiverTodayMutationError.FAILED)
            false
        }
    }

    suspend fun recordSlot(patientId: String, slot: MedicationSlot, doses: List<PatientDose>): Boolean {
        val current = mutableState.value
        if (current.patientId != patientId || current.refreshFailed || current.updatingDoseKey != null || current.updatingSlot != null) return false
        val candidates = doses.filter { it.status != DoseStatus.TAKEN }
        if (candidates.isEmpty()) {
            mutableState.value = current.copy(mutationMessage = CaregiverTodayMutationMessage.NOTHING_TO_RECORD)
            return false
        }
        val date = candidates.minOf(PatientDose::scheduledAt).atZone(TOKYO).toLocalDate().toString()
        mutableState.value = current.copy(
            updatingSlot = slot,
            mutationError = null,
            mutationMessage = null,
            lastUpdatedCount = 0,
            lastInsufficientCount = 0,
        )
        return try {
            val result = dataSource.recordSlot(patientId, date, slot)
            val locallyRecordableKeys = candidates
                .filterNot { it.medicationId in current.outOfStockMedicationIds }
                .take(result.updatedCount)
                .mapTo(mutableSetOf(), PatientDose::key)
            val updated = mutableState.value.doses.map {
                if (it.key in locallyRecordableKeys) it.copy(status = DoseStatus.TAKEN, recordedByType = RecordedByType.CAREGIVER) else it
            }.sortedWith(doseComparator)
            val message = when {
                result.updatedCount > 0 && result.insufficientCount > 0 -> CaregiverTodayMutationMessage.SLOT_PARTIAL
                result.updatedCount > 0 -> CaregiverTodayMutationMessage.SLOT_RECORDED
                result.insufficientCount > 0 -> CaregiverTodayMutationMessage.SLOT_PARTIAL
                else -> CaregiverTodayMutationMessage.NOTHING_TO_RECORD
            }
            mutableState.value = mutableState.value.copy(
                doses = updated,
                updatingSlot = null,
                mutationMessage = message,
                lastUpdatedCount = result.updatedCount,
                lastInsufficientCount = result.insufficientCount,
            )
            if (result.updatedCount > 0) freshnessStore.markScheduledDoseChanged(inventoryChanged = true)
            result.updatedCount > 0
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(updatingSlot = null, mutationError = CaregiverTodayMutationError.FAILED)
            false
        }
    }

    suspend fun recordPrn(patientId: String, medication: PatientMedication): Boolean {
        val current = mutableState.value
        if (current.patientId != patientId || current.refreshFailed || current.updatingDoseKey != null || current.updatingSlot != null || current.updatingPrnMedicationId != null || !medication.isPrn) return false
        if (medication.id in current.outOfStockMedicationIds) {
            mutableState.value = current.copy(mutationError = CaregiverTodayMutationError.INSUFFICIENT_INVENTORY, mutationMessage = null)
            return false
        }
        mutableState.value = current.copy(updatingPrnMedicationId = medication.id, mutationError = null, mutationMessage = null)
        return try {
            dataSource.recordPrn(patientId, medication)
            mutableState.value = mutableState.value.copy(
                updatingPrnMedicationId = null,
                mutationMessage = CaregiverTodayMutationMessage.PRN_RECORDED,
            )
            freshnessStore.markDoseChanged(inventoryChanged = true)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(
                updatingPrnMedicationId = null,
                mutationError = if (error is ApiException.InsufficientInventory) CaregiverTodayMutationError.INSUFFICIENT_INVENTORY else CaregiverTodayMutationError.FAILED,
            )
            false
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

    private val doseComparator get() = compareBy<PatientDose>({ statusRank(it.status) }, { it.scheduledAt })

    private companion object {
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}
