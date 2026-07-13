package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaregiverAuthChoiceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCanonicalChoicesAndGuidance() {
        composeRule.setContent {
            MedicationAppTheme { CaregiverAuthChoiceScreen({}, {}, {}) }
        }

        composeRule.onNodeWithText("家族アカウント").assertIsDisplayed()
        composeRule.onNodeWithText("ログイン").assertIsDisplayed()
        composeRule.onNodeWithText("既にアカウントをお持ちの方").assertIsDisplayed()
        composeRule.onNodeWithText("新規登録").assertIsDisplayed()
        composeRule.onNodeWithText("家族アカウントを作成する").assertIsDisplayed()
        composeRule.onNodeWithText("モードを選び直す").assertIsDisplayed()
    }

    @Test
    fun eachChoiceInvokesOnlyItsOwnAction() {
        var loginCount = 0
        var signupCount = 0
        composeRule.setContent {
            MedicationAppTheme {
                CaregiverAuthChoiceScreen(
                    onLogin = { loginCount += 1 },
                    onSignup = { signupCount += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("ログイン").performClick()
        composeRule.runOnIdle {
            assertEquals(1, loginCount)
            assertEquals(0, signupCount)
        }

        composeRule.onNodeWithText("新規登録").performClick()
        composeRule.runOnIdle {
            assertEquals(1, loginCount)
            assertEquals(1, signupCount)
        }
    }
}
