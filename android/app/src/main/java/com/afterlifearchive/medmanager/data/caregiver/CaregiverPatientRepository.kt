package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CaregiverPatient(
    val id: String,
    val displayName: String,
    val slotTimes: CaregiverSlotTimes? = null,
)

data class CaregiverSlotTimes(
    val morning: String,
    val noon: String,
    val evening: String,
    val bedtime: String,
)

data class CaregiverLinkingCode(val code: String, val expiresAt: String)

data class CaregiverPatientState(
    val patients: List<CaregiverPatient> = emptyList(),
    val selectedPatientId: String? = null,
    val hasLoaded: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
    val creating: Boolean = false,
    val createError: CaregiverCreateError? = null,
    val savingSlotTimes: Boolean = false,
    val slotTimesSaveFailed: Boolean = false,
    val linkingCode: CaregiverLinkingCode? = null,
    val issuingLinkingCode: Boolean = false,
    val linkingCodeFailed: Boolean = false,
    val destructiveActionInProgress: Boolean = false,
    val destructiveActionFailed: Boolean = false,
) {
    val selectedPatient: CaregiverPatient?
        get() = patients.firstOrNull { it.id == selectedPatientId }
}

enum class CaregiverCreateError { REQUIRED, TOO_LONG, PATIENT_LIMIT, FAILED }

fun interface CaregiverPatientDataSource {
    suspend fun listPatients(): List<CaregiverPatient>
    suspend fun createPatient(displayName: String): CaregiverPatient = error("createPatient not implemented")
    suspend fun updateSlotTimes(patientId: String, slotTimes: CaregiverSlotTimes): CaregiverSlotTimes =
        error("updateSlotTimes not implemented")
    suspend fun issueLinkingCode(patientId: String): CaregiverLinkingCode = error("issueLinkingCode not implemented")
    suspend fun revokePatient(patientId: String): Unit = error("revokePatient not implemented")
    suspend fun deletePatient(patientId: String): Unit = error("deletePatient not implemented")
    suspend fun deleteCaregiverAccount(): Unit = error("deleteCaregiverAccount not implemented")
}

class CaregiverPatientApi(private val client: ApiClient) : CaregiverPatientDataSource {
    override suspend fun listPatients(): List<CaregiverPatient> {
        val body = client.getBody("api/patients", RequestAuthPolicy.CAREGIVER)
        return caregiverJson.decodeFromString<CaregiverPatientListDto>(body).data.map { it.toDomain() }
    }

    override suspend fun createPatient(displayName: String): CaregiverPatient {
        val body = client.postBody(
            "api/patients",
            caregiverJson.encodeToString(CaregiverPatientCreateDto(displayName)),
            RequestAuthPolicy.CAREGIVER,
        )
        return caregiverJson.decodeFromString<CaregiverPatientEnvelopeDto>(body).data.toDomain()
    }

    override suspend fun updateSlotTimes(patientId: String, slotTimes: CaregiverSlotTimes): CaregiverSlotTimes {
        val body = client.patchBody(
            "api/patients/$patientId",
            caregiverJson.encodeToString(CaregiverSlotTimesUpdateDto(slotTimes.toDto())),
            RequestAuthPolicy.CAREGIVER,
        )
        return caregiverJson.decodeFromString<CaregiverSlotTimesEnvelopeDto>(body).data.slotTimes.toDomain()
    }

    override suspend fun issueLinkingCode(patientId: String): CaregiverLinkingCode {
        val body = client.postEmpty("api/patients/$patientId/linking-codes", RequestAuthPolicy.CAREGIVER)
        return caregiverJson.decodeFromString<CaregiverLinkingCodeEnvelopeDto>(body).data.toDomain()
    }

    override suspend fun revokePatient(patientId: String) {
        client.postEmpty("api/patients/$patientId/revoke", RequestAuthPolicy.CAREGIVER)
    }

    override suspend fun deletePatient(patientId: String) {
        client.deleteBody("api/patients/$patientId", RequestAuthPolicy.CAREGIVER)
    }

    override suspend fun deleteCaregiverAccount() {
        client.deleteBody("api/me", RequestAuthPolicy.CAREGIVER)
    }
}

class CaregiverPatientRepository(
    private val dataSource: CaregiverPatientDataSource,
    private val selectionRepository: CaregiverSelectionRepository,
    private val freshnessStore: MutationFreshnessStore = MutationFreshnessStore(),
) {
    private val mutableState = MutableStateFlow(
        CaregiverPatientState(selectedPatientId = selectionRepository.state.value.patientId),
    )
    val state: StateFlow<CaregiverPatientState> = mutableState.asStateFlow()

    suspend fun refresh() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        val hasContent = mutableState.value.hasLoaded
        mutableState.value = mutableState.value.copy(
            loading = !hasContent,
            refreshing = hasContent,
            loadFailed = false,
            refreshFailed = false,
        )
        try {
            acceptPatients(dataSource.listPatients())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(
                loading = false,
                refreshing = false,
                loadFailed = !hasContent,
                refreshFailed = hasContent,
            )
        }
    }

    fun selectPatient(patientId: String?) {
        val validId = patientId?.takeIf { candidate -> mutableState.value.patients.any { it.id == candidate } }
        selectionRepository.select(validId)
        mutableState.value = mutableState.value.copy(
            selectedPatientId = validId,
            linkingCode = null,
            linkingCodeFailed = false,
        )
    }

    suspend fun createPatient(displayName: String): Boolean {
        if (mutableState.value.refreshFailed) {
            mutableState.value = mutableState.value.copy(createError = CaregiverCreateError.FAILED)
            return false
        }
        val normalized = displayName.trim()
        val validation = when {
            normalized.isEmpty() -> CaregiverCreateError.REQUIRED
            normalized.length > 50 -> CaregiverCreateError.TOO_LONG
            else -> null
        }
        if (validation != null) {
            mutableState.value = mutableState.value.copy(createError = validation)
            return false
        }
        mutableState.value = mutableState.value.copy(creating = true, createError = null)
        return try {
            val created = dataSource.createPatient(normalized)
            val patients = listOf(created) + mutableState.value.patients.filterNot { it.id == created.id }
            selectionRepository.select(created.id)
            mutableState.value = mutableState.value.copy(
                patients = patients,
                selectedPatientId = created.id,
                creating = false,
                linkingCode = null,
                linkingCodeFailed = false,
            )
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(
                creating = false,
                createError = if (error is ApiException.PatientLimitExceeded) CaregiverCreateError.PATIENT_LIMIT else CaregiverCreateError.FAILED,
            )
            false
        }
    }

    suspend fun updateSelectedPatientSlotTimes(slotTimes: CaregiverSlotTimes): Boolean {
        if (mutableState.value.refreshFailed) {
            mutableState.value = mutableState.value.copy(slotTimesSaveFailed = true)
            return false
        }
        val patientId = mutableState.value.selectedPatientId ?: return false
        if (!slotTimes.isValid()) {
            mutableState.value = mutableState.value.copy(slotTimesSaveFailed = true)
            return false
        }
        mutableState.value = mutableState.value.copy(savingSlotTimes = true, slotTimesSaveFailed = false)
        return try {
            val saved = dataSource.updateSlotTimes(patientId, slotTimes)
            mutableState.value = mutableState.value.copy(
                patients = mutableState.value.patients.map { patient ->
                    if (patient.id == patientId) patient.copy(slotTimes = saved) else patient
                },
                savingSlotTimes = false,
            )
            freshnessStore.markSlotTimesChanged()
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(savingSlotTimes = false, slotTimesSaveFailed = true)
            false
        }
    }

    suspend fun issueLinkingCode(): Boolean {
        if (mutableState.value.refreshFailed) {
            mutableState.value = mutableState.value.copy(linkingCodeFailed = true)
            return false
        }
        val patientId = mutableState.value.selectedPatientId ?: return false
        mutableState.value = mutableState.value.copy(issuingLinkingCode = true, linkingCodeFailed = false)
        return try {
            val issued = dataSource.issueLinkingCode(patientId)
            mutableState.value = mutableState.value.copy(
                linkingCode = issued,
                issuingLinkingCode = false,
            )
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(issuingLinkingCode = false, linkingCodeFailed = true)
            false
        }
    }

    fun dismissLinkingCode() {
        mutableState.value = mutableState.value.copy(linkingCode = null)
    }

    suspend fun revokeSelectedPatient(): Boolean = mutateSelectedPatient(dataSource::revokePatient)

    suspend fun deleteSelectedPatient(): Boolean = mutateSelectedPatient(dataSource::deletePatient)

    suspend fun deleteCaregiverAccount(): Boolean {
        mutableState.value = mutableState.value.copy(destructiveActionInProgress = true, destructiveActionFailed = false)
        return try {
            dataSource.deleteCaregiverAccount()
            selectionRepository.clear()
            mutableState.value = CaregiverPatientState()
            freshnessStore.markPatientScopeChanged()
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(destructiveActionInProgress = false, destructiveActionFailed = true)
            false
        }
    }

    fun clear() {
        selectionRepository.clear()
        mutableState.value = CaregiverPatientState()
    }

    private fun acceptPatients(patients: List<CaregiverPatient>) {
        val storedId = selectionRepository.state.value.patientId
        val resolvedId = when {
            storedId != null && patients.any { it.id == storedId } -> storedId
            patients.size == 1 -> patients.single().id
            else -> null
        }
        if (resolvedId != storedId) selectionRepository.select(resolvedId)
        mutableState.value = CaregiverPatientState(
            patients = patients,
            selectedPatientId = resolvedId,
            hasLoaded = true,
        )
    }

    private suspend fun mutateSelectedPatient(action: suspend (String) -> Unit): Boolean {
        if (mutableState.value.refreshFailed) {
            mutableState.value = mutableState.value.copy(destructiveActionFailed = true)
            return false
        }
        val patientId = mutableState.value.selectedPatientId ?: return false
        mutableState.value = mutableState.value.copy(destructiveActionInProgress = true, destructiveActionFailed = false)
        return try {
            action(patientId)
            val remaining = mutableState.value.patients.filterNot { it.id == patientId }
            val nextId = remaining.singleOrNull()?.id
            selectionRepository.select(nextId)
            mutableState.value = mutableState.value.copy(
                patients = remaining,
                selectedPatientId = nextId,
                linkingCode = null,
                destructiveActionInProgress = false,
            )
            freshnessStore.markPatientScopeChanged()
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(destructiveActionInProgress = false, destructiveActionFailed = true)
            false
        }
    }
}

private val caregiverJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class CaregiverPatientListDto(val data: List<CaregiverPatientDto>)

@Serializable
private data class CaregiverPatientEnvelopeDto(val data: CaregiverPatientDto)

@Serializable
private data class CaregiverPatientCreateDto(val displayName: String)

@Serializable
private data class CaregiverSlotTimesUpdateDto(val slotTimes: CaregiverSlotTimesDto)

@Serializable
private data class CaregiverSlotTimesEnvelopeDto(val data: CaregiverSlotTimesDataDto)

@Serializable
private data class CaregiverSlotTimesDataDto(val slotTimes: CaregiverSlotTimesDto)

@Serializable
private data class CaregiverLinkingCodeEnvelopeDto(val data: CaregiverLinkingCodeDto)

@Serializable
private data class CaregiverLinkingCodeDto(val code: String, val expiresAt: String) {
    fun toDomain() = CaregiverLinkingCode(code, expiresAt)
}

@Serializable
private data class CaregiverPatientDto(
    val id: String,
    val displayName: String,
    val slotTimes: CaregiverSlotTimesDto? = null,
) {
    fun toDomain() = CaregiverPatient(id, displayName, slotTimes?.toDomain())
}

@Serializable
private data class CaregiverSlotTimesDto(
    val morning: String,
    val noon: String,
    val evening: String,
    val bedtime: String,
) {
    fun toDomain() = CaregiverSlotTimes(morning, noon, evening, bedtime)
}

private fun CaregiverSlotTimes.toDto() = CaregiverSlotTimesDto(morning, noon, evening, bedtime)

private fun CaregiverSlotTimes.isValid(): Boolean = listOf(morning, noon, evening, bedtime).all {
    TIME_PATTERN.matches(it)
}

private val TIME_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
