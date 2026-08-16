package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.PatientDataSource
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PatientTutorialOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun clearTutorialPreference() {
        tutorialPreferences().edit().clear().commit()
    }

    @Test
    fun firstStepShowsCanonicalCopyAndNextAction() {
        var nextCount = 0
        composeRule.setContent {
            MedicationAppTheme { PatientTutorialOverlay(0, {}, {}, { nextCount += 1 }) }
        }

        composeRule.onNodeWithText("今日のお薬").assertIsDisplayed()
        composeRule.onNodeWithText("次に飲むお薬を確認します。飲んだら「今飲んだ」を押します。飲み遅れたお薬も今日の記録から残せます。").assertIsDisplayed()
        composeRule.onNodeWithText("1/4").assertIsDisplayed()
        composeRule.onNodeWithText("次へ").performClick()
        composeRule.runOnIdle { assertEquals(1, nextCount) }
    }

    @Test
    fun finalStepOffersNotificationSetupAndSupportsBackAndSkip() {
        var skip = 0
        var previous = 0
        var next = 0
        composeRule.setContent {
            MedicationAppTheme { PatientTutorialOverlay(3, { skip += 1 }, { previous += 1 }, { next += 1 }) }
        }

        composeRule.onNodeWithText("お薬の時間に通知しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("朝・昼・夜など、設定した時間にお薬の通知を受け取れます。通知をオンにすると、飲み忘れに気づきやすくなります。").assertIsDisplayed()
        composeRule.onNodeWithText("通知をオンにする").performClick()
        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.onNodeWithText("あとで設定する").performClick()
        composeRule.runOnIdle {
            assertEquals(1, next)
            assertEquals(1, previous)
            assertEquals(1, skip)
        }
    }

    @Test
    fun homeTutorialMovesPublishedSamplesAndPersistsSkip() {
        val preferences = tutorialPreferences().also { it.edit().clear().commit() }
        val repository = PatientRepository(TutorialPatientDataSource())
        composeRule.setContent {
            MedicationAppTheme {
                PatientHomeScreen(
                    repository = repository,
                    onUnlink = {},
                    tutorialEnabled = true,
                    requestTutorialNotificationPermission = {},
                )
            }
        }

        composeRule.onNodeWithText("1/4").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-sample").assertIsDisplayed()
        composeRule.onAllNodesWithTag("patient-navigation-today").assertCountEquals(0)
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "患者モードの使い方 1/4"),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("次のお薬").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-next").performClick()
        composeRule.onNodeWithText("2/4").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-sample-history").assertIsDisplayed()
        composeRule.onNodeWithText("連続記録").assertIsDisplayed()
        composeRule.onNodeWithText("5日").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-skip").performClick()

        composeRule.onAllNodesWithTag("patient-tutorial").assertCountEquals(0)
        assertTrue(preferences.getBoolean("seen", false))
    }

    @Test
    fun homeTutorialFinalActionPersistsCompletionAndRequestsPermission() {
        val preferences = tutorialPreferences().also { it.edit().clear().commit() }
        val repository = PatientRepository(TutorialPatientDataSource())
        var permissionRequests = 0
        composeRule.setContent {
            MedicationAppTheme {
                PatientHomeScreen(
                    repository = repository,
                    onUnlink = {},
                    tutorialEnabled = true,
                    requestTutorialNotificationPermission = { permissionRequests += 1 },
                )
            }
        }

        repeat(3) { composeRule.onNodeWithTag("patient-tutorial-next").performClick() }
        composeRule.onNodeWithText("お薬の時間に通知しますか？").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-next").performClick()

        composeRule.onAllNodesWithTag("patient-tutorial").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, permissionRequests)
            assertTrue(preferences.getBoolean("seen", false))
        }
    }

    @Test
    fun todayReferenceFixtureCapturesProductionPatientShell() {
        captureReferenceFixture(0, PatientTab.TODAY, "today")
    }

    @Test
    fun historyReferenceFixtureCapturesProductionPatientShell() {
        captureReferenceFixture(1, PatientTab.HISTORY, "history")
    }

    @Test
    fun settingsReferenceFixtureCapturesProductionPatientShell() {
        captureReferenceFixture(2, PatientTab.SETTINGS, "settings")
    }

    @Test
    fun notificationReferenceFixtureCapturesProductionPatientShell() {
        captureReferenceFixture(3, PatientTab.TODAY, "notifications")
    }

    @Test
    fun publishedDedicatedSamplesContainCanonicalFixedCopy() {
        val tab = mutableStateOf(PatientTab.TODAY)
        composeRule.setContent {
            MedicationAppTheme { PatientTutorialSampleScreen(tab.value) }
        }

        composeRule.onNodeWithText("合計2錠（2種類）").assertIsDisplayed()
        composeRule.runOnIdle { tab.value = PatientTab.HISTORY }
        composeRule.onNodeWithText("1/3回分 記録済み").assertIsDisplayed()
        composeRule.onNodeWithText("連続記録").assertIsDisplayed()
        composeRule.runOnIdle { tab.value = PatientTab.SETTINGS }
        composeRule.onNodeWithText("通知を有効にする").assertIsDisplayed()
        composeRule.onNodeWithText("家族と服薬記録を共有しています").assertIsDisplayed()
    }

    @Test
    fun dedicatedHistoryAndOverlayActionsRemainReachableInDarkMaximumText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MedicationAppTheme(darkTheme = true) {
                    Box(Modifier.fillMaxSize()) {
                        PatientTutorialSampleScreen(PatientTab.HISTORY)
                        PatientTutorialOverlay(3, {}, {}, {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag("patient-tutorial-next").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-back").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("patient-tutorial-skip").performScrollTo().assertIsDisplayed()
    }

    private fun captureReferenceFixture(step: Int, tab: PatientTab, name: String) {
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize()) {
                    PatientTutorialSampleScreen(tab)
                    PatientTutorialOverlay(step, {}, {}, {})
                }
            }
        }
        composeRule.runOnIdle {
            normalizeStatusBar(activity)
        }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-100-patient-tutorial-$name-light.png")
    }

    @Suppress("DEPRECATION")
    private fun normalizeStatusBar(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = true
    }

    private fun tutorialPreferences() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("patient_tutorial", android.content.Context.MODE_PRIVATE)
}

private class TutorialPatientDataSource : PatientDataSource {
    override suspend fun today(): List<PatientDose> = emptyList()
    override suspend fun slotTimes(): PatientSlotTimes = PatientSlotTimes.DEFAULT
    override suspend fun recordDose(dose: PatientDose) = Unit
    override suspend fun history(year: Int, month: Int): List<HistoryDay> = emptyList()
    override suspend fun revokeSession() = Unit
}
