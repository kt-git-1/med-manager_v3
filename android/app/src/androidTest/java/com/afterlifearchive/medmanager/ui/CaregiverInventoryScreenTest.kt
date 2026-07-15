package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
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
        composeRule.setContent { MedicationAppTheme { CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {}) } }

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-loading").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("読み込み中...").assertIsDisplayed()
        gate.complete(Unit)
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
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-refill-low").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithTag("caregiver-inventory-refill-low").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-updating").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("更新中...").assertIsDisplayed()
        gate.complete(Unit)
    }

    @Test
    fun emptyInventoryShowsThreeStepOnboarding() {
        setContent(mutableListOf())

        composeRule.onNodeWithText("在庫管理はまだ空です").assertIsDisplayed()
        composeRule.onNodeWithText("薬タブで薬を登録").assertIsDisplayed()
        composeRule.onNodeWithText("薬の編集画面で在庫管理をオン").assertIsDisplayed()
        composeRule.onNodeWithText("残数が少ない薬を在庫画面で確認").assertIsDisplayed()
    }

    @Test
    fun screenshotFixtureShowsCaregiverInventoryList() {
        val initial = mutableListOf(
            item("low", "血圧の薬 5 mg", 4.0, low = true),
            item("ok", "胃の薬", 18.0),
            item("off", "整腸剤", 0.0, enabled = false),
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
        captureDevice(activity, "android-ui-204-caregiver-inventory-source-calibrated-light.png")
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
        captureDevice(activity, "android-ui-205-caregiver-inventory-detail-light.png")
    }

    @Test
    fun detailRefillAndCorrectionRequireConfirmationAndUseAuthoritativeValues() {
        setContent(mutableListOf(item("low", "少ない薬", 2.0, low = true)))
        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()

        composeRule.onNodeWithText("1日1回（1錠ずつ）").assertIsDisplayed()
        composeRule.onNodeWithText("残り").assertIsDisplayed()
        composeRule.onNodeWithText("在庫設定").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-refill-amount"))
        composeRule.onNodeWithTag("inventory-refill-amount").performTextInput("5")
        composeRule.onNodeWithTag("inventory-refill").performClick()
        composeRule.onNodeWithText("少ない薬 を +5個（2→7）").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-refill-confirm").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("caregiver-inventory-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("補充を記録しました").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-correction-quantity"))
        composeRule.onNodeWithTag("inventory-correction-quantity").performTextReplacement("4")
        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-correction"))
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
        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()
        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-correction-quantity"))
        composeRule.onNodeWithTag("inventory-correction-quantity").performTextReplacement("4")
        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-correction"))
        composeRule.onNodeWithTag("inventory-correction").performClick()
        composeRule.onNodeWithTag("inventory-correction-confirm").performClick()

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("inventory-retry").fetchSemanticsNodes().isNotEmpty() }
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
        composeRule.onNodeWithText("少ない薬").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-item-low").assertIsNotEnabled()
    }

    private fun setContent(initial: MutableList<CaregiverInventoryItem>) {
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
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverInventoryScreen(repository, CaregiverPatientState(listOf(patient), patient.id), true, {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun item(
        id: String,
        name: String,
        quantity: Double,
        enabled: Boolean = true,
        low: Boolean = false,
        out: Boolean = false,
    ) = CaregiverInventoryItem(
        id, name, false, 1.0, enabled, quantity, 3, false, low, out, 1.0,
        7.0, 14.0, 21.0, quantity.toInt(), "2026-07-18",
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
