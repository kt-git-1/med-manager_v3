package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.core.view.WindowCompat
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PatientSettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersNotificationLegalSupportAndConfirmedUnlink() {
        var settings = PatientNotificationSettings(masterEnabled = true)
        var analyticsEnabled = false
        val opened = mutableListOf<String>()
        composeRule.setContent {
            MedicationAppTheme {
                SettingsContent(
                    loading = false,
                    error = null,
                    notificationSettings = settings,
                    onNotificationSettingsChange = { settings = it },
                    notificationPermissionDenied = false,
                    analyticsEnabled = analyticsEnabled,
                    onAnalyticsEnabledChange = { analyticsEnabled = it },
                    onOpenUrl = opened::add,
                    onUnlink = {},
                )
            }
        }

        composeRule.onNodeWithText("通知を有効にする").assertIsDisplayed()
        composeRule.onNodeWithText("利用状況データを送信する").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-analytics-toggle").performClick()
        composeRule.onNodeWithText("プライバシーポリシー").performScrollTo().performClick()
        composeRule.onNodeWithText("利用規約").performScrollTo().performClick()
        composeRule.onNodeWithText("サポート").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(3, opened.size)
            assertTrue(opened[0].endsWith("/privacy"))
            assertTrue(analyticsEnabled)
        }
    }

    @Test
    fun currentIosSourceCalibratedSettingsFixtureUsesProductionHierarchy() {
        val activity = showSettings(
            notificationSettings = PatientNotificationSettings(masterEnabled = true),
            analyticsEnabled = true,
        )

        composeRule.onNodeWithText("設定").assertIsDisplayed()
        composeRule.onNodeWithText("お薬の通知").assertIsDisplayed()
        composeRule.onNodeWithText("通知を有効にする").assertIsDisplayed()
        composeRule.onNodeWithText("連携中").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-settings-list").performScrollToNode(hasText("利用状況データ"))
        composeRule.onNodeWithText("利用状況データ").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-settings-list").performScrollToNode(hasText("法務・サポート"))
        composeRule.onNodeWithText("法務・サポート").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-settings-list").performScrollToNode(hasText("ログアウト"))
        composeRule.onNodeWithText("ログアウト").assertIsDisplayed()
        captureDevice(activity, "android-ui-106-patient-settings-source-calibrated-logout-light.png")
        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(0)
        captureDevice(activity, "android-ui-106-patient-settings-source-calibrated-light.png")
    }

    @Test
    fun deniedNotificationPermissionDisablesToggleAndShowsGuidance() {
        val activity = showSettings(notificationPermissionDenied = true)

        composeRule.onNodeWithTag("patient-notification-toggle").assertIsNotEnabled()
        composeRule.onNodeWithTag("patient-settings-list").performScrollToNode(hasText("通知が許可されていません。設定アプリで通知を許可してください。"))
        composeRule.onNodeWithText("通知が許可されていません。設定アプリで通知を許可してください。").assertIsDisplayed()
        captureDevice(activity, "android-ui-106-patient-settings-notification-denied-light.png")
    }

    @Test
    fun logoutSubmissionDisablesActionAndShowsProgressCopy() {
        val activity = showSettings(loading = true)

        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(5)
        composeRule.onNodeWithTag("patient-unlink-button").assertIsNotEnabled()
        composeRule.onNodeWithText("ログアウト中…").assertIsDisplayed()
        captureDevice(activity, "android-ui-106-patient-settings-logout-loading-light.png")
    }

    @Test
    fun logoutFailureRemainsVisibleAndRetryable() {
        val activity = showSettings(error = "ログアウトできませんでした")

        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(6)
        composeRule.onNodeWithText("ログアウトできませんでした").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-unlink-button").assertIsEnabled()
        captureDevice(activity, "android-ui-106-patient-settings-logout-error-light.png")
    }

    @Test
    fun unlinkRequiresExplicitConfirmation() {
        var unlinkCount = 0
        showSettings(onUnlink = { unlinkCount += 1 })

        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(4)
        composeRule.onNodeWithTag("patient-unlink-button").performClick()
        composeRule.onNodeWithText("ログアウトしますか？").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-logout-confirm").performClick()
        composeRule.runOnIdle { assertEquals(1, unlinkCount) }
    }

    private fun showSettings(
        loading: Boolean = false,
        error: String? = null,
        notificationPermissionDenied: Boolean = false,
        notificationSettings: PatientNotificationSettings = PatientNotificationSettings(),
        analyticsEnabled: Boolean = false,
        onUnlink: () -> Unit = {},
    ): Activity {
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding()) {
                    SettingsContent(
                        loading = loading,
                        error = error,
                        notificationSettings = notificationSettings,
                        onNotificationSettingsChange = {},
                        notificationPermissionDenied = notificationPermissionDenied,
                        analyticsEnabled = analyticsEnabled,
                        onAnalyticsEnabledChange = {},
                        onOpenUrl = {},
                        onUnlink = onUnlink,
                    )
                }
            }
        }
        return activity
    }

    @Suppress("DEPRECATION")
    private fun captureDevice(activity: Activity, filename: String) {
        composeRule.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = true
        }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture(filename)
    }
}
