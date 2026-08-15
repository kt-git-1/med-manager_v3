package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme

internal const val CAREGIVER_TUTORIAL_STEP_COUNT = 10

private data class CaregiverTutorialCopy(val title: Int, val message: Int, val icon: ImageVector)

private val caregiverTutorialCopy = listOf(
    CaregiverTutorialCopy(R.string.caregiver_tutorial_today_title, R.string.caregiver_tutorial_today_message, Icons.Rounded.Home),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_medications_title, R.string.caregiver_tutorial_medications_message, Icons.Rounded.Medication),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_inventory_title, R.string.caregiver_tutorial_inventory_message, Icons.Rounded.Inventory2),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_history_title, R.string.caregiver_tutorial_history_message, Icons.Rounded.History),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_settings_title, R.string.caregiver_tutorial_settings_message, Icons.Rounded.Settings),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_time_title, R.string.caregiver_tutorial_time_message, Icons.Rounded.AccessTime),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_register_title, R.string.caregiver_tutorial_register_message, Icons.Rounded.PersonAdd),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_issue_title, R.string.caregiver_tutorial_issue_message, Icons.Rounded.Link),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_share_title, R.string.caregiver_tutorial_share_message, Icons.Rounded.Share),
    CaregiverTutorialCopy(R.string.caregiver_tutorial_notification_title, R.string.caregiver_tutorial_notification_message, Icons.Rounded.Notifications),
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
        Modifier.fillMaxSize().background(MedicationTheme.colors.tutorialScrim)
            .semantics { paneTitle = pane }
            .testTag("caregiver-tutorial"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp)
                .border(1.dp, MedicationTheme.colors.orange.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MedicationTheme.colors.elevatedBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MedicationTheme.colors.orange.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(copy.icon, contentDescription = null, tint = MedicationTheme.colors.orange, modifier = Modifier.size(21.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(copy.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(copy.message),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("caregiver-tutorial-skip")) {
                        Text(
                            stringResource(if (finalStep) R.string.caregiver_tutorial_later else R.string.patient_tutorial_skip),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (safeStep > 0) {
                        Surface(shape = RoundedCornerShape(50), color = MedicationTheme.colors.orange.copy(alpha = 0.10f)) {
                            IconButton(onClick = onPrevious, modifier = Modifier.size(42.dp).testTag("caregiver-tutorial-back")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = MedicationTheme.colors.orange)
                            }
                        }
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("caregiver-tutorial-next"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedicationTheme.colors.orange),
                    ) {
                        Icon(if (finalStep) Icons.Rounded.Notifications else Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(if (finalStep) R.string.caregiver_tutorial_enable else R.string.common_next),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
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
