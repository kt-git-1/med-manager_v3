package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

class CaregiverInventoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listShowsMetricsStatusAndFilters() {
        setContent(mutableListOf(item("low", "少ない薬", 2.0, low = true), item("out", "切れた薬", 0.0, out = true), item("off", "未設定薬", 0.0, enabled = false)))

        composeRule.onNodeWithText("在庫を管理").assertIsDisplayed()
        composeRule.onNodeWithText("要対応").assertIsDisplayed()
        composeRule.onNodeWithText("残り 2 錠").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-filter-out").performClick()
        composeRule.onNodeWithText("切れた薬").assertIsDisplayed()
        composeRule.onNodeWithText("少ない薬").assertDoesNotExist()
    }

    @Test
    fun detailRefillAndCorrectionRequireConfirmationAndUseAuthoritativeValues() {
        setContent(mutableListOf(item("low", "少ない薬", 2.0, low = true)))
        composeRule.onNodeWithTag("caregiver-inventory-item-low").performClick()

        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-refill-amount"))
        composeRule.onNodeWithTag("inventory-refill-amount").performTextInput("5")
        composeRule.onNodeWithTag("inventory-refill").performClick()
        composeRule.onNodeWithText("少ない薬を5錠補充し、残数を7錠にします。").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-refill-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("補充を記録しました").assertIsDisplayed()

        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasTestTag("inventory-correction-quantity"))
        composeRule.onNodeWithTag("inventory-correction-quantity").performTextReplacement("4")
        composeRule.onNodeWithTag("inventory-correction").performClick()
        composeRule.onNodeWithText("残数を4錠に変更します。").assertIsDisplayed()
        composeRule.onNodeWithTag("inventory-correction-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasText("残数を修正しました"))
        composeRule.onNodeWithText("残数を修正しました").assertIsDisplayed()
        composeRule.onNodeWithTag("caregiver-inventory-detail").performScrollToNode(hasText("残り 4 錠"))
        composeRule.onNodeWithText("残り 4 錠").assertIsDisplayed()
    }

    @Test
    fun unconfiguredMedicationCanEnableInventoryManagement() {
        setContent(mutableListOf(item("off", "未設定薬", 0.0, enabled = false)))
        composeRule.onNodeWithTag("caregiver-inventory-item-off").performClick()

        composeRule.onNodeWithTag("inventory-enabled").performClick()
        composeRule.onNodeWithTag("inventory-save-settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("在庫設定を保存しました").assertIsDisplayed()
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
}
