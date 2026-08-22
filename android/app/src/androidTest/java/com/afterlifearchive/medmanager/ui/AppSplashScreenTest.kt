package com.afterlifearchive.medmanager.ui

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test

class AppSplashScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun splashUsesLargeUnmaskedIosLogo() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MedicationAppTheme {
                AppSplashScreen(onFinished = {})
            }
        }

        composeRule.onNodeWithTag("app-splash-logo")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(180.dp)
    }
}
