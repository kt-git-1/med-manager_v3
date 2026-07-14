package com.afterlifearchive.medmanager

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        handlePatientNotificationIntent(intent, patientRepository)
        setContent {
            MedicationAppTheme {
                if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT_SETTINGS", false)) {
                    PatientModePreview(PatientTab.SETTINGS)
                } else if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT_HISTORY", false)) {
                    PatientModePreview(PatientTab.HISTORY)
                } else if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT", false)) {
                    PatientModePreview(PatientTab.TODAY)
                } else {
                    MedicationApp(repository, patientRepository, caregiverPatientRepository, caregiverMedicationRepository, caregiverTodayRepository, caregiverInventoryRepository)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { (application as MedicationApplication).sessionRepository.handleAuthCallback(it) }
        handlePatientNotificationIntent(intent, (application as MedicationApplication).patientRepository)
    }

    private fun handlePatientNotificationIntent(intent: Intent, repository: com.afterlifearchive.medmanager.data.patient.PatientRepository) {
        repository.handleNotificationTarget(
            intent.getStringExtra("notification_date"),
            intent.getStringExtra("notification_slot"),
        )
    }
}
