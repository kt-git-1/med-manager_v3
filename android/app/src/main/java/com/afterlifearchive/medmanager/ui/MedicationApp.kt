package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.push.CaregiverPushRepository
import com.afterlifearchive.medmanager.data.session.SessionRepository
import com.afterlifearchive.medmanager.AnalyticsService
import kotlinx.coroutines.launch

@Composable
fun MedicationApp(
    repository: SessionRepository,
    patientRepository: PatientRepository,
    caregiverPatientRepository: CaregiverPatientRepository,
    caregiverMedicationRepository: CaregiverMedicationRepository,
    caregiverTodayRepository: CaregiverTodayRepository,
    caregiverInventoryRepository: CaregiverInventoryRepository,
    caregiverHistoryRepository: CaregiverHistoryRepository,
    caregiverReportRepository: CaregiverReportRepository,
    caregiverPushRepository: CaregiverPushRepository,
    analyticsService: AnalyticsService,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.mode, state.caregiverAuthenticated) {
        if (state.mode == AppMode.CAREGIVER && state.caregiverAuthenticated) {
            repository.refreshCaregiverIfNeeded()
            caregiverPushRepository.restoreIfEnabled()
        } else {
            caregiverPatientRepository.clear()
            caregiverMedicationRepository.clear()
            caregiverTodayRepository.clear()
            caregiverInventoryRepository.clear()
            caregiverHistoryRepository.clear()
            caregiverReportRepository.clear()
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state.mode) {
            null -> ModeSelectScreen(analyticsService, repository::selectMode)
            AppMode.CAREGIVER -> if (state.caregiverAuthenticated) {
                CaregiverHomeScreen(
                    caregiverPatientRepository,
                    caregiverMedicationRepository,
                    caregiverTodayRepository,
                    caregiverInventoryRepository,
                    caregiverHistoryRepository,
                    caregiverReportRepository,
                    caregiverPushRepository,
                    analyticsService,
                    onLogout = {
                        scope.launch {
                            caregiverPushRepository.disable()
                            repository.logoutCaregiver()
                        }
                    },
                    onAccountDeleted = {
                        caregiverPushRepository.clearAfterAccountDeletion()
                        repository.logoutCaregiver()
                    },
                )
            } else {
                CaregiverAuthFlow(state, repository)
            }
            AppMode.PATIENT -> if (state.patientAuthenticated) {
                PatientHomeScreen(patientRepository, repository::unlinkPatient, analyticsService)
            } else {
                PatientLinkScreen(state, repository)
            }
        }
    }
}
