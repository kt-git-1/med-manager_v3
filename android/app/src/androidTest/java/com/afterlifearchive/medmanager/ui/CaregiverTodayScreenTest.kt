package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
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
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
        composeRule.onAllNodesWithText("昼 13:00").assertCountEquals(2)
        composeRule.onNodeWithText("1 / 3 回完了").assertIsDisplayed()
        composeRule.onNodeWithText("頓服薬が1件あります").assertIsDisplayed()
        composeRule.onNodeWithText("未記録の時間帯があります").assertIsDisplayed()
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
