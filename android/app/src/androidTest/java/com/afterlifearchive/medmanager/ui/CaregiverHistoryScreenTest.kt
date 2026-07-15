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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
        composeRule.onNodeWithText("薬A").assertIsDisplayed()
        composeRule.onNodeWithTag("history-backfill-med-1").performClick()
        composeRule.onNodeWithText("薬Aを服用済みとして記録します。").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-history-backfill-confirm").performClick()
        composeRule.waitForIdle()

        assertTrue(source.recorded)
        composeRule.onNodeWithText("服薬を記録しました").assertIsDisplayed()
    }

    @Test
    fun remotePushOpensExactDateAndHighlightsExactSlot() {
        val date = LocalDate.of(2026, 2, 11)
        val (repository, _) = repository(date, MedicationSlot.EVENING)
        repository.handleNotificationTarget("DOSE_TAKEN", "p1", date.toString(), "evening")

        setContent(repository)

        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("history-dose-highlighted-evening"))
        composeRule.onNodeWithTag("history-dose-highlighted-evening").assertIsDisplayed()
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
        val date = LocalDate.of(2026, 7, 15)
        val (repository, _) = repository(date)
        val patient = CaregiverPatient("p1", "さくら")
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverHistoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, highlightDurationMillis = null)
                }
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        captureDevice(activity, "android-ui-206-caregiver-history-light.png")
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

    override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), listOf(dose), emptyList())
    override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) { recordGate?.await(); recorded = true }
}
