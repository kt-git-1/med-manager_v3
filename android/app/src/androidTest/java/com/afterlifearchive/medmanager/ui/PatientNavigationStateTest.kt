package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class PatientNavigationStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveableStateRestoresNavigationButNotMutationConfirmations() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            val navigation = rememberPatientNavigationState()
            Column {
                Text(
                    listOf(
                        navigation.tab.name,
                        navigation.loadedTabs.map(PatientTab::name).sorted().joinToString(","),
                        navigation.selectedDoseKey.orEmpty(),
                    ).joinToString("|"),
                )
                Button(
                    modifier = Modifier.testTag("set-navigation"),
                    onClick = {
                        navigation.selectTab(PatientTab.HISTORY)
                        navigation.showDose("dose-42")
                    },
                ) { Text("set") }
            }
        }

        composeRule.onNodeWithTag("set-navigation").performClick()
        composeRule.onNodeWithText("HISTORY|HISTORY,TODAY|dose-42").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("HISTORY|HISTORY,TODAY|dose-42").assertIsDisplayed()
    }
}
