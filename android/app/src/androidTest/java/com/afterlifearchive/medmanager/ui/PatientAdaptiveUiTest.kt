package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDate

@RunWith(Parameterized::class)
class PatientAdaptiveUiTest(private val fontScale: Float) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayPrimaryAndPrnEntryRemainReachableAtAdaptiveFontScale() {
        val now = Instant.parse("2026-07-14T03:00:00Z")
        composeRule.setContent {
            MedicationAppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    TodayContent(
                        doses = listOf(PatientDose("dose", "med", now.plusSeconds(1800), DoseStatus.PENDING, "血圧の薬 5 mg", "1回1錠", 1.0, slot = MedicationSlot.NOON)),
                        loading = false, updatingKey = null, error = null, message = null, maintenanceWarning = null,
                        medications = emptyMap(), nextSlot = MedicationSlot.NOON, updatingSlot = null,
                        prnMedications = emptyList(), updatingPrnMedicationId = null,
                        onRetry = {}, onRecord = {}, onDetail = {}, onRecordSlot = {}, onRecordPrn = {}, onRemind = {}, now = now,
                    )
                }
            }
        }

        composeRule.onNodeWithText("次に飲むお薬").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-today-primary-bulk-record").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("patient-today-list").performScrollToNode(hasTestTag("patient-today-planned"))
        composeRule.onNodeWithTag("patient-today-planned").assertIsDisplayed()
    }

    @Test
    fun historyRecentRecordsRemainReachableAtAdaptiveFontScale() {
        composeRule.setContent {
            MedicationAppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    HistoryContent(
                        days = listOf(HistoryDay("2026-07-14", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.NONE, HistoryStatus.NONE, 0)),
                        loading = false, error = null, retentionCutoffDate = null, retentionDays = null, onRetry = {},
                        now = LocalDate.parse("2026-07-14"),
                    )
                }
            }
        }

        composeRule.onNodeWithText("今日の進捗").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-history-list").performScrollToIndex(3)
        composeRule.onNodeWithText("最近の記録").assertIsDisplayed()
        composeRule.onNodeWithText("今日 7月14日（火）").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsConsentAndLogoutRemainReachableAtAdaptiveFontScale() {
        composeRule.setContent {
            MedicationAppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    SettingsContent(
                        loading = false, error = null, notificationSettings = PatientNotificationSettings(),
                        onNotificationSettingsChange = {}, notificationPermissionDenied = false,
                        analyticsEnabled = false, onAnalyticsEnabledChange = {}, onOpenUrl = {}, onUnlink = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("patient-settings-list").performScrollToNode(hasTestTag("patient-analytics-toggle"))
        composeRule.onNodeWithTag("patient-analytics-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("patient-settings-list").performScrollToIndex(5)
        composeRule.onNodeWithTag("patient-unlink-button").assertIsDisplayed()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "fontScale={0}")
        fun fontScales() = listOf(1.3f, 2f)
    }
}
