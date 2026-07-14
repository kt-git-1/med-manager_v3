package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class CaregiverAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyDayMergesDateDoseStatusesAndPrnForTalkBack() {
        val date = LocalDate.of(2026, 7, 15)
        val repository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(
                    date.toString(),
                    HistoryStatus.TAKEN,
                    HistoryStatus.MISSED,
                    HistoryStatus.PENDING,
                    HistoryStatus.NONE,
                    2,
                ),
            )

            override suspend fun day(patientId: String, date: LocalDate) =
                HistoryDayDetail(date.toString(), emptyList(), emptyList())

            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")

        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHistoryScreen(
                    repository,
                    CaregiverPatientState(listOf(patient), patient.id),
                    enabled = true,
                )
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.monthLoaded }
        val label = "7月15日 水曜日、朝のお薬 服用済み、昼のお薬 未達、夕方のお薬 未服用、頓服 2回"
        composeRule.onNodeWithTag("caregiver-history-month")
            .performScrollToNode(hasContentDescription(label))
        composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }
}
