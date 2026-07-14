package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventorySummary
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.SlotBulkRecordResult
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class CaregiverTodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentShowsNextActionProgressPrnAndTimelineAggregation() {
        setContent(
            doses = listOf(
                dose("morning", DoseStatus.TAKEN, "2026-07-14T23:00:00Z"),
                dose("noon", DoseStatus.PENDING, "2026-07-15T04:00:00Z"),
                dose("evening", DoseStatus.MISSED, "2026-07-15T10:00:00Z"),
            ),
            prn = listOf(medication()),
            inventory = listOf(CaregiverInventorySummary("noon", true, 0.0, 1.0, true, true)),
        )

        composeRule.onNodeWithText("さくらさんを見守り中").assertIsDisplayed()
        composeRule.onNodeWithText("次にすること").assertIsDisplayed()
        composeRule.onAllNodesWithText("昼 13:00").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3 回完了").assertIsDisplayed()
        composeRule.onNodeWithText("頓服薬が1件あります").assertIsDisplayed()
        composeRule.onNodeWithText("未記録の時間帯があります").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-slot-action-noon"))
        composeRule.onNodeWithTag("caregiver-today-slot-action-noon").assertIsDisplayed()
    }

    @Test
    fun canonicalEmptyStateRoutesToMedicationTabAction() {
        var opened = false
        setContent(onOpenMedications = { opened = true })

        composeRule.onNodeWithText("今日の服薬予定はありません").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-open-medications").performClick()

        assertTrue(opened)
    }

    @Test
    fun noPatientUsesSharedCanonicalGateWithoutLoadingTodayData() {
        val repository = repository()
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(), true, {})
            }
        }

        composeRule.onNodeWithText("患者がいません").assertIsDisplayed()
        composeRule.onNodeWithText("見守る方の名前を登録すると", substring = true).assertIsDisplayed()
    }

    @Test
    fun failedLoadExposesRetryableCanonicalErrorState() {
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> = error("offline")
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("情報を取得できませんでした").assertIsDisplayed()
        composeRule.onNodeWithText("もう一度試す").assertIsDisplayed()
    }

    @Test
    fun failedRefreshShowsStaleSnapshotAndDisablesDoseMutation() {
        val original = dose("stale", DoseStatus.PENDING, "2026-07-15T04:00:00Z")
        var fail = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = if (fail) error("offline") else listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        runBlocking { repository.load("patient-1") }
        fail = true
        runBlocking { repository.load("patient-1") }
        val patient = CaregiverPatient("patient-1", "さくら")

        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }

        composeRule.onNodeWithTag("caregiver-today-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-dose-action-stale"))
        composeRule.onNodeWithTag("caregiver-today-dose-action-stale").assertIsNotEnabled()
    }

    @Test
    fun individualDoseActionRecordsThenDeletesThroughProductionScreen() {
        var current = listOf(dose("dose-1", DoseStatus.PENDING, "2026-07-14T23:00:00Z"))
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = current
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordDose(patientId: String, dose: PatientDose) {
                current = current.map { if (it.key == dose.key) it.copy(status = DoseStatus.TAKEN) else it }
            }
            override suspend fun deleteDose(patientId: String, dose: PatientDose) {
                current = current.map { if (it.key == dose.key) it.copy(status = DoseStatus.PENDING) else it }
            }
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "13:00", "19:00", "22:00"))
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-dose-action-dose-1"))
        composeRule.onNodeWithTag("caregiver-today-dose-action-dose-1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-mutation-message"))
        composeRule.onNodeWithText("服用を記録しました").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-dose-action-dose-1"))
        composeRule.onNodeWithTag("caregiver-today-dose-action-dose-1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-mutation-message"))
        composeRule.onNodeWithText("服用記録を取り消しました").assertIsDisplayed()
    }

    @Test
    fun olderMissedSlotRequiresConfirmationThenRecordsThroughCaregiverRoute() {
        var current = listOf(dose("old", DoseStatus.MISSED, "2026-07-13T23:00:00Z"))
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = current
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot): SlotBulkRecordResult {
                current = current.map { it.copy(status = DoseStatus.TAKEN) }
                return SlotBulkRecordResult(1, 0, 0, 1.0, 1, "08:00", MedicationSlot.entries.associateWith { HistoryStatus.NONE }, "group-1")
            }
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "13:00", "19:00", "22:00"))
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-slot-action-morning"))
        composeRule.onNodeWithTag("caregiver-today-slot-action-morning").performClick()
        composeRule.onNodeWithTag("caregiver-today-slot-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("朝の過去の未記録を含むお薬1件を", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-slot-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-mutation-message"))
        composeRule.onNodeWithText("1件を記録しました").assertIsDisplayed()
    }

    @Test
    fun prnPickerRequiresMedicationConfirmationAndShowsSuccess() {
        val prn = medication()
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = emptyList<PatientDose>()
            override suspend fun medications(patientId: String) = listOf(prn)
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordPrn(patientId: String, medication: PatientMedication) = Unit
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-prn-open").performClick()
        composeRule.onNodeWithTag("caregiver-today-prn-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-prn-prn").performClick()
        composeRule.onNodeWithTag("caregiver-today-prn-confirm-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("痛み止めを介護者が服用済みとして記録します。").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-prn-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("頓服を記録しました").assertIsDisplayed()
    }

    @Test
    fun successfulRecordRemainsRenderedWhenAutomaticRefreshFails() {
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-14T23:00:00Z")
        var refreshFails = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> = if (refreshFails) error("offline") else listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordDose(patientId: String, dose: PatientDose) { refreshFails = true }
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-dose-action-dose-1"))
        composeRule.onNodeWithTag("caregiver-today-dose-action-dose-1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-mutation-message"))
        composeRule.onNodeWithText("服用を記録しました").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-mutation-error").assertDoesNotExist()
        composeRule.onNodeWithText("情報を取得できませんでした").assertDoesNotExist()
    }

    private fun setContent(
        doses: List<PatientDose> = emptyList(),
        prn: List<PatientMedication> = emptyList(),
        inventory: List<CaregiverInventorySummary> = emptyList(),
        onOpenMedications: () -> Unit = {},
    ) {
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = doses
            override suspend fun medications(patientId: String) = prn
            override suspend fun inventory(patientId: String) = inventory
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "13:00", "19:00", "22:00"))
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, onOpenMedications)
            }
        }
        composeRule.waitForIdle()
    }

    private fun repository() = CaregiverTodayRepository(object : CaregiverTodayDataSource {
        override suspend fun today(patientId: String) = emptyList<PatientDose>()
        override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
        override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
    }, MutationFreshnessStore())

    private fun dose(id: String, status: DoseStatus, time: String) = PatientDose(
        key = id,
        medicationId = id,
        scheduledAt = Instant.parse(time),
        status = status,
        medicationName = "薬$id",
        dosageText = "5 mg",
        doseCount = 1.0,
        patientId = "patient-1",
    )

    private fun medication() = PatientMedication(
        "prn", "patient-1", "痛み止め", "200 mg", 1.0, 200.0, "mg", null, true, null,
        Instant.parse("2026-01-01T00:00:00Z"), null, null, null, false, 0.0, false,
        true, false, null, null, null,
    )
}
