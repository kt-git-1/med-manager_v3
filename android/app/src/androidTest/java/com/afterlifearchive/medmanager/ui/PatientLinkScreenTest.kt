package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import com.afterlifearchive.medmanager.data.session.PatientLinkFailure
import androidx.test.platform.app.InstrumentationRegistry
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
        composeRule.onNodeWithText("家族から受け取った6桁のコードを入力").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("連携コード").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("連携コード送信").assertIsDisplayed()
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

    @Test
    fun errorSubmitAndBackRemainReachableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MedicationAppTheme {
                    PatientLinkContent(
                        code = "123456",
                        loading = false,
                        errorMessage = "通信に失敗しました。接続を確認して、もう一度お試しください",
                        onCodeChange = {},
                        onSubmit = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("通信に失敗しました。接続を確認して、もう一度お試しください")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNodeWithText("モードを選び直す")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun linkFailureResourcesMatchPinnedIosCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf(
            PatientLinkFailure.INVALID to "6桁の数字コードを入力してください",
            PatientLinkFailure.NOT_FOUND to "コードが見つからないか期限切れです",
            PatientLinkFailure.AUTHORIZATION to "連携できませんでした。コードを確認して、もう一度お試しください",
            PatientLinkFailure.NETWORK to "通信に失敗しました。接続を確認して、もう一度お試しください",
            PatientLinkFailure.GENERIC to "連携に失敗しました",
        )

        expected.forEach { (failure, copy) ->
            assertEquals(copy, context.getString(failure.messageResource()))
        }
    }
}
