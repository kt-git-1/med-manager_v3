package com.afterlifearchive.medmanager.ui

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
    fun deniedNotificationPermissionDisablesToggleAndShowsGuidance() {
        composeRule.setContent {
            MedicationAppTheme {
                SettingsContent(false, null, PatientNotificationSettings(), {}, true, false, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("patient-notification-toggle").assertIsNotEnabled()
        composeRule.onNodeWithTag("patient-settings-list").performScrollToNode(hasText("通知が許可されていません。設定アプリで通知を許可してください。"))
        composeRule.onNodeWithText("通知が許可されていません。設定アプリで通知を許可してください。").assertIsDisplayed()
    }

    @Test
    fun unlinkRequiresExplicitConfirmation() {
        var unlinkCount = 0
        composeRule.setContent {
            MedicationAppTheme {
                SettingsContent(false, null, PatientNotificationSettings(), {}, false, false, {}, {}, { unlinkCount += 1 })
            }
        }

        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(4)
        composeRule.onNodeWithTag("patient-unlink-button").performClick()
        composeRule.onNodeWithText("ログアウトしますか？").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-logout-confirm").performClick()
        composeRule.runOnIdle { assertEquals(1, unlinkCount) }
    }
}
