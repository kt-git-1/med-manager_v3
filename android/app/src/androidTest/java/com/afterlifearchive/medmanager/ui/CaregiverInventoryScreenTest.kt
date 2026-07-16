package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import androidx.core.view.WindowCompat

class CaregiverInventoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listShowsMetricsStatusAndFilters() {
        setContent(mutableListOf(item("low", "少ない薬", 2.0, low = true), item("out", "切れた薬", 0.0, out = true), item("off", "未設定薬", 0.0, enabled = false)))

        composeRule.onNodeWithText("在庫を確認").assertIsDisplayed()
        composeRule.onNodeWithText("要確認").assertIsDisplayed()
        composeRule.onNodeWithText("まず補充が必要な薬があります").assertIsDisplayed()
        composeRule.onNodeWithText("対象の薬を詳しく見る").assertIsDisplayed()
        composeRule.onNodeWithText("在庫一覧").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasText("在庫未設定"))
        composeRule.onNodeWithText("在庫未設定").assertIsDisplayed()
        composeRule.onNodeWithText("残り 2 錠").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-filter-out"))
        composeRule.onNodeWithTag("caregiver-inventory-filter-out").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-filter-out").assertIsSelected()
        composeRule.onNodeWithText("切れた薬").assertIsDisplayed()
    }

    @Test
    fun initialLoadShowsIosLoadingMessage() {
        val gate = CompletableDeferred<Unit>()
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String): List<CaregiverInventoryItem> { gate.await(); return emptyList() }
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = error("unused")
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = error("unused")
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")
        val activity = setScreen(repository, CaregiverPatientState(listOf(patient), patient.id))

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-loading").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        captureDevice(activity, "android-ui-204-caregiver-inventory-loading-light-matched.png")
        gate.complete(Unit)
    }

    @Test
    fun initialFailureMatchesCurrentIosRecoveryAndLoginActions() {
        val repository = CaregiverInventoryRepository(failingSource(), MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")
        var returnedToLogin = false
        val activity = setScreen(
            repository,
            CaregiverPatientState(listOf(patient), patient.id),
            onReturnToLogin = { returnedToLogin = true },
        )

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-unavailable").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("情報を取得できませんでした").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-retry").assertIsDisplayed()
        captureDevice(activity, "android-ui-204-caregiver-inventory-error-light-matched.png")
        composeRule.onNodeWithTag("caregiver-inventory-return-login").performClick()
        assertTrue(returnedToLogin)
    }

    @Test
    fun patientListFailureRetriesThePatientRequest() {
        var retried = false
        setScreen(
            CaregiverInventoryRepository(emptySource(), MutationFreshnessStore()),
            CaregiverPatientState(hasLoaded = true, loadFailed = true),
            onRetryPatients = { retried = true },
        )

        composeRule.onNodeWithTag("caregiver-inventory-patients-unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-patients-retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun noPatientStateRoutesToRegistration() {
        var opened = false
        val activity = setScreen(
            CaregiverInventoryRepository(emptySource(), MutationFreshnessStore()),
            CaregiverPatientState(emptyList(), null),
            onCreatePatient = { opened = true },
        )

        composeRule.onNodeWithTag("caregiver-inventory-no-patient").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-create-patient").performClick()
        assertTrue(opened)
        captureDevice(activity, "android-ui-204-caregiver-inventory-no-patient-light-matched.png")
    }

    @Test
    fun selectionRequiredStateRoutesToPatientSettings() {
        var opened = false
        val activity = setScreen(
            CaregiverInventoryRepository(emptySource(), MutationFreshnessStore()),
            CaregiverPatientState(listOf(CaregiverPatient("p1", "さくら")), null),
            onOpenPatients = { opened = true },
        )

        composeRule.onNodeWithTag("caregiver-inventory-selection-required").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-open-patients").performClick()
        assertTrue(opened)
        captureDevice(activity, "android-ui-204-caregiver-inventory-selection-light-matched.png")
    }

    @Test
    fun weeklyRefillUsesBlockingUpdatingOverlay() {
        val original = item("low", "少ない薬", 2.0, low = true)
        val gate = CompletableDeferred<Unit>()
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = listOf(original)
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = original
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?): CaregiverInventoryItem {
                gate.await()
                return original.copy(inventoryQuantity = original.inventoryQuantity + (delta ?: 0.0), low = false)
            }
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")
        composeRule.setContent { MedicationAppTheme { CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {}) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-refill-low"))
        composeRule.onNodeWithTag("caregiver-inventory-refill-low").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-updating").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("更新中...").assertIsDisplayed()
        gate.complete(Unit)
    }

    @Test
    fun emptyInventoryShowsThreeStepOnboarding() {
        val activity = setContent(mutableListOf())

        composeRule.onNodeWithText("在庫管理はまだ空です").assertIsDisplayed()
        composeRule.onNodeWithText("薬タブで薬を登録").assertIsDisplayed()
        composeRule.onNodeWithText("薬の編集画面で在庫管理をオン").assertIsDisplayed()
        composeRule.onNodeWithText("残数が少ない薬を在庫画面で確認").assertIsDisplayed()
        captureDevice(activity, "android-ui-204-caregiver-inventory-empty-light-matched.png")
    }

    @Test
    fun screenshotFixtureShowsCaregiverInventoryList() {
        val initial = mutableListOf(
            item("low", "血圧の薬 5 mg", 4.0, low = true),
            item("ok", "胃の薬", 18.0),
            item("off", "整腸剤", 0.0, enabled = false),
            item("ended", "終了した薬", 6.0, periodEnded = true),
        )
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = initial.toList()
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = initial.first { it.medicationId == medicationId }
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = initial.first { it.medicationId == medicationId }
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "田中 花子")
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("田中 花子さん").assertIsDisplayed()
        captureDevice(activity, "android-ui-204-caregiver-inventory-populated-light-matched.png")

        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-filter-out"))
        composeRule.onNodeWithTag("caregiver-inventory-filter-out").performClick()
        composeRule.onNodeWithText("血圧の薬 5 mg").assertDoesNotExist()
        composeRule.onNodeWithText("胃の薬").assertDoesNotExist()
        captureDevice(activity, "android-ui-204-caregiver-inventory-filter-empty-light-matched.png")

        composeRule.onNodeWithTag("caregiver-inventory-filter-all").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasText("在庫未設定"))
        composeRule.onNodeWithText("終了した薬").assertIsDisplayed()
        composeRule.onNodeWithText("在庫未設定").assertIsDisplayed()
        captureDevice(activity, "android-ui-204-caregiver-inventory-lower-light-matched.png")
    }

    @Test
    fun emptyStateMatchesCurrentIosInDarkMode() {
        val emptyActivity = setContent(mutableListOf(), darkTheme = true)
        composeRule.onNodeWithTag("caregiver-inventory-open-medications").assertIsDisplayed()
        captureDevice(emptyActivity, "android-ui-204-caregiver-inventory-empty-dark-matched.png", darkTheme = true)
    }

    @Test
    fun populatedStateMatchesCurrentIosInDarkMode() {
        val populatedActivity = setContent(currentMatrixItems(), darkTheme = true)
        composeRule.onNodeWithTag("caregiver-inventory-list").assertIsDisplayed()
        captureDevice(populatedActivity, "android-ui-204-caregiver-inventory-populated-dark-matched.png", darkTheme = true)
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-item-low"))
        composeRule.onNodeWithTag("caregiver-inventory-item-low").assertIsDisplayed()
    }

    @Test
    fun emptyActionRemainsReachableAtTwoHundredPercentText() {
        val emptyActivity = setContent(mutableListOf(), fontScale = 2f)
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-open-medications"))
        composeRule.onNodeWithTag("caregiver-inventory-open-medications").assertIsDisplayed()
        captureDevice(emptyActivity, "android-ui-204-caregiver-inventory-empty-font-2.0-matched.png")
    }

    @Test
    fun populatedActionRemainsReachableAtTwoHundredPercentText() {
        val populatedActivity = setContent(currentMatrixItems(), fontScale = 2f)
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-refill-low"))
        composeRule.onNodeWithTag("caregiver-inventory-refill-low").assertIsDisplayed()
        captureDevice(populatedActivity, "android-ui-204-caregiver-inventory-populated-font-2.0-matched.png")
    }

    @Test
    fun screenshotFixtureShowsCaregiverInventoryDetail() {
        val initial = mutableListOf(item("low", "夕食後のお薬", 2.0, low = true))
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = initial.toList()
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = initial.first()
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = initial.first()
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")
        lateinit var activity: Activity
        composeRule.setContent {
            MedicationAppTheme {
                activity = checkNotNull(LocalActivity.current)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-item-low").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-detail").assertIsDisplayed()
        composeRule.onNodeWithText("在庫").assertIsDisplayed()
        composeRule.onNodeWithText("夕食後のお薬").assertIsDisplayed()
        captureDevice(activity, "android-ui-205-caregiver-inventory-detail-source-calibrated-light.png")
    }

    @Test
    fun detailRefillAndCorrectionRequireConfirmationAndUseAuthoritativeValues() {
        setContent(mutableListOf(item("low", "少ない薬", 2.0, low = true)))
        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()

        composeRule.onNodeWithText("1日1回（1錠ずつ）").assertIsDisplayed()
        composeRule.onNodeWithText("残り").assertIsDisplayed()
        composeRule.onNodeWithText("在庫設定").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-refill-amount"))
        composeRule.onNodeWithTag("inventory-refill-amount").performTextReplacement("5")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inventory-refill-amount").assertTextEquals("5")
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll").performScrollToNode(hasTestTag("inventory-refill"))
        composeRule.onNodeWithTag("inventory-refill").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-refill").performClick()
        composeRule.onNodeWithTag("inventory-refill-confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-refill-confirm").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("caregiver-inventory-detail").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithText("補充を記録しました").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-item-low"))
        composeRule.onNodeWithTag("caregiver-inventory-item-low").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("caregiver-inventory-detail").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll").performScrollToNode(hasTestTag("inventory-correction-quantity"))
        composeRule.onNodeWithTag("inventory-correction-quantity").performTextReplacement("4")
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll").performScrollToNode(hasTestTag("inventory-correction"))
        composeRule.onNodeWithTag("inventory-correction").performClick()
        composeRule.onNodeWithText("在庫を4個に変更しますか？（補充ではなく修正です）").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-correction-confirm").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("残数を修正しました").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasText("残り 4 錠"))
        composeRule.onNodeWithText("残り 4 錠").assertIsDisplayed()
    }

    @Test
    fun unconfiguredMedicationCanEnableInventoryManagement() {
        setContent(mutableListOf(item("off", "未設定薬", 0.0, enabled = false)))
        composeRule.onNodeWithTag("caregiver-inventory-item-off").performClick()

        composeRule.onNodeWithTag("inventory-enabled").performClick()
        composeRule.onNodeWithTag("inventory-save-settings").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-list").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("在庫設定を保存しました").assertIsDisplayed()
    }

    @Test
    fun failedCorrectionStaysOpenAndRetryClosesAfterSuccess() {
        val initial = mutableListOf(item("low", "少ない薬", 2.0, low = true))
        var fail = true
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = initial.toList()
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = initial.first()
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?): CaregiverInventoryItem {
                if (fail) { fail = false; error("offline") }
                val updated = initial.first().copy(inventoryQuantity = absoluteQuantity ?: 4.0, low = false)
                initial[0] = updated
                return updated
            }
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")
        composeRule.setContent { MedicationAppTheme { CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {}) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-item-low").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-item-low"))
        composeRule.onNodeWithTag("caregiver-inventory-item-low").assertIsDisplayed().performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("caregiver-inventory-detail").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll").performScrollToNode(hasTestTag("inventory-correction-quantity"))
        composeRule.onNodeWithTag("inventory-correction-quantity").performTextReplacement("4")
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll").performScrollToNode(hasTestTag("inventory-correction"))
        composeRule.onNodeWithTag("inventory-correction").performClick()
        composeRule.onNodeWithTag("inventory-correction-confirm").performClick()

        composeRule.waitUntil(5_000) { repository.state.value.mutationFailed }
        composeRule.onNodeWithTag("caregiver-inventory-detail-scroll").performScrollToNode(hasTestTag("inventory-retry"))
        composeRule.onNodeWithTag("caregiver-inventory-detail").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-retry").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun failedRefreshShowsStaleInventoryAndDisablesDetailAction() {
        val original = item("low", "少ない薬", 2.0, low = true)
        var fail = false
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = if (fail) error("offline") else listOf(original)
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = original
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = original
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        runBlocking { repository.load("p1") }
        fail = true
        runBlocking { repository.load("p1") }
        val patient = CaregiverPatient("p1", "さくら")
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }

        composeRule.onNodeWithTag("caregiver-inventory-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-list").performScrollToNode(hasTestTag("caregiver-inventory-item-low"))
        composeRule.onNodeWithText("少ない薬").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-item-low").assertIsNotEnabled()
    }

    private fun setContent(
        initial: MutableList<CaregiverInventoryItem>,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ): Activity {
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = initial.toList()
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?): CaregiverInventoryItem {
                val index = initial.indexOfFirst { it.medicationId == medicationId }
                initial[index] = initial[index].copy(inventoryEnabled = enabled)
                return initial[index]
            }
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?): CaregiverInventoryItem {
                val index = initial.indexOfFirst { it.medicationId == medicationId }
                val next = absoluteQuantity ?: initial[index].inventoryQuantity + (delta ?: 0.0)
                initial[index] = initial[index].copy(inventoryQuantity = next, low = next <= 3, out = next <= 0)
                return initial[index]
            }
        }
        val repository = CaregiverInventoryRepository(source, MutationFreshnessStore())
        val patient = CaregiverPatient("p1", "さくら")
        return setScreen(
            repository,
            CaregiverPatientState(listOf(patient), patient.id),
            darkTheme = darkTheme,
            fontScale = fontScale,
        )
    }

    private fun setScreen(
        repository: CaregiverInventoryRepository,
        patientState: CaregiverPatientState,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        onReturnToLogin: () -> Unit = {},
        onOpenPatients: () -> Unit = {},
        onCreatePatient: () -> Unit = {},
        onRetryPatients: () -> Unit = onOpenPatients,
    ): Activity {
        lateinit var activity: Activity
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                        CaregiverInventoryScreen(
                            repository = repository,
                            patientState = patientState,
                            enabled = true,
                            onOpenMedications = {},
                            onReturnToLogin = onReturnToLogin,
                            onOpenPatients = onOpenPatients,
                            onCreatePatient = onCreatePatient,
                            onRetryPatients = onRetryPatients,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return activity
    }

    private fun emptySource() = object : CaregiverInventoryDataSource {
        override suspend fun list(patientId: String) = emptyList<CaregiverInventoryItem>()
        override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = error("unused")
        override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = error("unused")
    }

    private fun failingSource() = object : CaregiverInventoryDataSource {
        override suspend fun list(patientId: String): List<CaregiverInventoryItem> = error("offline")
        override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = error("unused")
        override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = error("unused")
    }

    private fun currentMatrixItems() = mutableListOf(
        item("low", "血圧の薬 5 mg", 4.0, low = true),
        item("ok", "胃の薬", 18.0),
        item("off", "整腸剤", 0.0, enabled = false),
        item("ended", "終了した薬", 6.0, periodEnded = true),
    )

    private fun item(
        id: String,
        name: String,
        quantity: Double,
        enabled: Boolean = true,
        low: Boolean = false,
        out: Boolean = false,
        periodEnded: Boolean = false,
    ) = CaregiverInventoryItem(
        id, name, false, 1.0, enabled, quantity, 3, periodEnded, low, out, 1.0,
        7.0, 14.0, 21.0, quantity.toInt(), "2026-07-18",
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
