package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
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
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientHistoryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun simpleHistoryShowsTodayWeekAndRecentRecords() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    days = listOf(
                        HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                        HistoryDay("2026-07-14", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, 1),
                    ),
                    loading = false, error = null,
                    retentionCutoffDate = null, retentionDays = null,
                    onRetry = {}, now = LocalDate.parse("2026-07-14"),
                )
            }
        }

        composeRule.onNodeWithText("飲んだ記録を確認できます").assertIsDisplayed()
        composeRule.onNodeWithText("1/2回分 記録済み").assertIsDisplayed()
        composeRule.onNodeWithText("残り 1回分").assertIsDisplayed()
        composeRule.onNodeWithText("今週").assertIsDisplayed()
        composeRule.onNodeWithText("1/7日").assertIsDisplayed()
        composeRule.onNodeWithText("最近の記録").assertIsDisplayed()
        listOf("今日 7月14日（火）", "昨日 7月13日（月）", "朝・昼のお薬", "朝のお薬").forEach { text ->
            composeRule.onNodeWithTag("patient-history-list").performScrollToNode(hasText(text))
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun noScheduleUsesCurrentIosEmptyProgressCopy() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    days = emptyList(), loading = false, error = null,
                    retentionCutoffDate = null, retentionDays = null, onRetry = {},
                    now = LocalDate.parse("2026-07-14"),
                )
            }
        }

        composeRule.onNodeWithText("今日は予定がありません").assertIsDisplayed()
        composeRule.onNodeWithText("今日は定時薬の予定がありません。必要な時のお薬だけ記録できます。").assertIsDisplayed()
        composeRule.onNodeWithText("0/7日").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-104-patient-history-no-schedule-light.png",
        )
    }

    @Test
    fun missedTodayUsesPriorityMessage() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    days = listOf(
                        HistoryDay("2026-07-12", HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                        HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                        HistoryDay("2026-07-14", HistoryStatus.TAKEN, HistoryStatus.MISSED, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                    ),
                    loading = false, error = null, retentionCutoffDate = null, retentionDays = null,
                    onRetry = {}, now = LocalDate.parse("2026-07-14"),
                )
            }
        }

        composeRule.onNodeWithText("飲み忘れ 1回分").assertIsDisplayed()
        composeRule.onNodeWithText("飲み忘れがあります。気づいた時点で確認しましょう。").assertIsDisplayed()
        composeRule.onNodeWithText("1/7日").assertIsDisplayed()
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
                HistoryContent(emptyList(), false, null, "2026-06-14", 30, {})
            }
        }
        composeRule.onNodeWithText("この履歴は表示できません").assertIsDisplayed()
        composeRule.onNodeWithText("直近30日間", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-14", substring = true).assertIsDisplayed()
    }
}
