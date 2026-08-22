package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MedicationPullToRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downwardPullAtTopInvokesRefreshOnce() {
        var refreshCalls = 0
        composeRule.setContent {
            MedicationAppTheme {
                MedicationPullToRefresh(
                    isRefreshing = false,
                    enabled = true,
                    onRefresh = { refreshCalls += 1 },
                    testTag = "pull-refresh-test",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(20) { Text("item $it") }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("pull-refresh-test").performTouchInput { swipeDown() }

        composeRule.runOnIdle { assertEquals(1, refreshCalls) }
    }
}
