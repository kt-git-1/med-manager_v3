package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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
        val opened = mutableListOf<String>()
        composeRule.setContent {
            MedicationAppTheme {
                SettingsContent(
                    loading = false,
                    error = null,
                    notificationSettings = settings,
                    onNotificationSettingsChange = { settings = it },
                    onOpenUrl = opened::add,
                    onUnlink = {},
                )
            }
        }

        composeRule.onNodeWithText("通知を受け取る").assertIsDisplayed()
        composeRule.onNodeWithText("朝のお薬").assertIsDisplayed()
        composeRule.onNodeWithText("15分後にもう一度通知").assertIsDisplayed()
        composeRule.onNodeWithText("プライバシーポリシー").performScrollTo().performClick()
        composeRule.onNodeWithText("利用規約").performScrollTo().performClick()
        composeRule.onNodeWithText("サポート").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(3, opened.size)
            assertTrue(opened[0].endsWith("/privacy"))
        }
    }

    @Test
    fun unlinkRequiresExplicitConfirmation() {
        var unlinkCount = 0
        composeRule.setContent {
            MedicationAppTheme {
                SettingsContent(false, null, PatientNotificationSettings(), {}, {}, { unlinkCount += 1 })
            }
        }

        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(4)
        composeRule.onNodeWithTag("patient-unlink-button").performClick()
        composeRule.onNodeWithText("連携を解除しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("解除する").performClick()
        composeRule.runOnIdle { assertEquals(1, unlinkCount) }
    }
}
