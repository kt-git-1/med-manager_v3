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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme

private data class PatientTutorialCopy(val title: String, val message: String)

@Composable
internal fun PatientTutorialOverlay(step: Int, onSkip: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val steps = listOf(
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_today_title), stringResource(R.string.patient_tutorial_today_message)),
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_history_title), stringResource(R.string.patient_tutorial_history_message)),
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_notification_title), stringResource(R.string.patient_tutorial_notification_message)),
        PatientTutorialCopy(stringResource(R.string.patient_tutorial_permission_title), stringResource(R.string.patient_tutorial_permission_message)),
    )
    val copy = steps[step.coerceIn(steps.indices)]
    val contextPaneTitle = stringResource(R.string.patient_tutorial_pane, step + 1, steps.size)
    Box(
        Modifier.fillMaxSize().background(MedicationTheme.colors.tutorialScrim).padding(20.dp)
            .semantics { paneTitle = contextPaneTitle },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(copy.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(copy.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { index ->
                        Box(
                            Modifier.size(if (index == step) 20.dp else 7.dp, 7.dp)
                                .background(if (index == step) PatientTeal else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1}/${steps.size}", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.patient_tutorial_skip)) }
                    if (step > 0) OutlinedButton(onClick = onPrevious) { Text(stringResource(R.string.common_back)) }
                    Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (step == steps.lastIndex) R.string.patient_tutorial_enable_notifications else R.string.common_next))
                    }
                }
            }
        }
    }
}
