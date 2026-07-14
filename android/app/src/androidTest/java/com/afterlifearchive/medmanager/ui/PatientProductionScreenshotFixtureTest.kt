package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientProductionScreenshotFixtureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayFixtureCapturesProductionComponent() {
        val scheduledAt = Instant.parse("2026-07-14T23:00:00Z")
        composeRule.setContent {
            MedicationAppTheme {
                TodayContent(
                    doses = listOf(
                        PatientDose("morning", "med-1", scheduledAt, DoseStatus.PENDING, "血圧のお薬", "1回1錠", 1.0, slot = MedicationSlot.MORNING),
                        PatientDose("noon", "med-2", scheduledAt.plusSeconds(18_000), DoseStatus.TAKEN, "昼のお薬", "1回2錠", 2.0, slot = MedicationSlot.NOON),
                    ),
                    loading = false,
                    updatingKey = null,
                    error = null,
                    message = null,
                    maintenanceWarning = null,
                    medications = emptyMap(),
                    nextSlot = MedicationSlot.MORNING,
                    updatingSlot = null,
                    prnMedications = emptyList(),
                    updatingPrnMedicationId = null,
                    onRetry = {},
                    onRecord = {},
                    onDetail = {},
                    onRecordSlot = {},
                    onRecordPrn = {},
                    onRemind = {},
                    now = scheduledAt,
                )
            }
        }

        assertNonEmptyCapture()
    }

    @Test
    fun historyFixtureCapturesProductionComponent() {
        composeRule.setContent {
            MedicationAppTheme {
                HistoryContent(
                    days = listOf(
                        HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, 0),
                        HistoryDay("2026-07-14", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, 1),
                    ),
                    loading = false,
                    error = null,
                    retentionCutoffDate = null,
                    retentionDays = null,
                    onRetry = {},
                    now = LocalDate.parse("2026-07-14"),
                )
            }
        }

        assertNonEmptyCapture()
    }

    @Test
    fun settingsFixtureCapturesProductionComponent() {
        composeRule.setContent {
            MedicationAppTheme {
                SettingsContent(
                    loading = false,
                    error = null,
                    notificationSettings = PatientNotificationSettings(masterEnabled = true),
                    onNotificationSettingsChange = {},
                    onOpenUrl = {},
                    onUnlink = {},
                )
            }
        }

        assertNonEmptyCapture()
    }

    private fun assertNonEmptyCapture() {
        val image = composeRule.onRoot().captureToImage()
        assertTrue(image.width > 1)
        assertTrue(image.height > 1)
    }
}
