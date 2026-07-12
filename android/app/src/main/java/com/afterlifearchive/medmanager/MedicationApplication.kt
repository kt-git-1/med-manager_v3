package com.afterlifearchive.medmanager

import android.app.Application
import com.afterlifearchive.medmanager.data.auth.SupabaseAuthService
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.patient.PatientApi
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.session.AndroidSessionStorage
import com.afterlifearchive.medmanager.data.session.SessionRepository

class MedicationApplication : Application() {
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var patientRepository: PatientRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val storage = AndroidSessionStorage(this)
        val authService = SupabaseAuthService(AppConfig.fromBuildConfig())
        lateinit var repository: SessionRepository
        val apiClient = ApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { repository.authorizationToken() },
            isPatientSession = { repository.isPatientSession() },
            refreshPatientIfNeeded = { repository.refreshPatientIfNeeded() },
            forceRefreshPatient = { repository.forceRefreshPatientSession() },
            onPatientAuthFailure = { repository.invalidatePatientSession() },
        )
        repository = SessionRepository(storage, authService, apiClient)
        sessionRepository = repository
        patientRepository = PatientRepository(PatientApi(apiClient))
        repository.restore()
        ReminderScheduler.createNotificationChannel(this)
    }
}
