package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.core.view.WindowCompat
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
    fun currentIosSourceCalibratedHistoryFixtureUsesCurrentMetricsAndCopy() {
        val activity = showHistory(
            days = listOf(
                HistoryDay("2026-06-08", HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                HistoryDay("2026-06-09", HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                HistoryDay("2026-06-10", HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                HistoryDay("2026-06-11", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.PENDING, HistoryStatus.NONE, 0),
            ),
            now = LocalDate.parse("2026-06-11"),
        )

        composeRule.onNodeWithText("1/3回分 記録済み").assertIsDisplayed()
        composeRule.onNodeWithText("記録済み 1回分").assertIsDisplayed()
        composeRule.onNodeWithText("服用済み 1回分").assertDoesNotExist()
        composeRule.onNodeWithText("3/7日").assertIsDisplayed()
        captureDevice(activity, "android-ui-104-patient-history-source-calibrated-light.png")
    }

    @Test
    fun noScheduleUsesCurrentIosEmptyProgressCopy() {
        val activity = showHistory()

        composeRule.onNodeWithText("今日は予定がありません").assertIsDisplayed()
        composeRule.onNodeWithText("今日は定時薬の予定がありません。必要な時のお薬だけ記録できます。").assertIsDisplayed()
        composeRule.onNodeWithText("0/7日").assertIsDisplayed()
        captureDevice(activity, "android-ui-104-patient-history-no-schedule-light.png")
    }

    @Test
    fun initialLoadingUsesCurrentIosMessageState() {
        val activity = showHistory(loading = true)

        composeRule.onNodeWithTag("patient-history-loading").assertIsDisplayed()
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        captureDevice(activity, "android-ui-104-patient-history-loading-light.png")
    }

    @Test
    fun failureUsesCurrentIosErrorAndRetryContract() {
        var retryCount = 0
        val activity = showHistory(error = "取得に失敗しました", onRetry = { retryCount += 1 })

        composeRule.onNodeWithTag("patient-history-error").assertIsDisplayed()
        composeRule.onNodeWithText("取得に失敗しました").assertIsDisplayed()
        composeRule.onNodeWithText("再試行").assertIsDisplayed().performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(1, retryCount) }
        captureDevice(activity, "android-ui-104-patient-history-error-light.png")
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
        showDayDetail(detail = historyDetailFixture())

        composeRule.onNodeWithText("血圧薬 1錠").assertIsDisplayed()
        composeRule.onNodeWithText("家族が代理で記録").assertIsDisplayed()
        composeRule.onNodeWithText("記録済み").assertIsDisplayed()
        composeRule.onNodeWithText("頓服: 頭痛薬").assertIsDisplayed()
        composeRule.onNodeWithText("本人が記録").assertIsDisplayed()
        captureNode("android-ui-105-patient-history-day-content-light.png")
    }

    @Test
    fun dayDetailShowsEmptyContract() {
        showDayDetail(detail = HistoryDayDetail("2026-07-13", emptyList(), emptyList()))

        composeRule.onNodeWithText("予定がありません").assertIsDisplayed()
        composeRule.onNodeWithText("この日の服用予定はありません").assertIsDisplayed()
        captureNode("android-ui-105-patient-history-day-empty-light.png")
    }

    @Test
    fun dayDetailShowsLoadingContract() {
        showDayDetail(detail = null, loading = true)

        composeRule.onNodeWithTag("history-day-detail-loading").assertIsDisplayed()
        captureNode("android-ui-105-patient-history-day-loading-light.png")
    }

    @Test
    fun dayDetailShowsRetryableErrorContract() {
        var retryCount = 0
        showDayDetail(detail = null, error = "内部詳細を表示しない", onRetry = { retryCount += 1 })

        composeRule.onNodeWithText("読み込みに失敗しました。再試行してください。").assertIsDisplayed()
        composeRule.onNodeWithTag("history-day-detail-list").performScrollToNode(hasText("再試行"))
        composeRule.onNodeWithText("再試行").assertIsDisplayed().performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(1, retryCount) }
        captureNode("android-ui-105-patient-history-day-error-light.png")
    }

    @Test
    fun dayDetailShowsRetentionContract() {
        showDayDetail(
            detail = null,
            retentionCutoffDate = "2026-06-14",
            retentionDays = 30,
        )

        composeRule.onNodeWithText("この履歴は表示できません").assertIsDisplayed()
        composeRule.onNodeWithText("直近30日間", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-14", substring = true).assertIsDisplayed()
        captureNode("android-ui-105-patient-history-day-retention-light.png")
    }

    @Test
    fun retentionLockShowsCutoffAndDays() {
        val activity = showHistory(retentionCutoffDate = "2026-06-14", retentionDays = 30)
        composeRule.onNodeWithText("この履歴は表示できません").assertIsDisplayed()
        composeRule.onNodeWithText("直近30日間", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2026-06-14", substring = true).assertIsDisplayed()
        captureDevice(activity, "android-ui-104-patient-history-retention-light.png")
    }

    private fun showHistory(
        days: List<HistoryDay> = emptyList(),
        loading: Boolean = false,
        error: String? = null,
        retentionCutoffDate: String? = null,
        retentionDays: Int? = null,
        onRetry: () -> Unit = {},
        now: LocalDate = LocalDate.parse("2026-07-14"),
    ): Activity {
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding()) {
                    HistoryContent(
                        days = days,
                        loading = loading,
                        error = error,
                        retentionCutoffDate = retentionCutoffDate,
                        retentionDays = retentionDays,
                        onRetry = onRetry,
                        now = now,
                    )
                }
            }
        }
        return activity
    }

    private fun showDayDetail(
        detail: HistoryDayDetail?,
        loading: Boolean = false,
        error: String? = null,
        retentionCutoffDate: String? = null,
        retentionDays: Int? = null,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            MedicationAppTheme {
                Box(Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding()) {
                    HistoryDayDetailContent(
                        date = LocalDate.parse("2026-07-13"),
                        detail = detail,
                        loading = loading,
                        error = error,
                        retentionCutoffDate = retentionCutoffDate,
                        retentionDays = retentionDays,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }

    private fun historyDetailFixture() = HistoryDayDetail(
        "2026-07-13",
        listOf(HistoryScheduledDose("med", "血圧薬", "1錠", 1.0, Instant.parse("2026-07-13T03:15:00Z"), MedicationSlot.NOON, DoseStatus.TAKEN, RecordedByType.CAREGIVER)),
        listOf(PrnHistoryItem("prn", "頭痛薬", Instant.parse("2026-07-13T05:00:00Z"), 1.5, PrnActorType.PATIENT)),
    )

    private fun captureNode(filename: String) {
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), filename)
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
