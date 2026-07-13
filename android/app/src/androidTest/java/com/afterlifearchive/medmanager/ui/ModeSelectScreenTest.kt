package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModeSelectScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCanonicalJapaneseContent() {
        composeRule.setContent {
            MedicationAppTheme { ModeSelectScreen {} }
        }

        composeRule.onNodeWithText("どちらで\n使いますか？").assertIsDisplayed()
        composeRule.onNodeWithText("本人として使う").assertIsDisplayed()
        composeRule.onNodeWithText("今日のお薬を確認します").assertIsDisplayed()
        composeRule.onNodeWithText("家族として使う").assertIsDisplayed()
        composeRule.onNodeWithText("薬と在庫を管理します").assertIsDisplayed()
    }

    @Test
    fun patientCardSelectsPatientMode() {
        var selected: AppMode? = null
        composeRule.setContent {
            MedicationAppTheme { ModeSelectScreen { selected = it } }
        }

        composeRule.onNodeWithText("本人として使う").performClick()

        composeRule.runOnIdle { assertEquals(AppMode.PATIENT, selected) }
    }
}
