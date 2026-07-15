package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventorySummary
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.SlotBulkRecordResult
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.Instant
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
        val label = "7月15日 水曜日、朝のお薬 記録済み、昼のお薬 飲み忘れ、夕方のお薬 未記録、頓服 2回"
        composeRule.onNodeWithTag("caregiver-history-month")
            .performScrollToNode(hasContentDescription(label))
        composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    @Test
    fun primaryCaregiverActionsNameTheMedicationForTalkBack() {
        val patient = CaregiverPatient("p1", "さくら")
        val patientState = CaregiverPatientState(listOf(patient), patient.id)
        val medication = PatientMedication(
            "med-1", patient.id, "血圧の薬", "5mg", 1.0, 5.0, "mg", null, false, null,
            Instant.parse("2025-01-01T00:00:00Z"), null, 12.0, "錠", true, 12.0, false,
            true, false, null, listOf("08:00"), emptyList(),
        )
        val dose = PatientDose(
            "dose-1", medication.id, Instant.parse("2026-07-14T23:00:00Z"), DoseStatus.PENDING,
            medication.name, medication.dosageText, 1.0, patientId = patient.id,
        )
        val inventoryItem = CaregiverInventoryItem(
            medication.id, medication.name, false, 1.0, true, 12.0, 3, false, false, false,
            1.0, 7.0, 14.0, 21.0, 12, "2026-07-27",
        )
        var recorded = false
        val todayRepository = CaregiverTodayRepository(object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(dose.copy(status = if (recorded) DoseStatus.TAKEN else DoseStatus.PENDING))
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot): SlotBulkRecordResult {
                recorded = true
                return SlotBulkRecordResult(1, 0, 0, 1.0, 1, "08:00", MedicationSlot.entries.associateWith { HistoryStatus.NONE }, "group-1")
            }
        }, MutationFreshnessStore())
        val medicationRepository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { listOf(medication) },
            MutationFreshnessStore(),
        )
        val inventoryRepository = CaregiverInventoryRepository(object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = listOf(inventoryItem)
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = inventoryItem
            override suspend fun adjust(
                patientId: String,
                medicationId: String,
                reason: String,
                delta: Double?,
                absoluteQuantity: Double?,
            ) = inventoryItem
        }, MutationFreshnessStore())
        val surface = mutableStateOf(0)

        composeRule.setContent {
            MedicationAppTheme {
                when (surface.value) {
                    0 -> CaregiverTodayScreen(todayRepository, patientState, enabled = true, onOpenMedications = {})
                    1 -> CaregiverMedicationScreen(medicationRepository, patientState, enabled = true)
                    else -> CaregiverInventoryScreen(inventoryRepository, patientState, enabled = true, onOpenMedications = {})
                }
            }
        }

        composeRule.waitUntil(5_000) { todayRepository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-today-list")
            .performScrollToNode(hasText("1件をまとめて記録"))
        composeRule.onNodeWithText("1件をまとめて記録").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("caregiver-today-slot-confirm").performClick()
        composeRule.waitUntil(5_000) { todayRepository.state.value.doses.single().status == DoseStatus.TAKEN }
        composeRule.onNodeWithTag("caregiver-today-list")
            .performScrollToNode(hasContentDescription("血圧の薬の服用記録を取り消す"))
        composeRule.onNodeWithContentDescription("血圧の薬の服用記録を取り消す").assertIsDisplayed()

        composeRule.runOnUiThread { surface.value = 1 }
        composeRule.waitUntil(5_000) { medicationRepository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-medication-list")
            .performScrollToNode(hasContentDescription("血圧の薬を編集"))
        composeRule.onNodeWithContentDescription("血圧の薬を編集").assertIsDisplayed()

        composeRule.runOnUiThread { surface.value = 2 }
        composeRule.waitUntil(5_000) { inventoryRepository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-inventory-list")
            .performScrollToNode(hasContentDescription("血圧の薬の在庫詳細を開く"))
        composeRule.onNodeWithContentDescription("血圧の薬の在庫詳細を開く").assertIsDisplayed()
    }
}
