package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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

data class CaregiverPatientState(
    val patients: List<CaregiverPatient> = emptyList(),
    val selectedPatientId: String? = null,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    val selectedPatient: CaregiverPatient?
        get() = patients.firstOrNull { it.id == selectedPatientId }
}

fun interface CaregiverPatientDataSource {
    suspend fun listPatients(): List<CaregiverPatient>
}

class CaregiverPatientApi(private val client: ApiClient) : CaregiverPatientDataSource {
    override suspend fun listPatients(): List<CaregiverPatient> {
        val body = client.getBody("api/patients", RequestAuthPolicy.CAREGIVER)
        return caregiverJson.decodeFromString<CaregiverPatientListDto>(body).data.map { it.toDomain() }
    }
}

class CaregiverPatientRepository(
    private val dataSource: CaregiverPatientDataSource,
    private val selectionRepository: CaregiverSelectionRepository,
) {
    private val mutableState = MutableStateFlow(
        CaregiverPatientState(selectedPatientId = selectionRepository.state.value.patientId),
    )
    val state: StateFlow<CaregiverPatientState> = mutableState.asStateFlow()

    suspend fun refresh() {
        if (mutableState.value.loading) return
        mutableState.value = mutableState.value.copy(loading = true, loadFailed = false)
        try {
            acceptPatients(dataSource.listPatients())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(loading = false, loadFailed = true)
        }
    }

    fun selectPatient(patientId: String?) {
        val validId = patientId?.takeIf { candidate -> mutableState.value.patients.any { it.id == candidate } }
        selectionRepository.select(validId)
        mutableState.value = mutableState.value.copy(selectedPatientId = validId)
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
        )
    }
}

private val caregiverJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class CaregiverPatientListDto(val data: List<CaregiverPatientDto>)

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
