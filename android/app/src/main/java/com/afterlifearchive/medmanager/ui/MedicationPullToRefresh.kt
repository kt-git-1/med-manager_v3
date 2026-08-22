package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationPullToRefresh(
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (enabled && !isRefreshing) onRefresh() },
        modifier = modifier.testTag(testTag),
        content = content,
    )
}
