package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverCreateError
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.caregiver.CaregiverLinkingCode
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.push.CaregiverPushDataSource
import com.afterlifearchive.medmanager.data.push.CaregiverPushRepository
import com.afterlifearchive.medmanager.data.push.CaregiverPushStorage
import com.afterlifearchive.medmanager.data.push.CaregiverPushTokenSource
import com.afterlifearchive.medmanager.AnalyticsConsentState
import com.afterlifearchive.medmanager.AnalyticsConsentStore
import com.afterlifearchive.medmanager.AnalyticsService
import com.afterlifearchive.medmanager.AnalyticsTransport
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class CaregiverHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startsOnTodayAndExposesFiveTabsInCurrentIosOrder() {
        setContent(listOf(CaregiverPatient("patient-1", "さくら")))

        composeRule.onNodeWithTag("caregiver-content-today").assertIsDisplayed()
        listOf("today", "medications", "inventory", "history", "settings").forEach {
            composeRule.onNodeWithTag("caregiver-tab-$it").assertIsDisplayed()
        }
    }

    @Test
    fun settingsUsesAutoSelectedSolePatientAndSelectionPersistsAcrossTabs() {
        val (repository, storage) = setContent(listOf(CaregiverPatient("patient-1", "さくら")))

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithText("さくら").assertIsDisplayed()
        composeRule.onNodeWithText("選択中").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-slot-times"))
        composeRule.onNodeWithTag("caregiver-slot-times").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-slot-times").performClick()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet").performTouchInput { swipeDown() }
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-linking-code-issue"))
        composeRule.onNodeWithTag("caregiver-linking-code-issue").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-patient-revoke"))
        composeRule.onNodeWithTag("caregiver-patient-revoke").performClick()
        composeRule.onNodeWithText("既存の本人セッションは失効しますが、データは保持されます。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("キャンセル").performClick()
        composeRule.onNodeWithTag("caregiver-tab-today").performClick()
        composeRule.onNodeWithText("さくらさんを見守り中").assertIsDisplayed()

        assertEquals("patient-1", repository.state.value.selectedPatientId)
        assertEquals("patient-1", storage.currentPatientId)
    }

    @Test
    fun noPatientStateIsSharedByTodayAndSettings() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-feature-state").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-empty").assertIsDisplayed()
    }

    @Test
    fun failedPatientRefreshKeepsShellAndShowsGlobalStaleRetry() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        var fail = false
        val repository = CaregiverPatientRepository(
            CaregiverPatientDataSource {
                if (fail) error("offline") else listOf(CaregiverPatient("patient-1", "さくら"))
            },
            selection,
        )
        runBlocking { repository.refresh() }
        fail = true
        composeRule.setContent {
            MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) }
        }
        composeRule.waitUntil(5_000) { repository.state.value.refreshFailed }

        composeRule.onNodeWithTag("caregiver-patient-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tab-today").assertIsDisplayed()
        composeRule.onNodeWithText("さくらさんを見守り中").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-create-submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-logout"))
        composeRule.onNodeWithTag("caregiver-logout").assertIsEnabled()
    }

    @Test
    fun createFormRejectsNamesOverFiftyCharactersLocally() {
        val (repository, _) = setContent(emptyList())
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded && !repository.state.value.loading }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-create-submit").assertIsEnabled()
        composeRule.onNodeWithTag("caregiver-create-name").performTextReplacement("x".repeat(51))
        composeRule.onNodeWithTag("caregiver-create-name").assertTextContains("x".repeat(51))
        composeRule.onNodeWithTag("caregiver-settings-list")
            .performScrollToNode(hasTestTag("caregiver-create-submit"))
        composeRule.onNodeWithTag("caregiver-create-submit").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.createError == CaregiverCreateError.TOO_LONG }
        composeRule.onNodeWithTag("caregiver-create-error", useUnmergedTree = true).assertIsDisplayed()
            .assertTextContains("表示名は50文字以内で入力してください")
    }

    @Test
    fun settingsShowsMessageBearingInitialLoadingState() {
        val release = CompletableDeferred<Unit>()
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            CaregiverPatientDataSource {
                release.await()
                emptyList()
            },
            selection,
        )
        composeRule.setContent {
            MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) }
        }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.loading }
        composeRule.onNodeWithTag("caregiver-settings-loading").assertIsDisplayed()
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), "android-ui-208-caregiver-settings-loading-light.png")

        release.complete(Unit)
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }
    }

    @Test
    fun settingsEmptyStateExplainsThreeStepLinkingFlow() {
        setContent(emptyList())

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-empty").assertIsDisplayed()
        composeRule.onNodeWithText("見守る方の名前を登録").assertIsDisplayed()
        composeRule.onNodeWithText("連携コードを発行").assertIsDisplayed()
        composeRule.onNodeWithText("本人モードの端末でコードを入力").assertIsDisplayed()
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), "android-ui-208-caregiver-settings-empty-light.png")
    }

    @Test
    fun settingsInitialFailureMatchesCurrentIosRetryState() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { error("offline") }, selection)
        composeRule.setContent { MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) } }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.loadFailed }
        composeRule.onNodeWithTag("caregiver-settings-retry").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-settings-return-login").assertIsDisplayed()
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), "android-ui-208-caregiver-settings-error-light.png")
    }

    @Test
    fun issuedLinkingCodeUsesCurrentIosSheetHierarchy() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            object : CaregiverPatientDataSource {
                override suspend fun listPatients() = listOf(
                    CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")),
                )
                override suspend fun issueLinkingCode(patientId: String) =
                    CaregiverLinkingCode("123456", "2026-07-15T12:00:00Z")
            },
            selection,
        )
        composeRule.setContent { MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) } }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-linking-code-issue"))
        composeRule.onNodeWithTag("caregiver-linking-code-issue").performClick()
        composeRule.waitUntil(5_000) { repository.state.value.linkingCode != null }
        composeRule.onNodeWithTag("caregiver-linking-code-sheet").assertIsDisplayed()
        composeRule.onNodeWithText("患者にこのコードを伝えてください").assertIsDisplayed()
        composeRule.onNodeWithText("有効期限: 2026/07/15 21:00").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-linking-code-copy").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-linking-code-share").assertIsDisplayed()
        writeDeviceScreenshotFixture("android-ui-208-caregiver-linking-code-sheet-light.png")
    }

    @Test
    fun slotTimePresetUsesCurrentIosDetailSheet() {
        val (repository, _) = setContent(
            listOf(CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"))),
        )
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-slot-times"))
        composeRule.onNodeWithTag("caregiver-slot-times").performClick()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet-content").performScrollToNode(hasText("朝"))
        composeRule.onNodeWithText("朝").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet-content").performScrollToNode(hasText("眠前"))
        composeRule.onNodeWithText("眠前").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-slot-times-sheet-content").performScrollToNode(hasTestTag("caregiver-slot-times-save"))
        composeRule.onNodeWithTag("caregiver-slot-times-save").assertIsDisplayed()
        writeDeviceScreenshotFixture("android-ui-208-caregiver-slot-times-sheet-light.png")
    }

    @Test
    fun settingsBlocksTheScreenWhileCreatingPatient() {
        val release = CompletableDeferred<Unit>()
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            object : CaregiverPatientDataSource {
                override suspend fun listPatients() = emptyList<CaregiverPatient>()
                override suspend fun createPatient(displayName: String): CaregiverPatient {
                    release.await()
                    return CaregiverPatient("patient-created", displayName)
                }
            },
            selection,
        )
        composeRule.setContent {
            MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) }
        }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list")
            .performScrollToNode(hasTestTag("caregiver-create-name"))
        composeRule.onNodeWithTag("caregiver-create-name").performTextInput("さくら")
        composeRule.onNodeWithTag("caregiver-create-name").assertTextContains("さくら")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-settings-list")
            .performScrollToNode(hasTestTag("caregiver-create-submit"))
        composeRule.onNodeWithTag("caregiver-create-submit").assertIsEnabled().performClick()
        composeRule.waitUntil(5_000) { repository.state.value.creating }
        composeRule.onNodeWithTag("caregiver-settings-updating").assertIsDisplayed()
        composeRule.onNodeWithText("更新中...").assertIsDisplayed()

        release.complete(Unit)
        composeRule.waitUntil(5_000) { !repository.state.value.creating }
    }

    @Test
    fun screenshotFixtureShowsCaregiverSettingsHierarchy() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            CaregiverPatientDataSource {
                listOf(CaregiverPatient("patient-1", "さくら", CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")))
            },
            selection,
        )
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                CaregiverHomeScreen(repository, tutorialEnabled = false)
            }
        }
        composeRule.waitUntil(5_000) { repository.state.value.hasLoaded }
        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-header").assertIsDisplayed()

        captureDevice(activity, "android-ui-208-caregiver-settings-light.png")
    }

    @Test
    fun settingsExposesExplicitPushControlAndConfigurationState() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val patientRepository = CaregiverPatientRepository(CaregiverPatientDataSource { emptyList() }, selection)
        val pushRepository = CaregiverPushRepository(
            dataSource = object : CaregiverPushDataSource {
                override suspend fun register(token: String) = Unit
                override suspend fun unregister(token: String) = Unit
            },
            tokenSource = object : CaregiverPushTokenSource {
                override val configured = false
                override fun setAutoInitEnabled(enabled: Boolean) = Unit
                override suspend fun token() = "token"
            },
            storage = TestPushStorage(),
        )
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHomeScreen(patientRepository, pushRepository = pushRepository, tutorialEnabled = false)
            }
        }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-push-settings"))
        composeRule.onNodeWithTag("caregiver-push-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-push-switch").assertIsDisplayed()
    }

    @Test
    fun settingsIncludesLegalDestinationsAndConfirmsLogout() {
        setContent(emptyList())
        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()

        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-legal-support"))
        composeRule.onNodeWithTag("caregiver-privacy-link").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-terms-link").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-support-link").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-logout"))
        composeRule.onNodeWithTag("caregiver-logout").performClick()
        composeRule.onNodeWithText("ログアウトしますか？").assertIsDisplayed()
        composeRule.onNodeWithText("家族モードからログアウトします。").assertIsDisplayed()
        composeRule.onNodeWithText("キャンセル").performClick()
    }

    @Test
    fun caregiverSettingsUsesSharedAnalyticsConsentState() {
        val selectionStorage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(selectionStorage).also { it.restore() }
        val patientRepository = CaregiverPatientRepository(CaregiverPatientDataSource { emptyList() }, selection)
        val consent = CaregiverTestAnalyticsStore(AnalyticsConsentState(enabled = true, decided = true))
        val analytics = AnalyticsService(consent, CaregiverTestAnalyticsTransport()).also { it.configure() }
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHomeScreen(patientRepository, analyticsService = analytics, tutorialEnabled = false)
            }
        }

        composeRule.onNodeWithTag("caregiver-tab-settings").performClick()
        composeRule.onNodeWithTag("caregiver-settings-list").performScrollToNode(hasTestTag("caregiver-analytics-settings"))
        composeRule.onNodeWithTag("caregiver-analytics-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-analytics-toggle").performClick()
        composeRule.runOnIdle { assertEquals(AnalyticsConsentState(enabled = false, decided = true), analytics.state.value) }
    }

    @Test
    fun remotePushSelectsTargetPatientAndHistoryTab() {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val patientRepository = CaregiverPatientRepository(
            CaregiverPatientDataSource { listOf(CaregiverPatient("p1", "さくら"), CaregiverPatient("p2", "ゆうき")) },
            selection,
        )
        val historyRepository = CaregiverHistoryRepository(object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = emptyList<HistoryDay>()
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }, MutationFreshnessStore())
        historyRepository.handleNotificationTarget("DOSE_TAKEN", "p2", "2026-07-15", "noon")

        composeRule.setContent {
            MedicationAppTheme {
                CaregiverHomeScreen(patientRepository, historyRepository = historyRepository, tutorialEnabled = false)
            }
        }
        composeRule.waitUntil(5_000) { patientRepository.state.value.selectedPatientId == "p2" }

        composeRule.onNodeWithTag("caregiver-content-history").assertIsDisplayed()
        assertEquals("p2", storage.currentPatientId)
    }

    private fun setContent(patients: List<CaregiverPatient>): Pair<CaregiverPatientRepository, TestSelectionStorage> {
        val storage = TestSelectionStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(CaregiverPatientDataSource { patients }, selection)
        composeRule.setContent { MedicationAppTheme { CaregiverHomeScreen(repository, tutorialEnabled = false) } }
        composeRule.waitForIdle()
        return repository to storage
    }

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

private class TestPushStorage : CaregiverPushStorage {
    override var enabled = false
    override var token: String? = null
    override var registeredToken: String? = null
    override var pendingUnregisterToken: String? = null
}

private class CaregiverTestAnalyticsStore(initial: AnalyticsConsentState) : AnalyticsConsentStore {
    private var value = initial
    override fun state() = value
    override fun save(enabled: Boolean) { value = AnalyticsConsentState(enabled, decided = true) }
}

private class CaregiverTestAnalyticsTransport : AnalyticsTransport {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun reset() = Unit
    override fun log(name: String, parameters: Map<String, String>) = Unit
}

private class TestSelectionStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}
