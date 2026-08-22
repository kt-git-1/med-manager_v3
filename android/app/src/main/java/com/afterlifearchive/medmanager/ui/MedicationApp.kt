package com.afterlifearchive.medmanager.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import com.afterlifearchive.medmanager.R
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
import kotlinx.coroutines.delay
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
    var showSplash by remember { mutableStateOf(true) }
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
        if (showSplash) {
            AppSplashScreen(onFinished = { showSplash = false })
        } else when (state.mode) {
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

@Composable
internal fun AppSplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "splashOpacity",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = tween(durationMillis = 700),
        label = "splashScale",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(2_000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(180.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(40.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                )
                .graphicsLayer {
                    alpha = opacity
                    scaleX = scale
                    scaleY = scale
                }
                .testTag("app-splash-logo"),
        )
    }
}
