package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.AnalyticsConsentState
import com.afterlifearchive.medmanager.AnalyticsConsentStore
import com.afterlifearchive.medmanager.AnalyticsService
import com.afterlifearchive.medmanager.AnalyticsTransport
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventorySummary
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class CaregiverAdaptiveUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayPrimaryActionRemainsReachableAtOneHundredThirtyPercent() {
        val patient = CaregiverPatient("p1", "さくら")
        val patientState = CaregiverPatientState(listOf(patient), patient.id)
        val todayRepository = CaregiverTodayRepository(object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = emptyList<PatientDose>()
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }, MutationFreshnessStore())

        composeRule.setContent {
            FontScaled(1.3f) {
                CaregiverTodayScreen(todayRepository, patientState, enabled = true, onOpenMedications = {})
            }
        }
        composeRule.onNodeWithTag("caregiver-today-open-medications").assertIsDisplayed()
    }

    @Test
    fun medicationPrimaryActionRemainsReachableAtOneHundredThirtyPercent() {
        val patient = CaregiverPatient("p1", "さくら")
        val patientState = CaregiverPatientState(listOf(patient), patient.id)
        val medicationRepository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { emptyList() },
            MutationFreshnessStore(),
        )
        composeRule.setContent {
            FontScaled(1.3f) {
                CaregiverMedicationScreen(medicationRepository, patientState, enabled = true)
            }
        }
        composeRule.onNodeWithTag("caregiver-medication-add").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form")
            .performScrollToNode(hasTestTag("medication-save"))
        composeRule.onNodeWithTag("medication-save").assertIsDisplayed()
    }

    @Test
    fun inventoryDetailActionRemainsReachableAtOneHundredThirtyPercent() {
        val patient = CaregiverPatient("p1", "さくら")
        val patientState = CaregiverPatientState(listOf(patient), patient.id)
        val inventoryItem = CaregiverInventoryItem(
            "med-1", "血圧の薬", false, 1.0, true, 2.0, 3, false, true, false,
            1.0, 7.0, 14.0, 21.0, 2, "2026-07-18",
        )
        val inventoryRepository = CaregiverInventoryRepository(object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = listOf(inventoryItem)
            override suspend fun update(
                patientId: String,
                medicationId: String,
                enabled: Boolean,
                quantity: Double?,
            ) = inventoryItem

            override suspend fun adjust(
                patientId: String,
                medicationId: String,
                reason: String,
                delta: Double?,
                absoluteQuantity: Double?,
            ) = inventoryItem
        }, MutationFreshnessStore())

        composeRule.setContent {
            FontScaled(1.3f) {
                CaregiverInventoryScreen(inventoryRepository, patientState, enabled = true, onOpenMedications = {})
            }
        }
        composeRule.onNodeWithTag("caregiver-inventory-item-med-1").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-detail")
            .performScrollToNode(hasTestTag("inventory-save-settings"))
        composeRule.onNodeWithTag("inventory-save-settings").assertIsDisplayed()
    }

    @Test
    fun historyDetailRemainsReachableAtOneHundredThirtyPercent() {
        val patient = CaregiverPatient("p1", "さくら")
        val patientState = CaregiverPatientState(listOf(patient), patient.id)
        val date = LocalDate.of(2026, 7, 15)
        val historyRepository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(date.toString(), HistoryStatus.MISSED, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            )

            override suspend fun day(patientId: String, date: LocalDate) =
                HistoryDayDetail(date.toString(), emptyList(), emptyList())

            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        composeRule.setContent {
            FontScaled(1.3f) {
                CaregiverHistoryScreen(historyRepository, patientState, enabled = true)
            }
        }
        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { historyRepository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-day-sheet").assertIsDisplayed()
    }

    @Test
    fun caregiverSettingsAnalyticsAndLogoutRemainReachableAtTwoHundredPercent() {
        val storage = AdaptiveSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            CaregiverPatientDataSource { listOf(CaregiverPatient("p1", "さくら")) },
            selection,
        )
        val analytics = AnalyticsService(
            AdaptiveAnalyticsStore(),
            AdaptiveAnalyticsTransport(),
        ).also { it.configure() }
        composeRule.setContent {
            FontScaled(2f) {
                CaregiverHomeScreen(repository, analyticsService = analytics, tutorialEnabled = false)
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.selectedPatientId == "p1" }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list")
            .performScrollToNode(hasTestTag("caregiver-analytics-settings"))
        composeRule.onNodeWithTag("caregiver-analytics-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-list")
            .performScrollToNode(hasTestTag("caregiver-logout"))
        composeRule.onNodeWithTag("caregiver-logout").assertIsDisplayed()
    }
}

@Composable
private fun FontScaled(scale: Float, content: @Composable () -> Unit) {
    MedicationAppTheme {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
            content()
        }
    }
}

private class AdaptiveSelectionStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}

private class AdaptiveAnalyticsStore : AnalyticsConsentStore {
    private var value = AnalyticsConsentState(enabled = false, decided = true)
    override fun state() = value
    override fun save(enabled: Boolean) {
        value = AnalyticsConsentState(enabled, decided = true)
    }
}

private class AdaptiveAnalyticsTransport : AnalyticsTransport {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun reset() = Unit
    override fun log(name: String, parameters: Map<String, String>) = Unit
}
