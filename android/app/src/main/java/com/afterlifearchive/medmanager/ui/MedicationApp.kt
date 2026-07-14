package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.session.SessionRepository

@Composable
fun MedicationApp(
    repository: SessionRepository,
    patientRepository: PatientRepository,
    caregiverPatientRepository: CaregiverPatientRepository,
    caregiverMedicationRepository: CaregiverMedicationRepository,
    caregiverTodayRepository: CaregiverTodayRepository,
    caregiverInventoryRepository: CaregiverInventoryRepository,
    caregiverHistoryRepository: CaregiverHistoryRepository,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.mode, state.caregiverAuthenticated) {
        if (state.mode == AppMode.CAREGIVER && state.caregiverAuthenticated) {
            repository.refreshCaregiverIfNeeded()
        } else {
            caregiverPatientRepository.clear()
            caregiverMedicationRepository.clear()
            caregiverTodayRepository.clear()
            caregiverInventoryRepository.clear()
            caregiverHistoryRepository.clear()
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state.mode) {
            null -> ModeSelectScreen(repository::selectMode)
            AppMode.CAREGIVER -> if (state.caregiverAuthenticated) {
                CaregiverHomeScreen(
                    caregiverPatientRepository,
                    caregiverMedicationRepository,
                    caregiverTodayRepository,
                    caregiverInventoryRepository,
                    caregiverHistoryRepository,
                    onLogout = repository::logoutCaregiver,
                    onAccountDeleted = repository::logoutCaregiver,
                )
            } else {
                CaregiverAuthFlow(state, repository)
            }
            AppMode.PATIENT -> if (state.patientAuthenticated) {
                PatientHomeScreen(patientRepository, repository::unlinkPatient)
            } else {
                PatientLinkScreen(state, repository)
            }
        }
    }
}
