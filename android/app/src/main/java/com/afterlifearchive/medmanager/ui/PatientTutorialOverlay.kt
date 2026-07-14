package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme

private data class PatientTutorialCopy(val title: String, val message: String, val icon: ImageVector)

@Composable
internal fun PatientTutorialOverlay(step: Int, onSkip: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val steps = listOf(
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_today_title), stringResource(R.string.patient_tutorial_today_message), Icons.Rounded.CalendarMonth),
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_history_title), stringResource(R.string.patient_tutorial_history_message), Icons.Rounded.History),
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_notification_title), stringResource(R.string.patient_tutorial_notification_message), Icons.Rounded.Notifications),
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_permission_title), stringResource(R.string.patient_tutorial_permission_message), Icons.Rounded.Notifications),
    )
    val copy = steps[step.coerceIn(steps.indices)]
    val isFinalStep = step == steps.lastIndex
    val contextPaneTitle = stringResource(R.string.patient_tutorial_pane, step + 1, steps.size)
    Box(
        Modifier.fillMaxSize().background(MedicationTheme.colors.tutorialScrim)
            .semantics { paneTitle = contextPaneTitle },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 104.dp)
                .border(1.dp, PatientTeal.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MedicationTheme.colors.elevatedBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = PatientTeal.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(copy.icon, contentDescription = null, tint = PatientTeal, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(copy.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            copy.message,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { index ->
                        Box(
                            Modifier.size(width = if (index == step) 22.dp else 7.dp, height = 7.dp)
                                .background(if (index == step) PatientTeal else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1}/${steps.size}", fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Text(
                            stringResource(if (isFinalStep) R.string.patient_tutorial_notifications_later else R.string.patient_tutorial_skip),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (step > 0) {
                        Surface(shape = RoundedCornerShape(50), color = PatientTeal.copy(alpha = 0.10f)) {
                            IconButton(onClick = onPrevious, modifier = Modifier.size(50.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = PatientTeal)
                            }
                        }
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PatientTeal),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            if (isFinalStep) Icons.Rounded.Notifications else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(if (isFinalStep) R.string.patient_tutorial_enable_notifications else R.string.common_next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
