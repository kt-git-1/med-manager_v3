package com.afterlifearchive.medmanager

import android.app.Application
import com.afterlifearchive.medmanager.data.auth.SupabaseAuthService
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientApi
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationApi
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayApi
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryApi
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryApi
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportApi
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.patient.PatientApi
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.push.AndroidCaregiverPushStorage
import com.afterlifearchive.medmanager.data.push.CaregiverPushApi
import com.afterlifearchive.medmanager.data.push.CaregiverPushRepository
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
    lateinit var caregiverPatientRepository: CaregiverPatientRepository
        private set
    lateinit var caregiverMedicationRepository: CaregiverMedicationRepository
        private set
    lateinit var caregiverTodayRepository: CaregiverTodayRepository
        private set
    lateinit var caregiverInventoryRepository: CaregiverInventoryRepository
        private set
    lateinit var caregiverHistoryRepository: CaregiverHistoryRepository
        private set
    lateinit var caregiverReportRepository: CaregiverReportRepository
        private set
    lateinit var caregiverPushRepository: CaregiverPushRepository
        private set
    lateinit var analyticsService: AnalyticsService
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
        caregiverPatientRepository = CaregiverPatientRepository(
            CaregiverPatientApi(apiClient),
            caregiverSelectionRepository,
            mutationFreshnessStore,
        )
        caregiverMedicationRepository = CaregiverMedicationRepository(
            CaregiverMedicationApi(apiClient),
            mutationFreshnessStore,
        )
        caregiverTodayRepository = CaregiverTodayRepository(
            CaregiverTodayApi(apiClient),
            mutationFreshnessStore,
        )
        caregiverInventoryRepository = CaregiverInventoryRepository(
            CaregiverInventoryApi(apiClient),
            mutationFreshnessStore,
        )
        caregiverHistoryRepository = CaregiverHistoryRepository(
            CaregiverHistoryApi(apiClient),
            mutationFreshnessStore,
        )
        caregiverReportRepository = CaregiverReportRepository(CaregiverReportApi(apiClient))
        caregiverPushRepository = CaregiverPushRepository(
            dataSource = CaregiverPushApi(apiClient, if (BuildConfig.DEBUG) "DEV" else "PROD"),
            tokenSource = FirebaseCaregiverPushTokenSource(this),
            storage = AndroidCaregiverPushStorage(this),
        )
        analyticsService = AnalyticsService(
            AnalyticsConsentPreferences(this),
            FirebaseAnalyticsTransport(this),
            environmentSuppressed = { System.getProperty("robolectric") != null },
        ).also { it.configure() }
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

    fun handleCaregiverPushToken(token: String) {
        applicationScope.launch { caregiverPushRepository.onNewToken(token) }
    }
}
