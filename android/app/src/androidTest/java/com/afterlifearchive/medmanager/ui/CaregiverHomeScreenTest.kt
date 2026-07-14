package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CaregiverHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startsOnTodayAndExposesFiveTabsInCurrentIosOrder() {
        setContent(listOf(CaregiverPatient("patient-1", "さくら")))

        composeRule.onNodeWithTag("caregiver-content-today").assertIsDisplayed()
        listOf("today", "medications", "inventory", "history", "settings").forEach {
            composeRule.onNodeWithTag("caregiver-tab-$it").assertIsDisplayed()
        }
    }

    @Test
    fun settingsUsesAutoSelectedSolePatientAndSelectionPersistsAcrossTabs() {
        val (repository, storage) = setContent(listOf(CaregiverPatient("patient-1", "さくら")))

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithText("さくら").assertIsDisplayed()
        composeRule.onNodeWithText("選択中").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-slot-times"))
        composeRule.onNodeWithTag("caregiver-slot-times").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-linking-code-issue"))
        composeRule.onNodeWithTag("caregiver-linking-code-issue").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-patient-revoke"))
        composeRule.onNodeWithTag("caregiver-patient-revoke").performClick()
        composeRule.onNodeWithText("既存の本人セッションは失効しますが、データは保持されます。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("キャンセル").performClick()
        composeRule.onNodeWithTag("caregiver-tab-today").performClick()
        composeRule.onNodeWithText("さくらさんを見守り中").assertIsDisplayed()

        assertEquals("patient-1", repository.state.value.selectedPatientId)
        assertEquals("patient-1", storage.currentPatientId)
    }

    @Test
    fun noPatientStateIsSharedByTodayAndSettings() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-feature-state").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-empty").assertIsDisplayed()
    }

    @Test
    fun createFormRejectsNamesOverFiftyCharactersLocally() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-create-name").performTextInput("x".repeat(51))
        composeRule.onNodeWithTag("caregiver-create-submit").performClick()
        composeRule.onNodeWithText("表示名は50文字以内で入力してください").assertIsDisplayed()
    }

    @Test
    fun remotePushSelectsTargetPatientAndHistoryTab() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val patientRepository = CaregiverPatientRepository(
            CaregiverPatientDataSource { listOf(CaregiverPatient("p1", "さくら"), CaregiverPatient("p2", "ゆうき")) },
            selection,
        )
        val historyRepository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = emptyList<HistoryDay>()
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        historyRepository.handleNotificationTarget("DOSE_TAKEN", "p2", "2026-07-15", "noon")

        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHomeScreen(patientRepository, historyRepository = historyRepository, tutorialEnabled = false)
            }
        }
        composeRule.waitUntil(5_000) { patientRepository.state.value.selectedPatientId == "p2" }

        composeRule.onNodeWithTag("caregiver-content-history").assertIsDisplayed()
        assertEquals("p2", storage.currentPatientId)
    }

    private fun setContent(patients: List<CaregiverPatient>): Pair<CaregiverPatientRepository, TestSelectionStorage> {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { patients }, selection)
        composeRule.setContent { MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) } }
        composeRule.waitForIdle()
        return repository to storage
    }
}

private class TestSelectionStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}
