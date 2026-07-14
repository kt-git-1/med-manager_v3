package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PatientTodayContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersNextSlotInventoryPartialActionAndPrn() {
        lateinit var activity: Activity
        val medications = listOf(
            medication("enough", 10.0),
            medication("short", 0.5),
            medication("prn", 10.0, isPrn = true),
        ).associateBy(PatientMedication::id)
        var bulkSlot: MedicationSlot? = null
        var prnId: String? = null

        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(
                    Modifier.fillMaxSize().background(PatientBackground).safeDrawingPadding(),
                ) {
                    TodayContent(
                        doses = listOf(dose("enough", DoseStatus.PENDING), dose("short", DoseStatus.MISSED)),
                        loading = false,
                        updatingKey = null,
                        error = null,
                        message = null,
                        maintenanceWarning = null,
                        medications = medications,
                        nextSlot = MedicationSlot.MORNING,
                        updatingSlot = null,
                        prnMedications = listOf(medications.getValue("prn")),
                        updatingPrnMedicationId = null,
                        onRetry = {},
                        onRecord = {},
                        onDetail = {},
                        onRecordSlot = { bulkSlot = it },
                        onRecordPrn = { prnId = it.id },
                        onRemind = {},
                        now = Instant.parse("2026-07-13T23:15:00Z"),
                    )
                }
            }
        }

        composeRule.onNodeWithText("次に飲むお薬").assertIsDisplayed()
        composeRule.onAllNodesWithText("在庫不足のお薬が1件あります").onFirst().assertIsDisplayed()
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-101-patient-inventory-partial-light.png")
        composeRule.onNodeWithText("この時間のお薬を飲んだ").performClick()
        composeRule.onNodeWithTag("patient-today-prn-entry").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("痛い時").assertIsDisplayed()
        composeRule.onNodeWithTag("prn-record-prn").performClick()

        composeRule.runOnIdle {
            assertEquals(MedicationSlot.MORNING, bulkSlot)
            assertEquals("prn", prnId)
        }
    }

    @Test
    fun detailContentShowsCanonicalMedicationInformation() {
        val activity = showDoseDetail(notes = "夕食後に服用")

        composeRule.onAllNodesWithText("血圧の薬 5 mg").assertCountEquals(2)
        composeRule.onNodeWithText("2026/07/14 12:30").assertIsDisplayed()
        composeRule.onNodeWithText("記録済み").assertIsDisplayed()
        composeRule.onNodeWithText("メモ").assertIsDisplayed()
        composeRule.onNodeWithText("夕食後に服用").assertIsDisplayed()
        composeRule.onNodeWithText("1回に飲む量").assertIsDisplayed()
        composeRule.onNodeWithText("1回1.5錠").assertIsDisplayed()
        composeRule.onAllNodesWithText("薬の強さ").assertCountEquals(0)
        composeRule.onAllNodesWithText("現在の在庫").assertCountEquals(0)
        composeRule.runOnIdle { normalizeStatusBar(activity) }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture("android-ui-102-patient-dose-detail-light.png")
    }

    @Test
    fun detailContentShowsCurrentIosEmptyNotesFallback() {
        showDoseDetail(notes = null)

        composeRule.onNodeWithText("メモはありません").assertIsDisplayed()
        composeRule.onNodeWithText("1回1.5錠").assertIsDisplayed()
        writeScreenshotFixture(
            composeRule.onRoot().captureToImage(),
            "android-ui-102-patient-dose-detail-empty-notes-light.png",
        )
    }

    @Test
    fun reminderMaintenanceWarningDoesNotReplaceMutationSuccess() {
        val warning = "服薬記録は保存されましたが、通知予定を更新できませんでした。アプリを開いたときに再試行します。"
        composeRule.setContent {
            MedicationAppTheme {
                TodayContent(
                    doses = listOf(dose("med", DoseStatus.TAKEN)),
                    loading = false,
                    updatingKey = null,
                    error = null,
                    message = "服薬を記録しました。",
                    maintenanceWarning = warning,
                    medications = emptyMap(),
                    nextSlot = null,
                    updatingSlot = null,
                    prnMedications = emptyList(),
                    updatingPrnMedicationId = null,
                    onRetry = {},
                    onRecord = {},
                    onDetail = {},
                    onRecordSlot = {},
                    onRecordPrn = {},
                    onRemind = {},
                )
            }
        }

        composeRule.onNodeWithText("服薬を記録しました。").assertIsDisplayed()
        composeRule.onNodeWithText(warning).assertIsDisplayed()
    }

    private fun dose(id: String, status: DoseStatus) = PatientDose(
        key = "dose-$id", medicationId = id, scheduledAt = Instant.parse("2026-07-13T23:00:00Z"),
        status = status, medicationName = medicationName(id), dosageText = "1錠", doseCount = 1.0, slot = MedicationSlot.MORNING,
    )

    private fun medication(id: String, quantity: Double, isPrn: Boolean = false) = PatientMedication(
        id = id, patientId = "patient", name = medicationName(id), dosageText = "1錠", doseCountPerIntake = 1.0,
        dosageStrengthValue = 1.0, dosageStrengthUnit = "mg", notes = null, isPrn = isPrn,
        prnInstructions = if (isPrn) "痛い時" else null, startDate = Instant.EPOCH, endDate = null,
        inventoryCount = quantity, inventoryUnit = "錠", inventoryEnabled = true, inventoryQuantity = quantity,
        inventoryOut = quantity <= 0, isActive = true, isArchived = false, nextScheduledAt = null,
        regimenTimes = null, regimenDaysOfWeek = null,
    )

    private fun medicationName(id: String) = when (id) {
        "enough" -> "血圧の薬 5 mg"
        "short" -> "胃薬"
        "prn" -> "頭痛薬"
        else -> id
    }

    private fun showDoseDetail(notes: String?): Activity {
        lateinit var activity: Activity
        val medication = medication("med", 12.0).copy(
            notes = notes,
            dosageStrengthValue = 5.0,
            dosageStrengthUnit = "mg",
        )
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
                    PatientDoseDetailContent(
                        dose("med", DoseStatus.TAKEN).copy(
                            medicationName = "血圧の薬 5 mg",
                            dosageText = "1回1錠",
                            doseCount = 1.5,
                            scheduledAt = Instant.parse("2026-07-14T03:30:00Z"),
                        ),
                        medication,
                    )
                }
            }
        }
        return activity
    }

    @Suppress("DEPRECATION")
    private fun normalizeStatusBar(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = true
    }
}
