package com.afterlifearchive.medmanager.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaregiverSelectionState(val patientId: String? = null)

class CaregiverSelectionRepository(private val storage: SessionStorage) {
    private val mutableState = MutableStateFlow(CaregiverSelectionState())
    val state: StateFlow<CaregiverSelectionState> = mutableState.asStateFlow()

    fun restore() {
        mutableState.value = CaregiverSelectionState(storage.currentPatientId)
    }

    fun select(patientId: String?) {
        val normalized = patientId?.trim()?.takeIf(String::isNotEmpty)
        storage.currentPatientId = normalized
        mutableState.value = CaregiverSelectionState(normalized)
    }

    fun clear() = select(null)
}
