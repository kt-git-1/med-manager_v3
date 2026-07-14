package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme

internal const val CAREGIVER_TUTORIAL_STEP_COUNT = 10

private data class CaregiverTutorialCopy(val title: Int, val message: Int)

private val caregiverTutorialCopy = listOf(
    CaregiverTutorialCopy(R.string.caregiver_tutorial_today_title, R.string.caregiver_tutorial_today_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_medications_title, R.string.caregiver_tutorial_medications_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_inventory_title, R.string.caregiver_tutorial_inventory_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_history_title, R.string.caregiver_tutorial_history_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_settings_title, R.string.caregiver_tutorial_settings_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_time_title, R.string.caregiver_tutorial_time_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_register_title, R.string.caregiver_tutorial_register_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_issue_title, R.string.caregiver_tutorial_issue_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_share_title, R.string.caregiver_tutorial_share_message),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_notification_title, R.string.caregiver_tutorial_notification_message),
)

@Composable
internal fun CaregiverTutorialOverlay(
    step: Int,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val safeStep = step.coerceIn(caregiverTutorialCopy.indices)
    val copy = caregiverTutorialCopy[safeStep]
    val finalStep = safeStep == caregiverTutorialCopy.lastIndex
    val pane = stringResource(R.string.caregiver_tutorial_pane, safeStep + 1, caregiverTutorialCopy.size)
    Box(
        Modifier.fillMaxSize().background(MedicationTheme.colors.tutorialScrim).padding(20.dp)
            .semantics { paneTitle = pane }
            .testTag("caregiver-tutorial"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(copy.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(copy.message), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    caregiverTutorialCopy.indices.forEach { index ->
                        Box(
                            Modifier.size(if (index == safeStep) 18.dp else 6.dp, 6.dp)
                                .background(if (index == safeStep) MedicationTheme.colors.orange else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${safeStep + 1}/${caregiverTutorialCopy.size}", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f).testTag("caregiver-tutorial-skip")) {
                        Text(stringResource(if (finalStep) R.string.caregiver_tutorial_later else R.string.patient_tutorial_skip))
                    }
                    if (safeStep > 0) {
                        OutlinedButton(onClick = onPrevious, modifier = Modifier.testTag("caregiver-tutorial-back")) {
                            Text(stringResource(R.string.common_back))
                        }
                    }
                    Button(onClick = onNext, modifier = Modifier.weight(1f).testTag("caregiver-tutorial-next")) {
                        Text(stringResource(if (finalStep) R.string.caregiver_tutorial_enable else R.string.common_next))
                    }
                }
            }
        }
    }
}

internal fun caregiverTutorialTab(step: Int): CaregiverTab = when (step.coerceIn(0, CAREGIVER_TUTORIAL_STEP_COUNT - 1)) {
    0, 9 -> CaregiverTab.TODAY
    1 -> CaregiverTab.MEDICATIONS
    2 -> CaregiverTab.INVENTORY
    3 -> CaregiverTab.HISTORY
    else -> CaregiverTab.SETTINGS
}

internal fun caregiverTutorialFocusTag(step: Int): String? = when (step) {
    5 -> "caregiver-slot-times"
    6 -> "caregiver-create-name"
    7, 8 -> "caregiver-linking-code"
    else -> null
}
