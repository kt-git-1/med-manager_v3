package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TodayContent(
    doses: List<PatientDose>,
    loading: Boolean,
    updatingKey: String?,
    error: String?,
    message: String?,
    maintenanceWarning: String?,
    medications: Map<String, PatientMedication>,
    nextSlot: MedicationSlot?,
    updatingSlot: MedicationSlot?,
    prnMedications: List<PatientMedication>,
    updatingPrnMedicationId: String?,
    onRetry: () -> Unit,
    onRecord: (PatientDose) -> Unit,
    onDetail: (PatientDose) -> Unit,
    onRecordSlot: (MedicationSlot) -> Unit,
    onRecordPrn: (PatientMedication) -> Unit,
    onRemind: (PatientDose) -> Unit,
    now: Instant = Instant.now(),
) {
    val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    val date = today.format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_today_date_pattern), Locale.JAPANESE))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.patient_today_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        if (loading && doses.isEmpty()) item { PatientCenteredProgress() }
        if (loading && doses.isNotEmpty()) item { PatientNoticeCard(stringResource(R.string.patient_today_refreshing), MaterialTheme.colorScheme.surfaceVariant, null) }
        error?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.errorContainer, onRetry) } }
        message?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.primaryContainer, null) } }
        maintenanceWarning?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.tertiaryContainer, null) } }
        if (prnMedications.isNotEmpty()) {
            item { Text(stringResource(R.string.patient_prn_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(prnMedications, key = PatientMedication::id) { medication ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(medication.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(medication.prnInstructions ?: medication.dosageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (medication.isInsufficientForDose) Text(stringResource(R.string.patient_inventory_insufficient), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onRecordPrn(medication) },
                            enabled = !loading && !medication.isInsufficientForDose && updatingPrnMedicationId == null,
                            modifier = Modifier.testTag("prn-record-${medication.id}"),
                        ) { Text(stringResource(if (updatingPrnMedicationId == medication.id) R.string.patient_recording else R.string.patient_taken_action)) }
                    }
                }
            }
        }
        if (!loading && error == null && doses.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✓", color = PatientTeal, style = MaterialTheme.typography.displaySmall)
                        Text(stringResource(R.string.patient_today_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.patient_today_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val grouped = doses.groupBy { it.slot ?: PatientSlotTimes.DEFAULT.resolve(it.scheduledAt) }
        MedicationSlot.entries.forEach { slot ->
            val slotDoses = grouped[slot].orEmpty()
            if (slotDoses.isEmpty()) return@forEach
            val remaining = slotDoses.filter { it.status != DoseStatus.TAKEN }
            val insufficient = remaining.count { medications[it.medicationId]?.isInsufficientForDose == true }
            val scheduledAt = slotDoses.minOf(PatientDose::scheduledAt)
            val isWithinRecordingWindow = now >= scheduledAt.minusSeconds(30 * 60) && now <= scheduledAt.plusSeconds(60 * 60)
            item {
                SlotHeader(
                    slot = slot,
                    isNext = slot == nextSlot,
                    recordableCount = if (isWithinRecordingWindow) remaining.size - insufficient else 0,
                    insufficientCount = insufficient,
                    isWithinRecordingWindow = isWithinRecordingWindow,
                    updating = updatingSlot == slot || loading,
                    onRecordSlot = onRecordSlot,
                )
            }
            items(slotDoses, key = PatientDose::key) { dose ->
                DoseCard(
                    dose,
                    updatingKey == dose.key,
                    loading,
                    medications[dose.medicationId]?.isInsufficientForDose == true,
                    onRecord,
                    onRemind,
                    onDetail,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DoseCard(
    dose: PatientDose,
    updating: Boolean,
    screenUpdating: Boolean,
    inventoryInsufficient: Boolean,
    onRecord: (PatientDose) -> Unit,
    onRemind: (PatientDose) -> Unit,
    onDetail: (PatientDose) -> Unit,
) {
    val taken = dose.status == DoseStatus.TAKEN
    Card(
        onClick = { onDetail(dose) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (taken) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(dose.medicationName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(dose.dosageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeText(dose), color = PatientTeal, fontWeight = FontWeight.SemiBold)
                }
                val statusText = when {
                    taken -> stringResource(R.string.patient_status_taken)
                    inventoryInsufficient -> stringResource(R.string.patient_inventory_insufficient)
                    dose.status == DoseStatus.MISSED -> stringResource(R.string.patient_status_missed)
                    else -> stringResource(R.string.patient_status_pending)
                }
                Text(statusText, color = if (taken) PatientTeal else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            if (!taken) {
                Spacer(Modifier.height(18.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !screenUpdating && !updating && !inventoryInsufficient,
                    onClick = { onRecord(dose) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PatientTeal),
                ) {
                    Text(
                        stringResource(
                            when {
                                inventoryInsufficient -> R.string.patient_inventory_check
                                updating -> R.string.patient_recording
                                else -> R.string.patient_taken_action
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !screenUpdating, onClick = { onRemind(dose) }) {
                    Text(stringResource(R.string.patient_remind_ten_minutes), color = PatientTeal)
                }
            }
        }
    }
}

@Composable
internal fun PatientDoseDetailContent(dose: PatientDose, medication: PatientMedication?) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dose.medicationName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(dose.dosageText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${dateTimeText(dose)} ・ ${patientDoseStatusText(dose.status)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { PatientDetailCard(stringResource(R.string.patient_detail_notes), medication?.notes?.trim().takeUnless { it.isNullOrEmpty() } ?: stringResource(R.string.patient_detail_no_notes)) }
        item { PatientDetailCard(stringResource(R.string.patient_detail_dose_amount), stringResource(R.string.patient_tablet_amount, formatPatientAmount(dose.doseCount))) }
        if (medication != null) {
            item { PatientDetailCard(stringResource(R.string.patient_detail_strength), "${formatPatientAmount(medication.dosageStrengthValue)} ${medication.dosageStrengthUnit}") }
            if (medication.inventoryEnabled) {
                item { PatientDetailCard(stringResource(R.string.patient_detail_inventory), "${formatPatientAmount(medication.inventoryQuantity)} ${medication.inventoryUnit ?: ""}".trim()) }
            }
        }
    }
}

@Composable
private fun dateTimeText(dose: PatientDose): String = dose.scheduledAt.atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_detail_date_pattern), Locale.JAPANESE))

@Composable
private fun SlotHeader(
    slot: MedicationSlot,
    isNext: Boolean,
    recordableCount: Int,
    insufficientCount: Int,
    isWithinRecordingWindow: Boolean,
    updating: Boolean,
    onRecordSlot: (MedicationSlot) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(patientSlotTitle(slot), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PatientTeal)
                Spacer(Modifier.weight(1f))
                if (isNext) Text(stringResource(R.string.patient_next_slot), color = PatientTeal, fontWeight = FontWeight.Bold)
            }
            if (insufficientCount > 0) {
                Text(stringResource(R.string.patient_slot_insufficient_count, insufficientCount), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
            if (!isWithinRecordingWindow && recordableCount == 0) {
                Text(stringResource(R.string.patient_slot_wait_for_window), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recordableCount > 0) {
                Button(
                    onClick = { onRecordSlot(slot) },
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (updating) stringResource(R.string.patient_slot_recording) else stringResource(R.string.patient_slot_record_count, recordableCount)) }
            }
        }
    }
}

private fun timeText(dose: PatientDose): String = dose.scheduledAt
    .atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern("H:mm"))
