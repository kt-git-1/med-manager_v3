package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PatientTodayContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersNextSlotInventoryPartialActionAndPrn() {
        val medications = listOf(
            medication("enough", 10.0),
            medication("short", 0.5),
            medication("prn", 10.0, isPrn = true),
        ).associateBy(PatientMedication::id)
        var bulkSlot: MedicationSlot? = null
        var prnId: String? = null

        composeRule.setContent {
            MedicationAppTheme {
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
                    now = Instant.parse("2026-07-12T23:15:00Z"),
                )
            }
        }

        composeRule.onNodeWithText("次のお薬").assertIsDisplayed()
        composeRule.onNodeWithText("在庫不足のお薬が1件あります").assertIsDisplayed()
        composeRule.onNodeWithText("在庫を確認してください").assertIsNotEnabled()
        composeRule.onNodeWithText("この時間帯をまとめて記録（1件）").performClick()
        composeRule.onNodeWithText("必要なときのお薬").assertIsDisplayed()
        composeRule.onNodeWithText("痛い時").assertIsDisplayed()
        composeRule.onNodeWithTag("prn-record-prn").performClick()

        composeRule.runOnIdle {
            assertEquals(MedicationSlot.MORNING, bulkSlot)
            assertEquals("prn", prnId)
        }
    }

    @Test
    fun detailContentShowsCanonicalMedicationInformation() {
        val medication = medication("med", 12.0).copy(
            notes = "夕食後に服用",
            dosageStrengthValue = 5.0,
            dosageStrengthUnit = "mg",
        )
        composeRule.setContent {
            MedicationAppTheme {
                PatientDoseDetailContent(dose("med", DoseStatus.TAKEN).copy(doseCount = 1.5), medication)
            }
        }

        composeRule.onNodeWithText("med").assertIsDisplayed()
        composeRule.onNodeWithText("服用済み", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("夕食後に服用").assertIsDisplayed()
        composeRule.onNodeWithText("1.5錠").assertIsDisplayed()
        composeRule.onNodeWithText("5 mg").assertIsDisplayed()
        composeRule.onNodeWithText("12 錠").assertIsDisplayed()
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
        key = "dose-$id", medicationId = id, scheduledAt = Instant.parse("2026-07-12T23:00:00Z"),
        status = status, medicationName = id, dosageText = "1錠", doseCount = 1.0, slot = MedicationSlot.MORNING,
    )

    private fun medication(id: String, quantity: Double, isPrn: Boolean = false) = PatientMedication(
        id = id, patientId = "patient", name = id, dosageText = "1錠", doseCountPerIntake = 1.0,
        dosageStrengthValue = 1.0, dosageStrengthUnit = "mg", notes = null, isPrn = isPrn,
        prnInstructions = if (isPrn) "痛い時" else null, startDate = Instant.EPOCH, endDate = null,
        inventoryCount = quantity, inventoryUnit = "錠", inventoryEnabled = true, inventoryQuantity = quantity,
        inventoryOut = quantity <= 0, isActive = true, isArchived = false, nextScheduledAt = null,
        regimenTimes = null, regimenDaysOfWeek = null,
    )
}
