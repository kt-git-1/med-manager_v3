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
import androidx.compose.ui.test.assertIsEnabled
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
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.SlotBulkRecordResult
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import androidx.core.view.WindowCompat
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred

class CaregiverTodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentShowsNextActionProgressPrnAndTimelineAggregation() {
        val activity = setContent(
            doses = listOf(
                dose("morning", DoseStatus.TAKEN, "2026-07-14T23:00:00Z"),
                dose("noon", DoseStatus.PENDING, "2026-07-15T04:00:00Z"),
                dose("evening", DoseStatus.MISSED, "2026-07-15T10:00:00Z"),
            ),
            prn = listOf(medication()),
            inventory = listOf(CaregiverInventorySummary("noon", true, 0.0, 1.0, true, true)),
        )

        composeRule.onNodeWithText("さくらさん").assertIsDisplayed()
        composeRule.onNodeWithText("さくらさんを見守り中").assertDoesNotExist()
        composeRule.onNodeWithText("次にすること").assertIsDisplayed()
        composeRule.onAllNodesWithText("昼 13:00").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("1/3回分 完了").assertIsDisplayed()
        composeRule.onNodeWithText("頓服薬が1件あります").assertIsDisplayed()
        composeRule.onNodeWithText("飲み忘れがあります").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-primary-record").assertIsNotEnabled()
        captureDevice(activity, "android-ui-201-caregiver-today-light.png")
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-timeline-noon"))
        composeRule.onNodeWithTag("caregiver-today-slot-action-noon").assertDoesNotExist()
        composeRule.onNodeWithText("在庫不足").assertIsDisplayed()
    }

    @Test
    fun currentIosSourceCalibratedTodayFixtureUsesProductionHeroHierarchy() {
        val activity = setContent(
            patientName = "田中 花子",
            slotTimes = CaregiverSlotTimes("08:00", "12:30", "19:00", "22:00"),
            doses = listOf(
                dose("morning", DoseStatus.TAKEN, "2026-07-14T23:00:00Z", "朝の薬", "5 mg"),
                dose("noon-1", DoseStatus.PENDING, "2026-07-15T03:30:00Z", "血圧の薬", "5 mg"),
                dose("noon-2", DoseStatus.PENDING, "2026-07-15T03:30:00Z", "胃薬", ""),
                dose("evening", DoseStatus.TAKEN, "2026-07-15T10:00:00Z", "夕の薬", "10 mg"),
            ),
        )

        composeRule.onNodeWithText("田中 花子さん").assertIsDisplayed()
        composeRule.onNodeWithText("田中 花子さんを見守り中").assertDoesNotExist()
        composeRule.onNodeWithText("昼 12:30").assertIsDisplayed()
        composeRule.onNodeWithText("血圧の薬 5 mg").assertIsDisplayed()
        composeRule.onNodeWithText("胃薬").assertIsDisplayed()
        composeRule.onNodeWithText("2/3回分 完了").assertIsDisplayed()
        composeRule.onNodeWithText("昼が次に記録する服薬です").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-primary-record").assertIsEnabled()
        captureDevice(activity, "android-ui-201-caregiver-today-source-calibrated-light.png")
    }

    @Test
    fun currentIosSourceCalibratedTimelineUsesFourSlotAndDoseHierarchy() {
        val activity = setContent(
            patientName = "田中 花子",
            slotTimes = CaregiverSlotTimes("08:00", "12:30", "19:00", "22:00"),
            doses = listOf(
                dose("morning", DoseStatus.TAKEN, "2026-07-14T23:00:00Z", "朝の薬", "5 mg", RecordedByType.CAREGIVER),
                dose("noon-1", DoseStatus.PENDING, "2026-07-15T03:30:00Z", "血圧の薬", "5 mg"),
                dose("noon-2", DoseStatus.PENDING, "2026-07-15T03:30:00Z", "胃薬", ""),
                dose("evening", DoseStatus.MISSED, "2026-07-15T10:00:00Z", "夕の薬", "10 mg"),
            ),
        )

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-timeline-title"))
        composeRule.onNodeWithText("飲みました").assertIsDisplayed()
        composeRule.onNodeWithText("次に記録").assertIsDisplayed()
        composeRule.onNodeWithText("2件をまとめて記録").assertIsDisplayed()
        composeRule.onNodeWithText("この時間帯の2件を記録").assertDoesNotExist()
        composeRule.onNodeWithTag("caregiver-today-dose-action-morning").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-dose-action-noon-1").assertDoesNotExist()
        captureDevice(activity, "android-ui-201-caregiver-today-timeline-source-calibrated-light.png")

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-timeline-bedtime"))
        composeRule.onNodeWithText("飲み忘れ").assertIsDisplayed()
        composeRule.onAllNodesWithText("予定なし").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-timeline-bedtime").assertIsDisplayed()
        captureDevice(activity, "android-ui-201-caregiver-today-timeline-empty-slot-light.png")
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
        composeRule.onNodeWithText("再試行").assertIsDisplayed()
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
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-slot-action-noon"))
        composeRule.onNodeWithTag("caregiver-today-slot-action-noon").assertIsNotEnabled()
    }

    @Test
    fun takenDoseActionDeletesThroughCurrentIosTimeline() {
        var current = listOf(dose("dose-1", DoseStatus.TAKEN, "2026-07-14T23:00:00Z", recordedByType = RecordedByType.CAREGIVER))
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = current
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
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
        composeRule.onNodeWithText("服用記録を取り消しました").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-dose-action-dose-1").assertDoesNotExist()
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
        composeRule.onNodeWithText("朝の未記録・飲み忘れ1件を代理で", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-slot-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-mutation-message"))
        composeRule.onNodeWithText("1件を記録しました").assertIsDisplayed()
    }

    @Test
    fun prnPickerRequiresMedicationConfirmationAndShowsSuccess() {
        lateinit var activity: Activity
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
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-prn-open").performClick()
        composeRule.onNodeWithTag("caregiver-today-prn-picker").assertIsDisplayed()
        composeRule.onNodeWithText("飲んだ薬を選んでください").assertIsDisplayed()
        composeRule.onNodeWithText("今服用した頓服を家族が代理で記録できます。").assertIsDisplayed()
        composeRule.onNodeWithText("痛み止め 200 mg").assertIsDisplayed()
        composeRule.onNodeWithText("1回1錠").assertIsDisplayed()
        captureDevice(activity, "android-ui-201-caregiver-prn-list-light.png")
        composeRule.onNodeWithTag("caregiver-today-prn-prn").performClick()
        composeRule.onNodeWithTag("caregiver-today-prn-confirm-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("さくらさんが「痛み止め」を今服用した記録を残します。").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-prn-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-today-prn-picker").assertDoesNotExist()
        composeRule.onNodeWithText("頓服を記録しました").assertIsDisplayed()
    }

    @Test
    fun initialLoadUsesCurrentIosMessageState() {
        val release = CompletableDeferred<Unit>()
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> { release.await(); return emptyList() }
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら")
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
                }
            }
        }

        composeRule.onNodeWithTag("caregiver-today-loading").assertIsDisplayed()
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        captureDevice(activity, "android-ui-201-caregiver-today-loading-light.png")
        release.complete(Unit)
    }

    @Test
    fun prnFailureKeepsCurrentIosListOpenWithRetryAction() {
        val prn = medication()
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = emptyList<PatientDose>()
            override suspend fun medications(patientId: String) = listOf(prn)
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordPrn(patientId: String, medication: PatientMedication) = error("offline")
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
        composeRule.onNodeWithTag("caregiver-today-prn-prn").performClick()
        composeRule.onNodeWithTag("caregiver-today-prn-confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-prn-picker").assertIsDisplayed()
        composeRule.onAllNodesWithText("更新できませんでした。通信状態を確認して、もう一度お試しください。").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-prn-prn").assertIsDisplayed()
    }

    @Test
    fun successfulRecordRemainsRenderedWhenAutomaticRefreshFails() {
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-14T23:00:00Z")
        var refreshFails = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> = if (refreshFails) error("offline") else listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot): SlotBulkRecordResult {
                refreshFails = true
                return SlotBulkRecordResult(1, 0, 0, 1.0, 1, "08:00", MedicationSlot.entries.associateWith { HistoryStatus.NONE }, "group-1")
            }
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-primary-record").performClick()
        composeRule.onNodeWithTag("caregiver-today-slot-confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caregiver-today-list").performScrollToNode(hasTestTag("caregiver-today-mutation-message"))
        composeRule.onNodeWithText("1件を記録しました").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-today-mutation-error").assertDoesNotExist()
        composeRule.onNodeWithText("情報を取得できませんでした").assertDoesNotExist()
    }

    private fun setContent(
        doses: List<PatientDose> = emptyList(),
        prn: List<PatientMedication> = emptyList(),
        inventory: List<CaregiverInventorySummary> = emptyList(),
        patientName: String = "さくら",
        slotTimes: CaregiverSlotTimes = CaregiverSlotTimes("08:00", "13:00", "19:00", "22:00"),
        onOpenMedications: () -> Unit = {},
    ): Activity {
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = doses
            override suspend fun medications(patientId: String) = prn
            override suspend fun inventory(patientId: String) = inventory
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", patientName, slotTimes)
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverTodayScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, onOpenMedications)
                }
            }
        }
        composeRule.waitForIdle()
        return activity
    }

    private fun repository() = CaregiverTodayRepository(object : CaregiverTodayDataSource {
        override suspend fun today(patientId: String) = emptyList<PatientDose>()
        override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
        override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
    }, MutationFreshnessStore())

    private fun dose(
        id: String,
        status: DoseStatus,
        time: String,
        medicationName: String = "薬$id",
        dosageText: String = "5 mg",
        recordedByType: RecordedByType? = null,
    ) = PatientDose(
        key = id,
        medicationId = id,
        scheduledAt = Instant.parse(time),
        status = status,
        medicationName = medicationName,
        dosageText = dosageText,
        doseCount = 1.0,
        patientId = "patient-1",
        recordedByType = recordedByType,
    )

    private fun medication() = PatientMedication(
        "prn", "patient-1", "痛み止め", "200 mg", 1.0, 200.0, "mg", null, true, null,
        Instant.parse("2026-01-01T00:00:00Z"), null, null, null, false, 0.0, false,
        true, false, null, null, null,
    )

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
