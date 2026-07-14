package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

    @Test
    fun familyActionRemainsReachableAtTwoHundredPercentFontScale() {
        var selected: AppMode? = null
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MedicationAppTheme { ModeSelectScreen { selected = it } }
            }
        }

        composeRule.onNodeWithText("家族として使う")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(AppMode.CAREGIVER, selected) }
    }
}
