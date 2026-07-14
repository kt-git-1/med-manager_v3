package com.afterlifearchive.medmanager.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.afterlifearchive.medmanager.MainActivity
import org.junit.Rule
import org.junit.Test

class CaregiverConfigurationUiTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun productionCaregiverShellPreservesSelectedTabAcrossRecreationAndRotation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("PREVIEW_CAREGIVER", true)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.onNodeWithTag("caregiver-tab-inventory").performClick()
            assertInventorySelected()

            scenario.recreate()
            assertInventorySelected()

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
            assertInventorySelected()

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
            assertInventorySelected()
        }
    }

    private fun assertInventorySelected() {
        composeRule.onNodeWithTag("caregiver-tab-inventory").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("caregiver-content-inventory").assertIsDisplayed()
    }

    private fun waitForOrientation(scenario: ActivityScenario<MainActivity>, expected: Int) {
        composeRule.waitUntil(10_000) {
            var orientation = Configuration.ORIENTATION_UNDEFINED
            scenario.onActivity { orientation = it.resources.configuration.orientation }
            orientation == expected
        }
        composeRule.waitForIdle()
    }
}
