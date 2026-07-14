package com.afterlifearchive.medmanager

import android.app.Application
import com.afterlifearchive.medmanager.data.auth.SupabaseAuthService
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.patient.PatientApi
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.session.AndroidSessionStorage
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MedicationApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var patientRepository: PatientRepository
        private set
    lateinit var caregiverSelectionRepository: CaregiverSelectionRepository
        private set
    lateinit var mutationFreshnessStore: MutationFreshnessStore
        private set

    override fun onCreate() {
        super.onCreate()
        val storage = AndroidSessionStorage(this)
        caregiverSelectionRepository = CaregiverSelectionRepository(storage)
        val authService = SupabaseAuthService(AppConfig.fromBuildConfig())
        lateinit var repository: SessionRepository
        val apiClient = ApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            patientTokenProvider = { repository.patientAuthorizationToken() },
            caregiverTokenProvider = { repository.caregiverAuthorizationToken() },
            refreshPatientIfNeeded = { repository.refreshPatientIfNeeded() },
            forceRefreshPatient = { repository.forceRefreshPatientSession() },
            onPatientAuthFailure = { repository.invalidatePatientSession() },
            refreshCaregiverIfNeeded = { repository.refreshCaregiverIfNeeded() },
            onCaregiverAuthFailure = { repository.invalidateCaregiverSession() },
        )
        repository = SessionRepository(
            storage = storage,
            authService = authService,
            apiClient = apiClient,
            caregiverSelection = caregiverSelectionRepository,
        )
        sessionRepository = repository
        mutationFreshnessStore = MutationFreshnessStore()
        patientRepository = PatientRepository(PatientApi(apiClient), mutationFreshnessStore)
        repository.restore()
        ReminderScheduler.createNotificationChannel(this)

        val reminderMaintenance = PatientReminderMaintenanceCoordinator(
            isPatientSessionActive = { repository.state.value.patientAuthenticated },
            settingsProvider = { PatientNotificationPreferences(this).load() },
            historyProvider = { patientRepository.notificationHistory() },
            replacePlan = { PatientNotificationScheduler.replace(this, it) },
            onSuccess = patientRepository::clearReminderMaintenanceWarning,
            onFailure = { patientRepository.reportReminderMaintenanceFailure() },
        )
        val schedulerCursor = mutationFreshnessStore.newCursor(FreshnessConsumer.NOTIFICATION_SCHEDULER)
        applicationScope.launch {
            mutationFreshnessStore.revisions.collect {
                schedulerCursor.refreshIfStale { reminderMaintenance.rebuildIfEnabled() }
            }
        }
    }
}
