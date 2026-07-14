package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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

    private fun setContent(patients: List<CaregiverPatient>): Pair<CaregiverPatientRepository, TestSelectionStorage> {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { patients }, selection)
        composeRule.setContent { MedicationAppTheme { CaregiverHomeScreen(repository) } }
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
