package com.afterlifearchive.medmanager.data.patient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

data class PatientUiState(
    val doses: List<PatientDose> = emptyList(),
    val history: List<HistoryDay> = emptyList(),
    val loading: Boolean = false,
    val updatingDoseKey: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class PatientRepository(private val api: PatientDataSource) {
    private val mutableState = MutableStateFlow(PatientUiState())
    val state: StateFlow<PatientUiState> = mutableState.asStateFlow()

    suspend fun loadToday() {
        mutableState.value = mutableState.value.copy(loading = true, error = null, message = null)
        runCatching { api.today() }
            .onSuccess { mutableState.value = mutableState.value.copy(doses = it, loading = false) }
            .onFailure { mutableState.value = mutableState.value.copy(loading = false, error = it.message) }
    }

    suspend fun record(dose: PatientDose) {
        if (dose.status == DoseStatus.TAKEN) return
        mutableState.value = mutableState.value.copy(updatingDoseKey = dose.key, error = null, message = null)
        runCatching { api.recordDose(dose) }
            .onSuccess {
                val updated = mutableState.value.doses.map {
                    if (it.key == dose.key) it.copy(status = DoseStatus.TAKEN) else it
                }
                mutableState.value = mutableState.value.copy(
                    doses = updated,
                    updatingDoseKey = null,
                    message = "服薬を記録しました。",
                )
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(updatingDoseKey = null, error = it.message)
            }
    }

    suspend fun loadHistory(date: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))) {
        mutableState.value = mutableState.value.copy(loading = true, error = null, message = null)
        runCatching { api.history(date.year, date.monthValue) }
            .onSuccess { mutableState.value = mutableState.value.copy(history = it, loading = false) }
            .onFailure { mutableState.value = mutableState.value.copy(loading = false, error = it.message) }
    }

    suspend fun revokeSession(): Boolean {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        return runCatching { api.revokeSession() }
            .fold(
                onSuccess = {
                    mutableState.value = mutableState.value.copy(loading = false)
                    true
                },
                onFailure = {
                    mutableState.value = mutableState.value.copy(loading = false, error = it.message)
                    false
                },
            )
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(error = null, message = null)
    }
}
