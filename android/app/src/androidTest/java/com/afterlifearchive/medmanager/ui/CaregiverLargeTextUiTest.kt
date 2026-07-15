package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
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
import com.afterlifearchive.medmanager.data.caregiver.CaregiverLinkingCode
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CaregiverLargeTextUiTest(private val darkTheme: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val patient = CaregiverPatient("p1", "さくら")
    private val patientState = CaregiverPatientState(listOf(patient), patient.id)

    @Test
    fun todayMedicationActionRemainsReachableAtTwoHundredPercent() {
        val repository = CaregiverTodayRepository(object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = emptyList<PatientDose>()
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }, MutationFreshnessStore())

        composeRule.setContent {
            CaregiverLargeText(darkTheme) {
                CaregiverTodayScreen(repository, patientState, enabled = true, onOpenMedications = {})
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-today-list")
            .performScrollToNode(hasTestTag("caregiver-today-open-medications"))
        composeRule.onNodeWithTag("caregiver-today-open-medications").assertIsDisplayed()
        captureDarkFixture("android-caregiver-today-dark-font-2.0.png")
    }

    @Test
    fun medicationSaveRemainsReachableAtTwoHundredPercent() {
        val repository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { emptyList() },
            MutationFreshnessStore(),
        )

        composeRule.setContent {
            CaregiverLargeText(darkTheme) {
                CaregiverMedicationScreen(repository, patientState, enabled = true)
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-medication-list")
            .performScrollToNode(hasTestTag("caregiver-medication-add"))
        composeRule.onNodeWithTag("caregiver-medication-add").performClick()
        composeRule.onNodeWithTag("caregiver-medication-form")
            .performScrollToNode(hasTestTag("medication-save"))
        composeRule.onNodeWithTag("medication-save").assertIsDisplayed()
        captureDarkFixture("android-caregiver-medication-form-dark-font-2.0.png")
    }

    @Test
    fun inventoryCorrectionRemainsReachableAtTwoHundredPercent() {
        val item = CaregiverInventoryItem(
            "med-1", "血圧の薬", false, 1.0, true, 2.0, 3, false, true, false,
            1.0, 7.0, 14.0, 21.0, 2, "2026-07-18",
        )
        val repository = CaregiverInventoryRepository(object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = listOf(item)
            override suspend fun update(
                patientId: String,
                medicationId: String,
                enabled: Boolean,
                quantity: Double?,
            ) = item

            override suspend fun adjust(
                patientId: String,
                medicationId: String,
                reason: String,
                delta: Double?,
                absoluteQuantity: Double?,
            ) = item
        }, MutationFreshnessStore())

        composeRule.setContent {
            CaregiverLargeText(darkTheme) {
                CaregiverInventoryScreen(repository, patientState, enabled = true, onOpenMedications = {})
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-inventory-list")
            .performScrollToNode(hasTestTag("caregiver-inventory-item-med-1"))
        composeRule.onNodeWithTag("caregiver-inventory-item-med-1").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll")
            .performScrollToNode(hasTestTag("inventory-correction"))
        composeRule.onNodeWithTag("inventory-correction").assertIsDisplayed()
        captureDarkFixture("android-caregiver-inventory-detail-dark-font-2.0.png")
    }

    @Test
    fun historyInlineDetailRemainsReachableAtTwoHundredPercent() {
        val date = LocalDate.of(2026, 7, 15)
        val repository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(date.toString(), HistoryStatus.MISSED, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            )

            override suspend fun day(patientId: String, date: LocalDate) =
                HistoryDayDetail(date.toString(), emptyList(), emptyList())

            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())

        composeRule.setContent {
            CaregiverLargeText(darkTheme) {
                CaregiverHistoryScreen(repository, patientState, enabled = true)
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.monthLoaded }
        captureDarkFixture("android-caregiver-history-dark-font-2.0.png")
        composeRule.onNodeWithTag("caregiver-history-month")
            .performScrollToNode(hasTestTag("caregiver-history-day-$date"))
        composeRule.onNodeWithTag("caregiver-history-day-$date").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.dayDetail != null }
        composeRule.onNodeWithTag("caregiver-history-month").performScrollToNode(hasTestTag("caregiver-history-day-detail"))
        composeRule.onNodeWithTag("caregiver-history-day-detail").assertIsDisplayed()
    }

    @Test
    fun settingsSheetsRemainReachableAtTwoHundredPercent() {
        val storage = LargeTextSelectionStorage().apply { currentPatientId = "p1" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            object : CaregiverPatientDataSource {
                override suspend fun listPatients() = listOf(
                    CaregiverPatient("p1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")),
                )
                override suspend fun issueLinkingCode(patientId: String) =
                    CaregiverLinkingCode("123456", "2026-07-15T12:00:00Z")
            },
            selection,
        )
        composeRule.setContent {
            CaregiverLargeText(darkTheme) {
                CaregiverHomeScreen(repository, tutorialEnabled = false)
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-linking-code-issue"))
        composeRule.onNodeWithTag("caregiver-linking-code-issue").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.linkingCode != null }
        composeRule.onNodeWithTag("caregiver-linking-code-share").assertIsDisplayed()
        if (darkTheme) writeDeviceScreenshotFixture("android-ui-208-caregiver-linking-code-sheet-dark-font-2.0.png")

        composeRule.runOnIdle(repository::dismissLinkingCode)
        composeRule.waitUntil(5_000) { repository.state.value.linkingCode == null }
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-slot-times"))
        composeRule.onNodeWithTag("caregiver-slot-times").performClick()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet-content").performScrollToNode(hasTestTag("caregiver-slot-times-save"))
        composeRule.onNodeWithTag("caregiver-slot-times-save").assertIsDisplayed()
        if (darkTheme) writeDeviceScreenshotFixture("android-ui-208-caregiver-slot-times-sheet-dark-font-2.0.png")
    }

    private fun captureDarkFixture(name: String) {
        if (!darkTheme) return
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), name)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "darkTheme={0}")
        fun themes() = listOf(false, true)
    }
}

private class LargeTextSelectionStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}

@Composable
private fun CaregiverLargeText(darkTheme: Boolean, content: @Composable () -> Unit) {
    MedicationAppTheme(darkTheme = darkTheme) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
            content()
        }
    }
}
