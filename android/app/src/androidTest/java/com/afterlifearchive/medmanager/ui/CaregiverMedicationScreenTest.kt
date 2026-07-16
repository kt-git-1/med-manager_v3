package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.core.view.WindowCompat

class CaregiverMedicationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentShowsMetricsFiltersScheduleAndInventory() {
        setContent(listOf(scheduled(), prn(), ended()))

        composeRule.onNodeWithText("薬を管理").assertIsDisplayed()
        composeRule.onNodeWithText("さくらさん").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-scheduled").assertIsDisplayed()
        composeRule.onNodeWithText("毎日 朝・夕").assertIsDisplayed()
        composeRule.onNodeWithText("残り12錠").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-filter-prn").performClick()
        composeRule.onNodeWithTag("caregiver-medication-prn").assertIsDisplayed()
        composeRule.onNodeWithText("必要な時").assertIsDisplayed()
    }

    @Test
    fun selectedPatientWithNoMedicationShowsCanonicalEmptyState() {
        val activity = setContent(emptyList(), slotTimes = iosDefaultSlotTimes())

        composeRule.onNodeWithText("薬がありません").assertIsDisplayed()
        composeRule.onNodeWithText("まず1つ目の薬を登録しましょう。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("薬名、用量、1回に飲む量を入力").assertIsDisplayed()
        composeRule.onNodeWithText("定時薬は飲む時間と曜日を設定").assertIsDisplayed()
        composeRule.onNodeWithText("必要に応じて在庫管理をオン").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-empty-add").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-add").assertDoesNotExist()
        captureDevice(activity, "android-ui-202-caregiver-medications-empty-light-matched.png")
    }

    @Test
    fun initialLoadShowsIosLoadingMessage() {
        val gate = CompletableDeferred<Unit>()
        val repository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { gate.await(); emptyList() },
            MutationFreshnessStore(),
        )
        val patient = CaregiverPatient("patient-1", "さくら")
        val activity = setScreen(repository, CaregiverPatientState(listOf(patient), patient.id))

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-medication-loading").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        captureDevice(activity, "android-ui-202-caregiver-medications-loading-light-matched.png")
        gate.complete(Unit)
    }

    @Test
    fun initialFailureMatchesCurrentIosRecoveryAndLoginActions() {
        val repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { error("offline") }, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら")
        var returnedToLogin = false
        val activity = setScreen(
            repository,
            CaregiverPatientState(listOf(patient), patient.id),
            onReturnToLogin = { returnedToLogin = true },
        )

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-medication-unavailable").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("情報を取得できませんでした").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-retry").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-return-login").assertIsDisplayed()
        captureDevice(activity, "android-ui-202-caregiver-medications-error-light-matched.png")
        composeRule.onNodeWithTag("caregiver-medication-return-login").performClick()
        assertTrue(returnedToLogin)
    }

    @Test
    fun noPatientStateRoutesToRegistration() {
        var opened = false
        val activity = setScreen(
            repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { emptyList() }, MutationFreshnessStore()),
            patientState = CaregiverPatientState(emptyList(), null),
            onCreatePatient = { opened = true },
        )

        composeRule.onNodeWithText("患者がいません").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-create-patient").assertIsDisplayed().performClick()
        assertTrue(opened)
        captureDevice(activity, "android-ui-202-caregiver-medications-no-patient-light-matched.png")
    }

    @Test
    fun selectionRequiredStateRoutesToPatientSettings() {
        var opened = false
        val activity = setScreen(
            repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { emptyList() }, MutationFreshnessStore()),
            patientState = CaregiverPatientState(listOf(CaregiverPatient("patient-1", "さくら")), null),
            onOpenPatients = { opened = true },
        )

        composeRule.onNodeWithText("見守る方を選択してください").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-open-patients").assertIsDisplayed().performClick()
        assertTrue(opened)
        captureDevice(activity, "android-ui-202-caregiver-medications-selection-light-matched.png")
    }

    @Test
    fun screenshotFixtureShowsSectionedMedicationList() {
        val items = listOf(scheduled(), prn(), ended())
        val repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { items }, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"))
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverMedicationScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true)
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-medication-list").fetchSemanticsNodes().isNotEmpty() }
        captureDevice(activity, "android-ui-202-caregiver-medications-light.png")
    }

    @Test
    fun currentIosSourceCalibratedMedicationListUsesProductionHierarchy() {
        val items = listOf(
            medication("blood-pressure", "血圧の薬", false, null, 18.0, listOf("08:00", "12:00"), "5 mg"),
            medication("stomach", "整腸剤", false, null, 10.0, listOf("18:00"), "50 mg"),
            medication("headache", "頭痛薬", true, null, 0.0, null, ""),
        )
        val activity = setContent(items, patientName = "田中 花子")

        composeRule.onNodeWithText("薬を管理").assertIsDisplayed()
        composeRule.onNodeWithText("田中 花子さん").assertIsDisplayed()
        composeRule.onNodeWithText("追加").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("血圧の薬").assertIsDisplayed()
        composeRule.onNodeWithText("用量 5 mg").assertIsDisplayed()
        composeRule.onNodeWithText("毎日 朝・昼").assertIsDisplayed()
        composeRule.onNodeWithText("残り18錠").assertIsDisplayed()
        captureDevice(activity, "android-ui-202-caregiver-medications-populated-light-matched.png")

        composeRule.onNodeWithTag("caregiver-medication-filter-ended").performClick()
        composeRule.onNodeWithText("該当する薬はありません").assertDoesNotExist()
        composeRule.onNodeWithText("終了した定時薬").assertDoesNotExist()
        captureDevice(activity, "android-ui-202-caregiver-medications-filter-ended-light-matched.png")
    }

    @Test
    fun emptyStateMatchesCurrentIosInDarkMode() {
        val emptyActivity = setContent(emptyList(), darkTheme = true)
        composeRule.onNodeWithTag("caregiver-medication-empty-add").assertIsDisplayed()
        captureDevice(emptyActivity, "android-ui-202-caregiver-medications-empty-dark-matched.png", darkTheme = true)
    }

    @Test
    fun populatedStateMatchesCurrentIosInDarkMode() {
        val populatedActivity = setContent(listOf(scheduled(), prn(), ended()), darkTheme = true)
        composeRule.onNodeWithTag("caregiver-medication-scheduled").assertIsDisplayed()
        captureDevice(populatedActivity, "android-ui-202-caregiver-medications-populated-dark-matched.png", darkTheme = true)
    }

    @Test
    fun emptyActionRemainsReachableAtTwoHundredPercentText() {
        val emptyActivity = setContent(emptyList(), fontScale = 2f)
        composeRule.onNodeWithTag("caregiver-medication-list").performScrollToNode(hasTestTag("caregiver-medication-empty-add"))
        composeRule.onNodeWithTag("caregiver-medication-empty-add").assertIsDisplayed()
        captureDevice(emptyActivity, "android-ui-202-caregiver-medications-empty-font-2.0-matched.png")
    }

    @Test
    fun populatedEditRemainsReachableAtTwoHundredPercentText() {
        val populatedActivity = setContent(listOf(scheduled(), prn(), ended()), fontScale = 2f)
        composeRule.onNodeWithTag("caregiver-medication-list").performScrollToNode(hasTestTag("caregiver-medication-edit-scheduled"))
        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").assertIsDisplayed()
        captureDevice(populatedActivity, "android-ui-202-caregiver-medications-populated-font-2.0-matched.png")
    }

    @Test
    fun screenshotFixtureShowsMedicationForm() {
        val repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { emptyList() }, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"))
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverMedicationScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true)
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-medication-empty-add").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").assertIsDisplayed()
        composeRule.onNodeWithTag("medication-name").performTextInput("血圧の薬")
        composeRule.onNodeWithTag("medication-strength-value").performTextInput("5")
        composeRule.onNodeWithTag("medication-strength-unit").performTextInput("mg")
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-slot-morning"))
        composeRule.onNodeWithTag("medication-slot-morning").performClick()
        composeRule.onNodeWithTag("medication-slot-evening").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-editor-hero"))
        activity.runOnUiThread {
            activity.currentFocus?.clearFocus()
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        composeRule.waitForIdle()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-source-calibrated-light.png")
    }

    @Test
    fun addFormCapturesCurrentIosBasicSchedulePrnAndWeeklyStates() {
        val activity = setContent(emptyList())
        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()

        composeRule.onNodeWithTag("medication-editor-hero").assertIsDisplayed()
        composeRule.onNodeWithText("基本情報").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-add-basic-light-matched.png")

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-frequency-daily"))
        composeRule.onNodeWithText("本人画面に出す時間を選択").assertIsDisplayed()
        composeRule.onNodeWithText("時間帯").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-add-schedule-light-matched.png")

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-kind-prn"))
        composeRule.onNodeWithTag("medication-kind-prn").performClick()
        composeRule.onNodeWithTag("medication-prn-instructions").assertIsDisplayed()
        composeRule.onNodeWithText("頓服は決まった時間には出さず", substring = true).assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-prn-light-matched.png")

        composeRule.onNodeWithTag("medication-kind-scheduled").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-frequency-weekly"))
        composeRule.onNodeWithTag("medication-frequency-weekly").performClick()
        composeRule.onNodeWithTag("medication-day-mon").performClick()
        composeRule.onNodeWithTag("medication-slot-morning").performClick()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-weekly-light-matched.png")
    }

    @Test
    fun editFormCapturesTopLowerAndDeleteConfirmationStates() {
        val activity = setContent(
            listOf(medication("scheduled", "アムロジピン", false, null, 12.0, listOf("08:00", "19:00"))),
            slotTimes = iosDefaultSlotTimes(),
        )
        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").performClick()

        composeRule.onNodeWithTag("medication-name").assertTextEquals("アムロジピン")
        composeRule.onNodeWithTag("medication-editor-hero").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-edit-basic-light-matched.png")

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-delete"))
        composeRule.onNodeWithTag("medication-inventory").assertDoesNotExist()
        composeRule.onNodeWithTag("medication-delete").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-edit-lower-light-matched.png")

        composeRule.onNodeWithTag("medication-delete").performClick()
        composeRule.onNodeWithTag("medication-delete-dialog").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-delete-confirm-light-matched.png")
    }

    @Test
    fun validationFailureUsesCurrentIosErrorHierarchy() {
        val activity = setContent(emptyList(), slotTimes = iosDefaultSlotTimes())
        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-save"))
        composeRule.onNodeWithTag("medication-save").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-validation-errors"))
        composeRule.onNodeWithTag("medication-validation-errors").assertIsDisplayed()
        composeRule.onNodeWithText("薬名は必須です", substring = true).assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-validation-light-matched.png")
    }

    @Test
    fun addFormMatchesCurrentIosInDarkMode() {
        val activity = setContent(emptyList(), slotTimes = iosDefaultSlotTimes(), darkTheme = true)
        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()

        composeRule.onNodeWithTag("medication-editor-hero").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-add-basic-dark-matched.png", darkTheme = true)
    }

    @Test
    fun addFormSaveRemainsReachableAtTwoHundredPercent() {
        val activity = setContent(emptyList(), slotTimes = iosDefaultSlotTimes(), fontScale = 2f)
        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-save"))
        composeRule.onNodeWithTag("medication-save").assertIsDisplayed()
        captureDevice(activity, "android-ui-203-caregiver-medication-form-add-font-2.0-matched.png")
    }

    @Test
    fun failedRefreshKeepsMedicationVisibleAndDisablesEditing() {
        var fail = false
        val repository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { if (fail) error("offline") else listOf(scheduled()) },
            MutationFreshnessStore(),
        )
        runBlocking { repository.load("patient-1") }
        fail = true
        runBlocking { repository.load("patient-1") }
        val patient = CaregiverPatient("patient-1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverMedicationScreen(repository, CaregiverPatientState(listOf(patient), patient.id), enabled = true)
            }
        }

        composeRule.onNodeWithTag("caregiver-medication-stale").assertIsDisplayed()
        composeRule.onNodeWithText("アムロジピン").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-add").assertIsNotEnabled()
        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").assertIsNotEnabled()
    }

    @Test
    fun addFormShowsConditionalPrnFieldAndValidationSummary() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-kind-prn"))
        composeRule.onNodeWithTag("medication-kind-prn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-prn-instructions"))
        composeRule.onNodeWithTag("medication-prn-instructions").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-save"))
        composeRule.onNodeWithTag("medication-save").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-validation-errors"))
        composeRule.onNodeWithTag("medication-validation-errors").assertIsDisplayed()
        composeRule.onNodeWithText("薬名は必須です", substring = true).assertIsDisplayed()
    }

    @Test
    fun addFormProvidesIOSParityDosageUnitPicker() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()
        composeRule.onNodeWithTag("medication-strength-unit-menu").performClick()
        composeRule.onNodeWithTag("medication-strength-unit-mg").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("medication-strength-unit").assertTextEquals("mg")
    }

    @Test
    fun editFormIsPrepopulatedFromSelectedMedication() {
        setContent(listOf(scheduled()))

        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").assertIsDisplayed()
        composeRule.onNodeWithTag("medication-name").assertTextEquals("アムロジピン")
        composeRule.onNodeWithTag("medication-strength-value").assertTextEquals("5")
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-slot-morning"))
        composeRule.onNodeWithTag("medication-slot-morning").assertExists()
    }

    @Test
    fun regularScheduleSupportsWeeklyDaysAndPrnHidesSchedule() {
        setContent(emptyList())
        composeRule.onNodeWithTag("caregiver-medication-empty-add").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-frequency-weekly"))
        composeRule.onNodeWithTag("medication-frequency-weekly").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-day-mon"))
        composeRule.onNodeWithTag("medication-day-mon").assertExists().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("medication-slot-morning").assertExists().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-kind-prn"))
        composeRule.onNodeWithTag("medication-kind-prn").performClick()
        composeRule.onNodeWithTag("medication-frequency-weekly").assertDoesNotExist()
    }

    @Test
    fun editFormRequiresExplicitDestructiveDeleteConfirmation() {
        setContent(listOf(scheduled()))
        composeRule.onNodeWithTag("caregiver-medication-edit-scheduled").performClick()

        composeRule.onNodeWithTag("caregiver-medication-form").performScrollToNode(hasTestTag("medication-delete"))
        composeRule.onNodeWithTag("medication-delete").performClick()

        composeRule.onNodeWithTag("medication-delete-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("薬を削除しますか？").assertIsDisplayed()
        composeRule.onNodeWithText("この操作は取り消せません。").assertIsDisplayed()
    }

    private fun setContent(
        items: List<PatientMedication>,
        patientName: String = "さくら",
        slotTimes: CaregiverSlotTimes = CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"),
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ): Activity {
        val repository = CaregiverMedicationRepository(CaregiverMedicationDataSource { items }, MutationFreshnessStore())
        val patient = CaregiverPatient("patient-1", patientName, slotTimes)
        return setScreen(repository, CaregiverPatientState(listOf(patient), patient.id), darkTheme = darkTheme, fontScale = fontScale)
    }

    private fun setScreen(
        repository: CaregiverMedicationRepository,
        patientState: CaregiverPatientState,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        onReturnToLogin: () -> Unit = {},
        onOpenPatients: () -> Unit = {},
        onCreatePatient: () -> Unit = {},
    ): Activity {
        lateinit var activity: Activity
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                        CaregiverMedicationScreen(
                            repository,
                            patientState,
                            enabled = true,
                            onReturnToLogin = onReturnToLogin,
                            onOpenPatients = onOpenPatients,
                            onCreatePatient = onCreatePatient,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return activity
    }

    private fun scheduled() = medication("scheduled", "アムロジピン", false, null, 12.0, listOf("08:00", "18:00"))
    private fun iosDefaultSlotTimes() = CaregiverSlotTimes("08:00", "13:00", "19:00", "22:00")
    private fun prn() = medication("prn", "ロキソニン", true, null, 0.0, null)
    private fun ended() = medication("ended", "終了薬", false, Instant.parse("2026-01-01T00:00:00Z"), 0.0, listOf("12:00"))

    private fun medication(
        id: String,
        name: String,
        isPrn: Boolean,
        endDate: Instant?,
        inventory: Double,
        times: List<String>?,
        dosageText: String = "5mg",
    ) = PatientMedication(
        id, "patient-1", name, dosageText, 1.0, 5.0, "mg", null, isPrn, null,
        Instant.parse("2025-01-01T00:00:00Z"), endDate, inventory, "錠", inventory > 0, inventory, false,
        endDate == null, false, null, times, emptyList(),
    )

    @Suppress("DEPRECATION")
    private fun captureDevice(activity: Activity, filename: String, darkTheme: Boolean = false) {
        composeRule.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture(filename)
    }
}
