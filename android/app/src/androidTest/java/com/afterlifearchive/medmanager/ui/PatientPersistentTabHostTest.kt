package com.afterlifearchive.medmanager.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PatientPersistentTabHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabsAreLazyRetainedAndHiddenFromInteractionAndAccessibility() {
        lateinit var selected: MutableState<PatientTab>
        lateinit var loaded: MutableState<Set<PatientTab>>
        var todayCreated = 0
        var todayDisposed = 0
        var historyCreated = 0
        var historyDisposed = 0

        composeRule.setContent {
            MedicationAppTheme {
                selected = remember { mutableStateOf(PatientTab.TODAY) }
                loaded = remember { mutableStateOf(setOf(PatientTab.TODAY)) }
                PatientPersistentTabHost(selected.value, loaded.value) { tab ->
                    var clicks by remember { mutableIntStateOf(0) }
                    DisposableEffect(tab) {
                        if (tab == PatientTab.TODAY) todayCreated += 1 else historyCreated += 1
                        onDispose {
                            if (tab == PatientTab.TODAY) todayDisposed += 1 else historyDisposed += 1
                        }
                    }
                    Button(
                        onClick = { clicks += 1 },
                        modifier = Modifier.testTag("${tab.name.lowercase()}-action"),
                    ) {
                        Text("${tab.name.lowercase()}-$clicks")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("today-action").performClick()
        composeRule.onNodeWithText("today-1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("history-action").assertCountEquals(0)

        composeRule.runOnIdle {
            loaded.value = loaded.value + PatientTab.HISTORY
            selected.value = PatientTab.HISTORY
        }
        composeRule.onAllNodesWithTag("today-action").assertCountEquals(0)
        composeRule.onNodeWithTag("history-action").performClick()
        composeRule.onNodeWithText("history-1").assertIsDisplayed()

        composeRule.runOnIdle { selected.value = PatientTab.TODAY }
        composeRule.onAllNodesWithTag("history-action").assertCountEquals(0)
        composeRule.onNodeWithText("today-1").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(1, todayCreated)
            assertEquals(0, todayDisposed)
            assertEquals(1, historyCreated)
            assertEquals(0, historyDisposed)
        }
    }

    @Test
    fun visitedTabPreservesScrollPositionAfterSwitchingAwayAndBack() {
        lateinit var selected: MutableState<PatientTab>
        lateinit var loaded: MutableState<Set<PatientTab>>
        lateinit var todayListState: LazyListState
        lateinit var scope: CoroutineScope

        composeRule.setContent {
            MedicationAppTheme {
                selected = remember { mutableStateOf(PatientTab.TODAY) }
                loaded = remember { mutableStateOf(setOf(PatientTab.TODAY)) }
                scope = rememberCoroutineScope()
                PatientPersistentTabHost(selected.value, loaded.value) { tab ->
                    if (tab == PatientTab.TODAY) {
                        todayListState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyColumn(state = todayListState) {
                            items((0 until 50).toList()) { Text("row-$it", Modifier.height(80.dp)) }
                        }
                    } else {
                        Text("history")
                    }
                }
            }
        }

        composeRule.runOnIdle { scope.launch { todayListState.scrollToItem(30) } }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            loaded.value = loaded.value + PatientTab.HISTORY
            selected.value = PatientTab.HISTORY
        }
        composeRule.onNodeWithText("history").assertIsDisplayed()
        composeRule.runOnIdle { selected.value = PatientTab.TODAY }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(30, todayListState.firstVisibleItemIndex) }
    }
}
