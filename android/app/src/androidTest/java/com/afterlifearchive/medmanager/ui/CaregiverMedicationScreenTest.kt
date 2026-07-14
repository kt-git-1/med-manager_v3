package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextEquals
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CaregiverMedicationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentShowsMetricsFiltersScheduleAndInventory() {
        setContent(listOf(scheduled(), prn(), ended()))

        composeRule.onNodeWithText("薬を管理").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-scheduled").assertIsDisplayed()
        composeRule.onNodeWithText("毎日 朝・夕").assertIsDisplayed()
        composeRule.onNodeWithText("残り 12 錠").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-filter-prn").performClick()
        composeRule.onNodeWithTag("caregiver-medication-prn").assertIsDisplayed()
    }

    @Test
    fun selectedPatientWithNoMedicationShowsCanonicalEmptyState() {
        setContent(emptyList())

        composeRule.onNodeWithText("薬がありません").assertIsDisplayed()
        composeRule.onNodeWithText("まず1つ目の薬を登録しましょう。", substring = true).assertIsDisplayed()
    }

    @Test
    fun addFormShowsConditionalPrnFieldAndValidationSummary() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-medication-add").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").assertIsDisplayed()
        composeRule.onNodeWithTag("medication-kind-prn").performClick()
        composeRule.onNodeWithTag("medication-prn-instructions").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-save"))
        composeRule.onNodeWithTag("medication-save").performClick()
        composeRule.onNodeWithTag("medication-validation-errors").assertIsDisplayed()
        composeRule.onNodeWithText("薬の名前を入力してください", substring = true).assertIsDisplayed()
    }

    @Test
    fun editFormIsPrepopulatedFromSelectedMedication() {
        setContent(listOf(scheduled()))

        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").assertIsDisplayed()
        composeRule.onNodeWithTag("medication-name").assertTextEquals("薬の名前", "アムロジピン")
        composeRule.onNodeWithTag("medication-strength-value").assertTextEquals("数値", "5")
        composeRule.onNodeWithTag("medication-slot-morning").assertIsDisplayed()
    }

    @Test
    fun regularScheduleSupportsWeeklyDaysAndPrnHidesSchedule() {
        setContent(emptyList())
        composeRule.onNodeWithTag("caregiver-medication-add").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-frequency-weekly"))
        composeRule.onNodeWithTag("medication-frequency-weekly").performClick()
        composeRule.onNodeWithTag("medication-day-mon").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("medication-slot-morning").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-kind-prn"))
        composeRule.onNodeWithTag("medication-kind-prn").performClick()
        composeRule.onNodeWithTag("medication-frequency-weekly").assertDoesNotExist()
    }

    @Test
    fun editFormRequiresExplicitDestructiveDeleteConfirmation() {
        setContent(listOf(scheduled()))
        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-delete"))
        composeRule.onNodeWithTag("medication-delete").performClick()

        composeRule.onNodeWithTag("medication-delete-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("薬を削除しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("この操作は取り消せません。").assertIsDisplayed()
    }

    private fun setContent(items: List<PatientMedication>) {
        val repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { items }, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"))
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverMedicationScreen(repository, CaregiverPatientState(listOf(patient), patient.id), enabled = true)
            }
        }
        composeRule.waitForIdle()
    }

    private fun scheduled() = medication("scheduled", "アムロジピン", false, null, 12.0, listOf("08:00", "18:00"))
    private fun prn() = medication("prn", "ロキソニン", true, null, 0.0, null)
    private fun ended() = medication("ended", "終了薬", false, Instant.parse("2026-01-01T00:00:00Z"), 0.0, listOf("12:00"))

    private fun medication(
        id: String,
        name: String,
        isPrn: Boolean,
        endDate: Instant?,
        inventory: Double,
        times: List<String>?,
    ) = PatientMedication(
        id, "patient-1", name, "5mg", 1.0, 5.0, "mg", null, isPrn, null,
        Instant.parse("2025-01-01T00:00:00Z"), endDate, inventory, "錠", inventory > 0, inventory, false,
        endDate == null, false, null, times, emptyList(),
    )
}
