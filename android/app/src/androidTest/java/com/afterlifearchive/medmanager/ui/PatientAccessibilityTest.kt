package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PatientAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun simpleHistoryExposesPatientFacingSummary() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    listOf(HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.MISSED, HistoryStatus.PENDING, HistoryStatus.NONE, 2)),
                    false, null, null, null, {}, LocalDate.parse("2026-07-14"),
                )
            }
        }

        composeRule.onNodeWithText("飲んだ記録を確認できます").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-history-list").performScrollToNode(hasText("昨日 7月13日（月）"))
        composeRule.onNodeWithText("昨日 7月13日（月）").assertIsDisplayed()
        composeRule.onNodeWithText("朝・昼・夕のお薬").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-history-list").performScrollToNode(hasContentDescription("月 7月13日 忘れ"))
        composeRule.onNodeWithContentDescription("月 7月13日 忘れ").assertIsDisplayed()
    }

    @Test
    fun tutorialRemainsOperableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            MedicationAppTheme {
                val current = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(current.density, 2f)) {
                    PatientTutorialOverlay(3, {}, {}, {})
                }
            }
        }

        composeRule.onNodeWithText("お薬の時間に通知しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("スキップ").assertIsDisplayed()
        composeRule.onNodeWithText("戻る").assertIsDisplayed()
        composeRule.onNodeWithText("通知を設定").assertIsDisplayed()
    }
}
