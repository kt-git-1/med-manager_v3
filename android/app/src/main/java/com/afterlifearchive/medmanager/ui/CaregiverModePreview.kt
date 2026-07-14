package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage

@Composable
internal fun CaregiverModePreview() {
    val patientName = stringResource(R.string.caregiver_preview_patient_name)
    val repository = remember(patientName) {
        val selection = CaregiverSelectionRepository(CaregiverPreviewSessionStorage()).also { it.restore() }
        CaregiverPatientRepository(
            dataSource = CaregiverPatientDataSource {
                listOf(
                    CaregiverPatient(
                        id = CAREGIVER_PREVIEW_PATIENT_ID,
                        displayName = patientName,
                        slotTimes = CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"),
                    ),
                )
            },
            selectionRepository = selection,
        )
    }
    CaregiverHomeScreen(repository = repository, tutorialEnabled = false)
}

private class CaregiverPreviewSessionStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = CAREGIVER_PREVIEW_PATIENT_ID
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}

private const val CAREGIVER_PREVIEW_PATIENT_ID = "preview-caregiver-patient"
