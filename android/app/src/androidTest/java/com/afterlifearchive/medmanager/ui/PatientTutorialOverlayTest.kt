package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PatientTutorialOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstStepShowsCanonicalCopyAndNextAction() {
        var nextCount = 0
        composeRule.setContent {
            MedicationAppTheme { PatientTutorialOverlay(0, {}, {}, { nextCount += 1 }) }
        }

        composeRule.onNodeWithText("今日のお薬").assertIsDisplayed()
        composeRule.onNodeWithText("飲むお薬を確認します。飲んだら「飲んだ」を押します。").assertIsDisplayed()
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

    private fun captureReferenceFixture(step: Int, tab: PatientTab, name: String) {
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize()) {
                    PatientModePreview(initialTab = tab)
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
}
