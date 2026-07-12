package com.afterlifearchive.medmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.afterlifearchive.medmanager.ui.MedicationApp
import com.afterlifearchive.medmanager.ui.PatientModePreview
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as MedicationApplication).sessionRepository
        val patientRepository = (application as MedicationApplication).patientRepository
        setContent {
            MedicationAppTheme {
                if (BuildConfig.DEBUG && intent.getBooleanExtra("PREVIEW_PATIENT", false)) {
                    PatientModePreview()
                } else {
                    MedicationApp(repository, patientRepository)
                }
            }
        }
    }
}
