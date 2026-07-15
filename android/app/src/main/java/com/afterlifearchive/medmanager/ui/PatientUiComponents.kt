package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientUserMessage

internal val PatientTeal: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val PatientBackground: Color @Composable get() = MaterialTheme.colorScheme.background

@Composable
internal fun PatientDetailCard(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun patientDoseStatusText(status: DoseStatus) = stringResource(
    when (status) {
        DoseStatus.PENDING -> R.string.patient_status_pending
        DoseStatus.TAKEN -> R.string.patient_status_taken
        DoseStatus.MISSED -> R.string.patient_status_missed
    },
)

internal fun formatPatientAmount(value: Double) =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@Composable
internal fun patientSlotTitle(slot: MedicationSlot) = stringResource(
    when (slot) {
        MedicationSlot.MORNING -> R.string.patient_slot_morning
        MedicationSlot.NOON -> R.string.patient_slot_noon
        MedicationSlot.EVENING -> R.string.patient_slot_evening
        MedicationSlot.BEDTIME -> R.string.patient_slot_bedtime
    },
)

@Composable
internal fun patientUserMessageText(message: PatientUserMessage): String = when (message) {
    is PatientUserMessage.Raw -> message.value
    PatientUserMessage.InventoryInsufficient -> stringResource(R.string.patient_message_inventory_insufficient)
    PatientUserMessage.DoseRecorded -> stringResource(R.string.patient_message_dose_recorded)
    is PatientUserMessage.SlotPartial -> stringResource(R.string.patient_message_slot_partial, message.updatedCount, message.insufficientCount)
    is PatientUserMessage.SlotRecorded -> stringResource(R.string.patient_message_slot_recorded, message.updatedCount)
    PatientUserMessage.NoRecordableMedication -> stringResource(R.string.patient_message_no_recordable_medication)
    PatientUserMessage.PrnRecorded -> stringResource(R.string.patient_message_prn_recorded)
    PatientUserMessage.PrnRecordFailed -> stringResource(R.string.patient_prn_error)
    PatientUserMessage.TodayLoadFailed -> stringResource(R.string.patient_today_error)
    is PatientUserMessage.Validation -> message.safeMessage ?: stringResource(R.string.api_error_validation)
    PatientUserMessage.Unauthorized -> stringResource(R.string.api_error_unauthorized)
    PatientUserMessage.Forbidden -> stringResource(R.string.api_error_forbidden)
    PatientUserMessage.NotFound -> stringResource(R.string.api_error_not_found)
    is PatientUserMessage.Conflict -> message.safeMessage ?: stringResource(R.string.api_error_conflict)
    PatientUserMessage.InsufficientInventory -> stringResource(R.string.api_error_inventory)
    is PatientUserMessage.PatientLimit -> stringResource(R.string.api_error_patient_limit, message.limit)
    PatientUserMessage.RateLimited -> stringResource(R.string.api_error_rate_limited)
    PatientUserMessage.Network -> stringResource(R.string.session_error_network)
    PatientUserMessage.Server -> stringResource(R.string.api_error_server)
}

@Composable
internal fun PatientNoticeCard(text: String, color: Color, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().background(color, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(text)
        onRetry?.let { TextButton(onClick = it) { Text(stringResource(R.string.common_retry)) } }
    }
}

@Composable
internal fun PatientCenteredProgress() = Box(
    Modifier.fillMaxWidth().padding(32.dp),
    contentAlignment = Alignment.Center,
) {
    CircularProgressIndicator(color = PatientTeal)
}
