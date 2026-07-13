package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
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
        composeRule.onNodeWithText("飲むお薬を確認します。飲んだら「飲みました」を押します。").assertIsDisplayed()
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
        composeRule.onNodeWithText("通知を設定").performClick()
        composeRule.onNodeWithText("戻る").performClick()
        composeRule.onNodeWithText("スキップ").performClick()
        composeRule.runOnIdle {
            assertEquals(1, next)
            assertEquals(1, previous)
            assertEquals(1, skip)
        }
    }
}
