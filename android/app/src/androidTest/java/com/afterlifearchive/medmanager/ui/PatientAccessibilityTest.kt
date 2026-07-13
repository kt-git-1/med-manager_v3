package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class PatientAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarExposesCompleteTalkBackDescription() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    listOf(HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.MISSED, HistoryStatus.PENDING, HistoryStatus.NONE, 2)),
                    YearMonth.of(2026, 7), false, null, null, null, {}, {}, {}, {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "7月13日 月曜日、朝服用済み、昼未達、夕未服用、寝る前予定なし、頓服2回",
        ).assertIsDisplayed()
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
