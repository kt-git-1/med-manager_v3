package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PatientLinkScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun canonicalContentAndDisabledStateAreVisible() {
        composeRule.setContent {
            MedicationAppTheme {
                PatientLinkContent("", false, null, {}, {}, {})
            }
        }

        composeRule.onNodeWithText("連携コード").assertIsDisplayed()
        composeRule.onNodeWithText("家族から受け取った6桁のコード\nを入力").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("モードを選び直す").assertIsDisplayed()
    }

    @Test
    fun inputIsSanitizedToSixDigitsAndEnablesSubmission() {
        var code by mutableStateOf("")
        var submitCount = 0
        composeRule.setContent {
            MedicationAppTheme {
                PatientLinkContent(
                    code = code,
                    loading = false,
                    errorMessage = null,
                    onCodeChange = { code = it.filter(Char::isDigit).take(6) },
                    onSubmit = { submitCount += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).performTextInput("12a34567")
        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).assertTextEquals("123456")
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, submitCount) }
    }

    @Test
    fun errorMessageUsesInlinePresentation() {
        composeRule.setContent {
            MedicationAppTheme {
                PatientLinkContent("123456", false, "コードが見つからないか期限切れです", {}, {}, {})
            }
        }

        composeRule.onNodeWithText("コードが見つからないか期限切れです").assertIsDisplayed()
    }
}
