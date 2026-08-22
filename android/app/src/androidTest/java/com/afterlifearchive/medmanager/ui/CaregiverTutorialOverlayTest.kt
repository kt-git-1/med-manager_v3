package com.afterlifearchive.medmanager.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverLinkingCode
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
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
        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.onNodeWithText("通知をオンにする").performClick()

        assertTrue(skip == 1 && previous == 1 && next == 1)
    }

    @Test
    fun homeTutorialMovesPublishedSamplesAndPersistsSkip() {
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
        composeRule.onNodeWithTag("caregiver-tutorial-sample").assertIsDisplayed()
        composeRule.onAllNodesWithTag("caregiver-tab-today").assertCountEquals(0)
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "家族モードの使い方 1/10"),
        ).assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("血圧の薬", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()
        composeRule.onNodeWithText("薬を登録・編集").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-sample-medications").assertIsDisplayed()
        composeRule.onNodeWithText("残り18錠").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-skip").performClick()

        composeRule.onAllNodesWithTag("caregiver-tutorial").assertCountEquals(0)
        assertTrue(preferences.getBoolean("seen", false))
    }

    @Test
    fun stepSevenOpensRealPatientCreationOnlyAfterExplicitAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("caregiver_tutorial", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val storage = TutorialStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { emptyList() }, selection)
        composeRule.setContent {
            MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = true) }
        }

        repeat(6) { composeRule.onNodeWithTag("caregiver-tutorial-next").performClick() }

        composeRule.onNodeWithTag("caregiver-tutorial-sample-register").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial").assertIsDisplayed()
        composeRule.onAllNodesWithTag("caregiver-create-sheet").assertCountEquals(0)
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()
        composeRule.onNodeWithTag("caregiver-create-sheet").assertIsDisplayed()
        assertTrue(!preferences.getBoolean("seen", false))
    }

    @Test
    fun successfulPatientAndCodeActionsAdvancePagesSevenThroughNine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("caregiver_tutorial", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val patients = mutableListOf<CaregiverPatient>()
        val dataSource = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = patients.toList()
            override suspend fun createPatient(displayName: String) =
                CaregiverPatient("created", displayName).also(patients::add)
            override suspend fun issueLinkingCode(patientId: String) =
                CaregiverLinkingCode("123456", "2026-08-21T18:00:00Z")
        }
        val selection = CaregiverSelectionRepository(TutorialStorage()).also { it.restore() }
        val repository = CaregiverPatientRepository(dataSource, selection)
        val medicationRepository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { emptyList() },
            MutationFreshnessStore(),
        )
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHomeScreen(
                    repository,
                    medicationRepository = medicationRepository,
                    tutorialEnabled = true,
                )
            }
        }

        repeat(6) { composeRule.onNodeWithTag("caregiver-tutorial-next").performClick() }
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()
        composeRule.onNodeWithTag("caregiver-create-name").performTextInput("さくら")
        composeRule.onNodeWithTag("caregiver-create-submit").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.selectedPatientId == "created" }
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "家族モードの使い方 8/10"),
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.linkingCode != null }
        composeRule.onNodeWithTag("caregiver-linking-code-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-linking-code-sheet").performTouchInput { swipeDown() }
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "家族モードの使い方 9/10"),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-sample-register-medication").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("caregiver-guided-medication-step").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("caregiver-medication-form").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-guided-medication-step").assertIsDisplayed()
    }

    @Test
    fun publishedDedicatedSampleCoversAllTenTutorialSteps() {
        val step = mutableIntStateOf(0)
        composeRule.setContent {
            MedicationAppTheme { CaregiverTutorialSampleScreen(step.intValue) }
        }

        val expected = listOf(
            "caregiver-tutorial-sample-today" to "飲み遅れが1回ありました",
            "caregiver-tutorial-sample-medications" to "残り18錠",
            "caregiver-tutorial-sample-inventory" to "あと2日分",
            "caregiver-tutorial-sample-history" to "6月10日（水）",
            "caregiver-tutorial-sample-settings" to "通知を受け取る",
            "caregiver-tutorial-sample-time-preset" to "朝・昼・夜・眠前の時刻を変更できます",
            "caregiver-tutorial-sample-register" to "本人の名前を入力して保存します。",
            "caregiver-tutorial-sample-issue-code" to "連携コードを発行",
            "caregiver-tutorial-sample-register-medication" to "残り18錠",
            "caregiver-tutorial-sample-notification" to "服薬記録をすぐ確認できます",
        )
        expected.forEachIndexed { index, (tag, text) ->
            composeRule.runOnIdle { step.intValue = index }
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun publishedDedicatedSampleReferenceFixturesCaptureAllTenSteps() {
        val step = mutableIntStateOf(0)
        composeRule.setContent {
            MedicationAppTheme {
                Box(Modifier.fillMaxSize()) {
                    CaregiverTutorialSampleScreen(step.intValue)
                    CaregiverTutorialOverlay(step.intValue, {}, {}, {})
                }
            }
        }

        val names = listOf("today", "medications", "inventory", "history", "settings", "time-preset", "register", "issue-code", "register-medication", "notification")
        names.forEachIndexed { index, name ->
            composeRule.runOnIdle { step.intValue = index }
            composeRule.onNodeWithTag("caregiver-tutorial-sample-$name").assertIsDisplayed()
            SystemClock.sleep(120)
            writeDeviceScreenshotFixture("android-ui-200-caregiver-tutorial-$name-light-matched.png")
        }
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
    fun dedicatedSettingsSampleRemainsScrollableInDarkMaximumText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MedicationAppTheme(darkTheme = true) { CaregiverTutorialSampleScreen(4) }
            }
        }

        composeRule.onNodeWithText("通知を受け取る").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun finalOverlayPrimaryRequestsItsAction() {
        var permissionRequests = 0
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTutorialOverlay(9, {}, {}, { permissionRequests += 1 })
            }
        }

        composeRule.onNodeWithText("家族の服薬状況を通知しますか？").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tutorial-next").performClick()

        assertTrue(permissionRequests == 1)
    }
}

private class TutorialStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}
