package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PrnActorType
import com.afterlifearchive.medmanager.data.patient.PrnHistoryItem
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class PatientHistoryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun monthCalendarShowsLegendStatusesPrnAndNavigation() {
        var selected: LocalDate? = null
        var previous: YearMonth? = null
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    days = listOf(HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.MISSED, HistoryStatus.PENDING, HistoryStatus.NONE, 2)),
                    yearMonth = YearMonth.of(2026, 7), loading = false, error = null,
                    retentionCutoffDate = null, retentionDays = null,
                    onPreviousMonth = { previous = it.minusMonths(1) }, onNextMonth = {},
                    onSelectDate = { selected = it }, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("2026年7月").assertIsDisplayed()
        composeRule.onNodeWithText("服用済み").assertIsDisplayed()
        composeRule.onNodeWithText("未達").assertIsDisplayed()
        composeRule.onNodeWithText("未服用").assertIsDisplayed()
        composeRule.onNodeWithText("予定なし").assertIsDisplayed()
        composeRule.onNodeWithText("頓2").assertIsDisplayed()
        composeRule.onNodeWithText("13").performClick()
        composeRule.onNodeWithText("‹ 前月").performClick()

        composeRule.runOnIdle {
            assertEquals(LocalDate.parse("2026-07-13"), selected)
            assertEquals(YearMonth.of(2026, 6), previous)
        }
    }

    @Test
    fun dayDetailShowsScheduledAndPrnRecorderInformation() {
        val detail = HistoryDayDetail(
            "2026-07-13",
            listOf(HistoryScheduledDose("med", "血圧薬", "1錠", 1.0, Instant.parse("2026-07-13T03:15:00Z"), MedicationSlot.NOON, DoseStatus.TAKEN, RecordedByType.CAREGIVER)),
            listOf(PrnHistoryItem("prn", "頭痛薬", Instant.parse("2026-07-13T05:00:00Z"), 1.5, PrnActorType.PATIENT)),
        )
        composeRule.setContent {
            MedicationAppTheme {
                HistoryDayDetailContent(LocalDate.parse("2026-07-13"), detail, false, null, null, null, {})
            }
        }

        composeRule.onNodeWithText("血圧薬").assertIsDisplayed()
        composeRule.onNodeWithText("家族が記録").assertIsDisplayed()
        composeRule.onNodeWithText("服用済み").assertIsDisplayed()
        composeRule.onNodeWithText("頭痛薬").assertIsDisplayed()
        composeRule.onNodeWithText("1.5錠", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("本人").assertIsDisplayed()
    }

    @Test
    fun retentionLockShowsCutoffAndDays() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(emptyList(), YearMonth.of(2026, 5), false, null, "2026-06-14", 30, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("この履歴は表示できません").assertIsDisplayed()
        composeRule.onNodeWithText("直近30日間", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-14", substring = true).assertIsDisplayed()
    }
}
