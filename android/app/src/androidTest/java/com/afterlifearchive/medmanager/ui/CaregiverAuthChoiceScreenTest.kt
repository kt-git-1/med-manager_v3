package com.afterlifearchive.medmanager.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
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
    fun currentIosRuntimeFixtureMatchesHeaderAndChoiceHierarchy() {
        val activity = showChoice()

        composeRule.onNodeWithTag("caregiver-auth-header-icon").assertIsDisplayed()
        composeRule.onNodeWithText("家族アカウント").assertIsDisplayed()
        composeRule.onNodeWithText("ログイン").assertIsDisplayed()
        composeRule.onNodeWithText("新規登録").assertIsDisplayed()
        composeRule.onNodeWithText("モードを選び直す").assertIsDisplayed()
        captureDevice(activity, "android-ui-003-caregiver-auth-choice-matched-light.png")
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

    @Test
    fun darkThemePreservesRoleHierarchyAndActions() {
        val activity = showChoice(darkTheme = true)

        composeRule.onNodeWithText("家族アカウント").assertIsDisplayed()
        composeRule.onNodeWithText("ログイン").assertIsDisplayed()
        composeRule.onNodeWithText("新規登録").assertIsDisplayed()
        composeRule.onNodeWithText("モードを選び直す").assertIsDisplayed()
        captureFixture("android-ui-003-caregiver-auth-choice-dark.png")
        captureDevice(activity, "android-ui-003-caregiver-auth-choice-matched-dark.png", darkTheme = true)
    }

    @Test
    fun darkMaximumTextKeepsEveryChoiceAndModeResetReachable() {
        val activity = showChoice(darkTheme = true, fontScale = 2f)

        composeRule.onNodeWithText("ログイン").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("新規登録").performScrollTo().assertIsDisplayed()
        captureDevice(activity, "android-ui-003-caregiver-auth-choice-dark-font-2.0.png", darkTheme = true)
        composeRule.onNodeWithText("モードを選び直す").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun allActionsRemainReachableAtTwoHundredPercentFontScale() {
        var loginCount = 0
        var signupCount = 0
        var backCount = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MedicationAppTheme {
                    CaregiverAuthChoiceScreen(
                        onLogin = { loginCount += 1 },
                        onSignup = { signupCount += 1 },
                        onBack = { backCount += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithText("ログイン").performScrollTo().assertIsDisplayed()
        captureFixture("android-ui-003-caregiver-auth-choice-font-2.0.png")
        composeRule.onNodeWithText("ログイン").performClick()
        composeRule.onNodeWithText("新規登録").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("モードを選び直す").performScrollTo().assertIsDisplayed()
        captureFixture("android-ui-003-caregiver-auth-choice-font-2.0-scrolled.png")
        composeRule.onNodeWithText("モードを選び直す").performClick()

        composeRule.runOnIdle {
            assertEquals(1, loginCount)
            assertEquals(1, signupCount)
            assertEquals(1, backCount)
        }
    }

    private fun captureFixture(name: String) {
        writeScreenshotFixture(composeRule.onRoot().captureToImage(), name)
    }

    private fun showChoice(darkTheme: Boolean = false, fontScale: Float = 1f): Activity {
        lateinit var activity: Activity
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MedicationAppTheme(darkTheme = darkTheme) {
                    activity = checkNotNull(LocalActivity.current)
                    CaregiverAuthChoiceScreen({}, {}, {})
                }
            }
        }
        return activity
    }

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
