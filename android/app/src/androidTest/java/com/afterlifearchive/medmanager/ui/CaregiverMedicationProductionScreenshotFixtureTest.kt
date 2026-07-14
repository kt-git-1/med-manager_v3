package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class CaregiverMedicationProductionScreenshotFixtureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun medicationListAndFormFixturesUseProductionComponents() {
        val items = listOf(
            medication("blood-pressure", "血圧の薬", "5 mg", false, 18.0, listOf("morning", "noon")),
            medication("digestive", "整腸剤", "50 mg", false, 10.0, listOf("evening")),
            medication("pain", "痛み止め", "200 mg", true, 6.0, null),
        )
        val repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { items }, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "田中 花子", CaregiverSlotTimes("08:00", "13:00", "19:00", "22:00"))
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverMedicationScreen(repository, CaregiverPatientState(listOf(patient), patient.id), enabled = true)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-medication-list").assertExists()
        saveCapture("03-android-caregiver-medications-light.png")

        composeRule.onNodeWithTag("caregiver-medication-add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-medication-form").assertExists()
        saveCapture("04-android-medication-form-light.png")
    }

    private fun saveCapture(name: String) {
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), name)
    }

    private fun medication(
        id: String,
        name: String,
        dosage: String,
        isPrn: Boolean,
        inventory: Double,
        times: List<String>?,
    ) = PatientMedication(
        id = id,
        patientId = "patient-1",
        name = name,
        dosageText = dosage,
        doseCountPerIntake = 1.0,
        dosageStrengthValue = dosage.substringBefore(' ').toDouble(),
        dosageStrengthUnit = "mg",
        notes = null,
        isPrn = isPrn,
        prnInstructions = if (isPrn) "痛い時に1錠" else null,
        startDate = Instant.parse("2026-07-01T00:00:00Z"),
        endDate = null,
        inventoryCount = inventory,
        inventoryUnit = "錠",
        inventoryEnabled = true,
        inventoryQuantity = inventory,
        inventoryOut = false,
        isActive = true,
        isArchived = false,
        nextScheduledAt = null,
        regimenTimes = times,
        regimenDaysOfWeek = emptyList(),
    )
}
