package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PrnActorType
import com.afterlifearchive.medmanager.data.patient.PrnHistoryItem
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.onAllNodesWithTag

class CaregiverHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun monthSelectionShowsDayDetailAndConfirmationProtectedBackfill() {
        val date = LocalDate.of(2026, 7, 15)
        val (repository, source) = repository(date)
        setContent(repository)

        composeRule.onNodeWithText("服薬履歴").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("history-dose-morning"))
        composeRule.onNodeWithText("薬A 1錠").assertIsDisplayed()
        composeRule.onNodeWithTag("history-backfill-med-1").performClick()
        composeRule.onNodeWithText("薬Aを服用済みとして記録します。").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-backfill-confirm").performClick()
        composeRule.waitForIdle()

        assertTrue(source.recorded)
        composeRule.onNodeWithText("服薬を記録しました").assertIsDisplayed()
    }

    @Test
    fun missedDoseRemotePushOpensExactDateAndHighlightsExactSlot() {
        val date = LocalDate.of(2026, 2, 11)
        val (repository, _) = repository(date, MedicationSlot.EVENING)
        repository.handleNotificationTarget("DOSE_MISSED", "p1", date.toString(), "evening")

        setContent(repository)

        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("history-dose-highlighted-evening"))
        composeRule.onNodeWithTag("history-dose-highlighted-evening").assertIsDisplayed()
    }

    @Test
    fun selectedDayUsesCurrentIosTimestampSortedTimeline() {
        val date = LocalDate.of(2026, 7, 15)
        val (repository, _) = repository(date)
        setContent(repository)

        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        val timeline = patientHistoryTimelineItems(checkNotNull(repository.state.value.dayDetail))
        assertTrue(timeline.first() is PatientHistoryTimelineItem.Prn)
        assertTrue(timeline.last() is PatientHistoryTimelineItem.Scheduled)

        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(androidx.compose.ui.test.hasText("頓服: 頭痛薬"))
        composeRule.onNodeWithText("頓服: 頭痛薬").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("history-dose-morning"))
        composeRule.onNodeWithText("薬A 1錠").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-206-caregiver-history-day-timeline-light.png",
        )
    }

    @Test
    fun selectedDayShowsMessageBearingLoadingState() {
        val date = LocalDate.of(2026, 7, 15)
        val dayGate = CompletableDeferred<Unit>()
        val repository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(date.toString(), HistoryStatus.NONE, HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            )
            override suspend fun day(patientId: String, date: LocalDate): HistoryDayDetail {
                dayGate.await()
                return HistoryDayDetail(date.toString(), emptyList(), emptyList())
            }
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        setContent(repository)
        composeRule.waitUntil(5_000) { repository.state.value.monthLoaded }

        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.loadingDay }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("caregiver-history-day-loading"))
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), "android-ui-206-caregiver-history-day-loading-light.png")
        dayGate.complete(Unit)
    }

    @Test
    fun selectedDayShowsCurrentIosEmptyState() {
        val date = LocalDate.of(2026, 7, 15)
        val repository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(date.toString(), HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            )
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        setContent(repository)
        composeRule.waitUntil(5_000) { repository.state.value.monthLoaded }

        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(androidx.compose.ui.test.hasText("予定がありません"))
        composeRule.onNodeWithText("予定がありません").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(androidx.compose.ui.test.hasText("この日の服用予定はありません"))
        composeRule.onNodeWithText("この日の服用予定はありません").assertIsDisplayed()
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), "android-ui-206-caregiver-history-day-empty-light.png")
    }

    @Test
    fun selectedDayFailureRetriesTheExactDayRequest() {
        val date = LocalDate.of(2026, 7, 15)
        var dayCalls = 0
        val repository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(date.toString(), HistoryStatus.NONE, HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            )
            override suspend fun day(patientId: String, date: LocalDate): HistoryDayDetail {
                dayCalls += 1
                error("offline")
            }
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        setContent(repository)
        composeRule.waitUntil(5_000) { repository.state.value.monthLoaded }

        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.dayFailed }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(androidx.compose.ui.test.hasText("再試行"))
        composeRule.onNodeWithText("通信状況を確認して", substring = true).assertIsDisplayed()
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), "android-ui-206-caregiver-history-day-error-light.png")
        composeRule.onNodeWithText("再試行").performClick()
        composeRule.waitUntil(5_000) { dayCalls >= 2 }
    }

    @Test
    fun failedMonthRefreshKeepsCalendarAndShowsStaleRetryCard() {
        val date = LocalDate.of(2026, 7, 15)
        var fail = false
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) =
                if (fail) error("offline") else listOf(HistoryDay(date.toString(), HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0))
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }
        val repository = CaregiverHistoryRepository(source, MutationFreshnessStore())
        runBlocking { repository.loadMonth("p1", YearMonth.of(2026, 7)) }
        fail = true
        runBlocking { repository.loadMonth("p1", YearMonth.of(2026, 7)) }

        setContent(repository)

        composeRule.onNodeWithTag("caregiver-history-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-day-$date").assertIsDisplayed()
    }

    @Test
    fun initialLoadShowsIosLoadingMessage() {
        val gate = CompletableDeferred<Unit>()
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth): List<HistoryDay> { gate.await(); return emptyList() }
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }
        val repository = CaregiverHistoryRepository(source, MutationFreshnessStore())
        setContent(repository)

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-history-loading").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        gate.complete(Unit)
    }

    @Test
    fun screenshotFixtureShowsInlineCaregiverHistory() {
        val date = LocalDate.of(2026, 6, 10)
        val repository = CaregiverHistoryRepository(MarketingHistorySource(date), MutationFreshnessStore())
        runBlocking {
            repository.loadMonth("p1", YearMonth.of(2026, 6))
            repository.selectDate(date)
            repository.loadDay("p1", date)
        }
        val patient = CaregiverPatient("p1", "田中 花子")
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverHistoryScreen(
                        repository,
                        CaregiverPatientState(listOf(patient), patient.id),
                        true,
                        highlightDurationMillis = null,
                        billingEnabled = false,
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithText("2026年6月").assertIsDisplayed()
        composeRule.onAllNodesWithTag("caregiver-history-previous-month").assertCountEquals(0)
        composeRule.onAllNodesWithTag("caregiver-history-next-month").assertCountEquals(0)
        composeRule.onNodeWithTag("caregiver-history-weekday-0").assertTextEquals("月")
        composeRule.onNodeWithTag("caregiver-history-weekday-6").assertTextEquals("日")
        composeRule.onNodeWithText("日付の下の点は、朝・昼・夜・眠前の服薬状況です。").assertIsDisplayed()
        composeRule.onNodeWithText("6月10日（水）").assertIsDisplayed()
        composeRule.onNodeWithText("1/3回分 記録済み").assertIsDisplayed()
        captureDevice(activity, "android-ui-206-caregiver-history-source-calibrated-light.png")
    }

    @Test
    fun premiumHistoryRetainsMonthNavigation() {
        val date = LocalDate.of(2026, 7, 15)
        val (repository, _) = repository(date)
        val patient = CaregiverPatient("p1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHistoryScreen(
                    repository,
                    CaregiverPatientState(listOf(patient), patient.id),
                    enabled = true,
                    billingEnabled = true,
                )
            }
        }
        composeRule.onNodeWithTag("caregiver-history-previous-month").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-next-month").assertIsDisplayed()
    }

    @Test
    fun backfillUsesBlockingUpdatingOverlay() {
        val date = LocalDate.of(2026, 7, 15)
        val (repository, source) = repository(date)
        source.recordGate = CompletableDeferred()
        setContent(repository)
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("history-backfill-med-1"))
        composeRule.onNodeWithTag("history-backfill-med-1").performClick()
        composeRule.onNodeWithTag("caregiver-history-backfill-confirm").performClick()

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-history-updating").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("更新中...").assertIsDisplayed()
        source.recordGate?.complete(Unit)
    }

    private fun setContent(repository: CaregiverHistoryRepository) {
        val patient = CaregiverPatient("p1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHistoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, highlightDurationMillis = null)
            }
        }
    }

    private fun repository(date: LocalDate, slot: MedicationSlot = MedicationSlot.MORNING): Pair<CaregiverHistoryRepository, MutableHistorySource> {
        val source = MutableHistorySource(date, slot)
        return CaregiverHistoryRepository(source, MutationFreshnessStore()) to source
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

private class MutableHistorySource(private val date: LocalDate, private val slot: MedicationSlot) : CaregiverHistoryDataSource {
    var recorded = false
    var recordGate: CompletableDeferred<Unit>? = null
    private val dose get() = HistoryScheduledDose(
        "med-1", "薬A", "1錠", 1.0, Instant.parse("${date}T08:00:00Z"), slot,
        if (recorded) DoseStatus.TAKEN else DoseStatus.MISSED, null,
    )

    override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
        HistoryDay(
            date.toString(),
            if (slot == MedicationSlot.MORNING) HistoryStatus.MISSED else HistoryStatus.NONE,
            HistoryStatus.NONE,
            if (slot == MedicationSlot.EVENING) HistoryStatus.MISSED else HistoryStatus.NONE,
            HistoryStatus.NONE,
            0,
        ),
    )

    override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(
        date.toString(),
        listOf(dose),
        listOf(PrnHistoryItem("prn-1", "頭痛薬", Instant.parse("${date}T03:00:00Z"), 1.0, PrnActorType.PATIENT)),
    )
    override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) { recordGate?.await(); recorded = true }
}

private class MarketingHistorySource(private val selectedDate: LocalDate) : CaregiverHistoryDataSource {
    override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
        HistoryDay("2026-06-05", HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, 0),
        HistoryDay("2026-06-06", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, 0),
        HistoryDay("2026-06-08", HistoryStatus.TAKEN, HistoryStatus.MISSED, HistoryStatus.NONE, HistoryStatus.NONE, 0),
        HistoryDay("2026-06-09", HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, 1),
        HistoryDay("2026-06-10", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.MISSED, HistoryStatus.NONE, 0),
        HistoryDay("2026-06-11", HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
    )

    override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(
        date.toString(),
        listOf(
            HistoryScheduledDose("med-1", "血圧の薬", "1錠", 1.0, Instant.parse("${selectedDate}T08:00:00Z"), MedicationSlot.MORNING, DoseStatus.TAKEN, null),
            HistoryScheduledDose("med-2", "整腸剤", "1錠", 1.0, Instant.parse("${selectedDate}T12:00:00Z"), MedicationSlot.NOON, DoseStatus.PENDING, null),
            HistoryScheduledDose("med-3", "夜のお薬", "1錠", 1.0, Instant.parse("${selectedDate}T18:00:00Z"), MedicationSlot.EVENING, DoseStatus.MISSED, null),
        ),
        emptyList(),
    )

    override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
}
