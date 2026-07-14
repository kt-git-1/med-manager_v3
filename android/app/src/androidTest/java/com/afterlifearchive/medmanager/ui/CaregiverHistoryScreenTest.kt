package com.afterlifearchive.medmanager.ui

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
        composeRule.onNodeWithTag("caregiver-history-day-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("history-day-detail-list").performScrollToNode(hasTestTag("history-dose-morning"))
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
        composeRule.onNodeWithTag("caregiver-history-day-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("history-day-detail-list").performScrollToNode(hasTestTag("history-dose-highlighted-evening"))
        composeRule.onNodeWithTag("history-dose-highlighted-evening").assertIsDisplayed()
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
}

private class MutableHistorySource(private val date: LocalDate, private val slot: MedicationSlot) : CaregiverHistoryDataSource {
    var recorded = false
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
    override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) { recorded = true }
}
