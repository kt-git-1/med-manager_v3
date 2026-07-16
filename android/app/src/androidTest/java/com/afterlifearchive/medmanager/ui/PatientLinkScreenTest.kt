package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import com.afterlifearchive.medmanager.data.session.PatientLinkFailure
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowCompat
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
    fun currentIosEmptyHierarchyMatchesInLightAppearance() {
        val activity = showLink(code = "")

        composeRule.onNodeWithText("連携コード").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("モードを選び直す").assertIsDisplayed()
        captureDevice(activity, "android-ui-002-patient-link-empty-light.png")
    }

    @Test
    fun currentIosFilledHierarchyMatchesInLightAppearanceWithoutSubmitting() {
        val activity = showLink(code = "")

        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).performTextInput("123456")
        dismissKeyboard()
        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).assertTextEquals("123456")
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsDisplayed().assertIsEnabled()
        captureDevice(activity, "android-ui-002-patient-link-filled-light.png")
    }

    @Test
    fun currentIosFilledHierarchyMatchesInDarkAppearanceWithoutSubmitting() {
        val activity = showLink(code = "", darkTheme = true)

        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).performTextInput("123456")
        dismissKeyboard()
        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).assertTextEquals("123456")
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsDisplayed().assertIsEnabled()
        captureDevice(activity, "android-ui-002-patient-link-filled-dark.png", darkTheme = true)
    }

    @Test
    fun filledActionsRemainReachableInDarkAppearanceAtTwoHundredPercentFontScale() {
        val activity = showLink(code = "", darkTheme = true, fontScale = 2f)

        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).performTextInput("123456")
        dismissKeyboard()
        captureDevice(activity, "android-ui-002-patient-link-filled-dark-font-2.0.png", darkTheme = true)
        composeRule.onNodeWithTag(LINK_CODE_INPUT_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("モードを選び直す").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithTag("patient-link-list")
            .performScrollToNode(hasTestTag("patient-link-back"))
        composeRule.onNodeWithTag("patient-link-back").assertIsDisplayed()
        captureFixture("android-ui-002-patient-link-network-error-font-2.0.png")
    }

    @Test
    fun loadingReplacesSubmitLabelAndPreventsDuplicateSubmission() {
        composeRule.setContent {
            MedicationAppTheme {
                PatientLinkContent("123456", true, null, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("送信").assertDoesNotExist()
    }

    @Test
    fun requiredVisualStatesHaveDeterministicFixtures() {
        var fixture by mutableStateOf(PatientLinkFixture.Valid)
        composeRule.setContent {
            MedicationAppTheme {
                PatientLinkContent(
                    code = "123456",
                    loading = fixture.loading,
                    errorMessage = fixture.errorMessage,
                    onCodeChange = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        PatientLinkFixture.entries.forEach { state ->
            composeRule.runOnIdle { fixture = state }
            composeRule.waitForIdle()
            state.errorMessage?.let {
                composeRule.onNodeWithText(it).assertIsDisplayed()
            }
            if (state.loading) {
                composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsNotEnabled()
                composeRule.onNodeWithText("送信").assertDoesNotExist()
            } else {
                composeRule.onNodeWithTag(LINK_CODE_SUBMIT_TAG).assertIsEnabled()
            }
            captureFixture(state.fixtureName)
        }
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

    private fun captureFixture(name: String) {
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), name)
    }

    private fun showLink(
        code: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
    ): Activity {
        lateinit var activity: Activity
        var currentCode by mutableStateOf(code)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    PatientLinkContent(
                        code = currentCode,
                        loading = false,
                        errorMessage = null,
                        onCodeChange = { currentCode = it.filter(Char::isDigit).take(6) },
                        onSubmit = {},
                        onBack = {},
                    )
                }
            }
        }
        return activity
    }

    private fun dismissKeyboard() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        SystemClock.sleep(250)
    }

    @Suppress("DEPRECATION")
    private fun captureDevice(activity: Activity, filename: String, darkTheme: Boolean = false) {
        composeRule.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
        SystemClock.sleep(250)
        writeDeviceScreenshotFixture(filename)
    }
}

private enum class PatientLinkFixture(
    val fixtureName: String,
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    Valid("android-ui-002-patient-link-valid-light.png"),
    Loading("android-ui-002-patient-link-loading-light.png", loading = true),
    Validation(
        "android-ui-002-patient-link-validation-light.png",
        errorMessage = "6桁の数字コードを入力してください",
    ),
    NotFound(
        "android-ui-002-patient-link-not-found-light.png",
        errorMessage = "コードが見つからないか期限切れです",
    ),
    Authorization(
        "android-ui-002-patient-link-authorization-light.png",
        errorMessage = "連携できませんでした。コードを確認して、もう一度お試しください",
    ),
    Network(
        "android-ui-002-patient-link-network-light.png",
        errorMessage = "通信に失敗しました。接続を確認して、もう一度お試しください",
    ),
    RateLimit(
        "android-ui-002-patient-link-rate-limit-light.png",
        errorMessage = "連携に失敗しました",
    ),
}
