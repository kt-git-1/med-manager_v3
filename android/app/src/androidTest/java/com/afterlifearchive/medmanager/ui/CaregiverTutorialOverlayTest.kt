package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaregiverTutorialOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun canonicalTenStepCopyAndFinalActionsAreOperable() {
        var skip = 0
        var previous = 0
        var next = 0
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTutorialOverlay(9, { skip += 1 }, { previous += 1 }, { next += 1 })
            }
        }

        composeRule.onNodeWithText("家族の服薬状況を通知しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("10/10").assertIsDisplayed()
        composeRule.onNodeWithText("あとで設定する").performClick()
        composeRule.onNodeWithText("戻る").performClick()
        composeRule.onNodeWithText("通知をオンにする").performClick()

        assertTrue(skip == 1 && previous == 1 && next == 1)
    }

    @Test
    fun homeTutorialMovesTheRealTabsAndPersistsSkip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("caregiver_tutorial", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val storage = TutorialStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { emptyList() }, selection)
        composeRule.setContent {
            MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = true) }
        }

        composeRule.onNodeWithText("今日の予定を確認").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()
        composeRule.onNodeWithText("薬を登録・編集").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-content-medications").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-skip").performClick()

        composeRule.onAllNodesWithTag("caregiver-tutorial").assertCountEquals(0)
        assertTrue(preferences.getBoolean("seen", false))
    }

    @Test
    fun finalActionsAndPaneSemanticsRemainReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MedicationAppTheme { CaregiverTutorialOverlay(9, {}, {}, {}) }
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "家族モードの使い方 10/10"),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-skip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-back").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-next").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun finalPrimaryOpensRealRegistrationAndRequestsNotificationPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("caregiver_tutorial", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val storage = TutorialStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { emptyList() }, selection)
        var permissionRequests = 0
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHomeScreen(
                    repository,
                    tutorialEnabled = true,
                    requestNotificationPermission = { permissionRequests += 1 },
                )
            }
        }

        repeat(9) { composeRule.onNodeWithTag("caregiver-tutorial-next").performClick() }
        composeRule.onNodeWithText("家族の服薬状況を通知しますか？").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()

        composeRule.onAllNodesWithTag("caregiver-tutorial").assertCountEquals(0)
        composeRule.onNodeWithTag("caregiver-create-name").assertIsDisplayed()
        assertTrue(permissionRequests == 1)
        assertTrue(preferences.getBoolean("seen", false))
    }
}

private class TutorialStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}
