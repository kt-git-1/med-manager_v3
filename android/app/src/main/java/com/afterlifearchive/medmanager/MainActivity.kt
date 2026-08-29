package com.afterlifearchive.medmanager

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.afterlifearchive.medmanager.ui.CaregiverModePreview
import com.afterlifearchive.medmanager.ui.MedicationApp
import com.afterlifearchive.medmanager.ui.PatientModePreview
import com.afterlifearchive.medmanager.ui.PatientTab
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as MedicationApplication).sessionRepository
        intent.dataString?.let(repository::handleAuthCallback)
        val patientRepository = (application as MedicationApplication).patientRepository
        val caregiverPatientRepository = (application as MedicationApplication).caregiverPatientRepository
        val caregiverMedicationRepository = (application as MedicationApplication).caregiverMedicationRepository
        val caregiverTodayRepository = (application as MedicationApplication).caregiverTodayRepository
        val caregiverInventoryRepository = (application as MedicationApplication).caregiverInventoryRepository
        val caregiverHistoryRepository = (application as MedicationApplication).caregiverHistoryRepository
        val caregiverReportRepository = (application as MedicationApplication).caregiverReportRepository
        val caregiverPushRepository = (application as MedicationApplication).caregiverPushRepository
        val analyticsService = (application as MedicationApplication).analyticsService
        if (BuildConfig.DEBUG && listOf("PREVIEW_CAREGIVER", "PREVIEW_PATIENT_SETTINGS", "PREVIEW_PATIENT_HISTORY", "PREVIEW_PATIENT").any { intent.getBooleanExtra(it, false) }) {
            analyticsService.setSessionSuppressed(true)
        }
        handlePatientNotificationIntent(intent, patientRepository, analyticsService)
        handleCaregiverNotificationIntent(intent, caregiverHistoryRepository, analyticsService)
        setContent {
            MedicationAppTheme {
                if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_CAREGIVER", false)) {
                    CaregiverModePreview()
                } else if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT_SETTINGS", false)) {
                    PatientModePreview(PatientTab.SETTINGS)
                } else if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT_HISTORY", false)) {
                    PatientModePreview(PatientTab.HISTORY)
                } else if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT", false)) {
                    PatientModePreview(PatientTab.TODAY)
                } else {
                    MedicationApp(repository, patientRepository, caregiverPatientRepository, caregiverMedicationRepository, caregiverTodayRepository, caregiverInventoryRepository, caregiverHistoryRepository, caregiverReportRepository, caregiverPushRepository, analyticsService)
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { (application as MedicationApplication).sessionRepository.handleAuthCallback(it) }
        val app = application as MedicationApplication
        handlePatientNotificationIntent(intent, app.patientRepository, app.analyticsService)
        handleCaregiverNotificationIntent(intent, app.caregiverHistoryRepository, app.analyticsService)
    }

    private fun handlePatientNotificationIntent(
        intent: Intent,
        repository: com.afterlifearchive.medmanager.data.patient.PatientRepository,
        analyticsService: AnalyticsService,
    ) {
        val handled = repository.handleNotificationTarget(
            intent.getStringExtra("notification_date"),
            intent.getStringExtra("notification_slot"),
        )
        if (handled) {
            analyticsService.logNotificationOpened(AnalyticsNotificationSource.LOCAL_REMINDER)
            intent.removeExtra("notification_date")
            intent.removeExtra("notification_slot")
        }
    }

    private fun handleCaregiverNotificationIntent(
        intent: Intent,
        repository: com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository,
        analyticsService: AnalyticsService,
    ) {
        val handled = repository.handleNotificationTarget(
            intent.getStringExtra("type"),
            intent.getStringExtra("patientId"),
            intent.getStringExtra("date"),
            intent.getStringExtra("slot"),
        )
        if (handled) {
            analyticsService.logNotificationOpened(AnalyticsNotificationSource.REMOTE_PUSH)
            intent.removeExtra("type")
            intent.removeExtra("patientId")
            intent.removeExtra("date")
            intent.removeExtra("slot")
        }
    }
}
