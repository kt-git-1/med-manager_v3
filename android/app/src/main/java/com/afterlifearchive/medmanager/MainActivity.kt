package com.afterlifearchive.medmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.afterlifearchive.medmanager.ui.MedicationApp
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedicationAppTheme {
                MedicationApp()
            }
        }
    }
}
