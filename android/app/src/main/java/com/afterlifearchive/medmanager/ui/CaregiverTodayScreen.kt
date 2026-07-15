package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayMutationError
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayMutationMessage
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CaregiverTodayScreen(
    repository: CaregiverTodayRepository,
    patientState: CaregiverPatientState,
    enabled: Boolean,
    onOpenMedications: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val freshness by repository.freshness.collectAsStateWithLifecycle()
    val selected = patientState.selectedPatient
    val cursor = remember(repository) { repository.newFreshnessCursor() }
    val scope = rememberCoroutineScope()
    var slotToConfirm by remember { mutableStateOf<Pair<MedicationSlot, List<PatientDose>>?>(null) }
    var showingPrnPicker by remember { mutableStateOf(false) }
    var prnToConfirm by remember { mutableStateOf<PatientMedication?>(null) }

    LaunchedEffect(enabled, selected?.id, freshness.dose, freshness.medication, freshness.inventory, freshness.slotTimes) {
        if (enabled && selected != null) cursor.refreshIfStale { repository.load(selected.id) }
        if (selected == null) repository.clear()
    }

    when {
        patientState.loading && patientState.patients.isEmpty() -> CaregiverTodayLoadingState()
        patientState.loadFailed -> CaregiverTodayMessage(
            stringResource(R.string.caregiver_data_unavailable_title),
            stringResource(R.string.caregiver_data_unavailable_message),
        )
        patientState.patients.isEmpty() -> CaregiverTodayMessage(
            stringResource(R.string.caregiver_no_patient_title),
            stringResource(R.string.caregiver_no_patient_message),
        )
        selected == null -> CaregiverTodayMessage(
            stringResource(R.string.caregiver_no_selection_title),
            stringResource(R.string.caregiver_no_selection_message),
        )
        state.loading -> CaregiverTodayLoadingState()
        state.loadFailed -> CaregiverTodayMessage(
            stringResource(R.string.caregiver_data_unavailable_title),
            stringResource(R.string.caregiver_data_unavailable_message),
        ) {
            Button(onClick = { scope.launch { repository.load(selected.id) } }, enabled = enabled) {
                Text(stringResource(R.string.common_retry))
            }
        }
        else -> Box(Modifier.fillMaxSize()) {
            CaregiverTodayContent(
                patientName = selected.displayName,
                slotTimes = selected.slotTimes,
                doses = state.doses,
                prnMedications = state.prnMedications,
                outOfStockMedicationIds = state.outOfStockMedicationIds,
                updatingDoseKey = state.updatingDoseKey,
                mutationError = state.mutationError,
                mutationMessage = state.mutationMessage,
                refreshFailed = state.refreshFailed,
                lastUpdatedCount = state.lastUpdatedCount,
                lastInsufficientCount = state.lastInsufficientCount,
                updatingSlot = state.updatingSlot,
                enabled = enabled && !state.refreshFailed,
                onRetry = { scope.launch { repository.load(selected.id) } },
                onRecordDose = { dose -> scope.launch { repository.recordDose(selected.id, dose) } },
                onDeleteDose = { dose -> scope.launch { repository.deleteDose(selected.id, dose) } },
                onRecordSlot = { slot, doses -> slotToConfirm = slot to doses },
                onOpenPrn = { showingPrnPicker = true },
                onOpenMedications = onOpenMedications,
            )
            if (state.refreshing || state.updatingDoseKey != null || state.updatingSlot != null) {
                CaregiverTodayUpdatingOverlay()
            }
        }
    }

    val confirmation = slotToConfirm
    if (confirmation != null && selected != null) {
        val recordable = confirmation.second.filter { it.status != DoseStatus.TAKEN }
        val includesMissed = recordable.any { it.status == DoseStatus.MISSED }
        AlertDialog(
            onDismissRequest = { slotToConfirm = null },
            title = { Text(stringResource(R.string.caregiver_today_confirm_slot_title)) },
            text = {
                Text(
                    stringResource(
                        if (includesMissed) R.string.caregiver_today_confirm_slot_missed_message else R.string.caregiver_today_confirm_slot_message,
                        slotLabel(confirmation.first),
                        recordable.size,
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { slotToConfirm = null }) { Text(stringResource(R.string.caregiver_medication_form_cancel)) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        slotToConfirm = null
                        scope.launch { repository.recordSlot(selected.id, confirmation.first, confirmation.second) }
                    },
                    modifier = Modifier.testTag("caregiver-today-slot-confirm"),
                ) { Text(stringResource(R.string.caregiver_today_confirm_record)) }
            },
            modifier = Modifier.testTag("caregiver-today-slot-dialog"),
        )
    }
    if (showingPrnPicker) {
        ModalBottomSheet(
            onDismissRequest = { if (state.updatingPrnMedicationId == null) showingPrnPicker = false },
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.9f).testTag("caregiver-today-prn-picker")) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.caregiver_today_prn_screen_title),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    item {
                        Text(stringResource(R.string.caregiver_today_prn_select), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.caregiver_today_prn_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                    state.mutationError?.let { error ->
                        item {
                            PatientNoticeCard(
                                stringResource(if (error == CaregiverTodayMutationError.INSUFFICIENT_INVENTORY) R.string.patient_inventory_insufficient else R.string.caregiver_today_mutation_failed),
                                MaterialTheme.colorScheme.errorContainer,
                                null,
                            )
                        }
                    }
                    items(state.prnMedications, key = PatientMedication::id) { medication ->
                        CaregiverPrnMedicationCard(
                            medication = medication,
                            insufficient = medication.id in state.outOfStockMedicationIds,
                            disabled = state.updatingPrnMedicationId != null,
                            onRecord = { prnToConfirm = medication },
                        )
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
                if (state.updatingPrnMedicationId != null) CaregiverTodayUpdatingOverlay()
            }
        }
    }
    val prnConfirmation = prnToConfirm
    if (prnConfirmation != null && selected != null) {
        AlertDialog(
            onDismissRequest = { prnToConfirm = null },
            title = { Text(stringResource(R.string.caregiver_today_prn_confirm_title)) },
            text = { Text(stringResource(R.string.caregiver_today_prn_confirm_message, selected.displayName, prnConfirmation.name)) },
            dismissButton = {
                TextButton(onClick = { prnToConfirm = null }) { Text(stringResource(R.string.caregiver_medication_form_cancel)) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prnToConfirm = null
                        scope.launch {
                            if (repository.recordPrn(selected.id, prnConfirmation)) showingPrnPicker = false
                        }
                    },
                    modifier = Modifier.testTag("caregiver-today-prn-confirm"),
                ) { Text(stringResource(R.string.caregiver_today_prn_confirm_action)) }
            },
            modifier = Modifier.testTag("caregiver-today-prn-confirm-dialog"),
        )
    }
}

@Composable
private fun CaregiverTodayContent(
    patientName: String,
    slotTimes: CaregiverSlotTimes?,
    doses: List<PatientDose>,
    prnMedications: List<PatientMedication>,
    outOfStockMedicationIds: Set<String>,
    updatingDoseKey: String?,
    mutationError: CaregiverTodayMutationError?,
    mutationMessage: CaregiverTodayMutationMessage?,
    refreshFailed: Boolean,
    lastUpdatedCount: Int,
    lastInsufficientCount: Int,
    updatingSlot: MedicationSlot?,
    enabled: Boolean,
    onRetry: () -> Unit,
    onRecordDose: (PatientDose) -> Unit,
    onDeleteDose: (PatientDose) -> Unit,
    onRecordSlot: (MedicationSlot, List<PatientDose>) -> Unit,
    onOpenPrn: () -> Unit,
    onOpenMedications: () -> Unit,
) {
    val prnCount = prnMedications.size
    val rows = MedicationSlot.entries.mapNotNull { slot ->
        doses.filter { resolveSlot(it, slotTimes) == slot }.takeIf { it.isNotEmpty() }?.let { slot to it }
    }
    val pending = doses.filter { it.status == DoseStatus.PENDING }.minByOrNull { it.scheduledAt }
    val missedRows = rows.count { (_, items) -> items.any { it.status == DoseStatus.MISSED } }
    val takenRows = rows.count { (_, items) -> items.all { it.status == DoseStatus.TAKEN } }
    val colors = MedicationTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-today-list"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(patientName.trim().take(1), style = MaterialTheme.typography.headlineSmall, color = colors.primaryTealText, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(stringResource(R.string.caregiver_today_watching, patientName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_today_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (refreshFailed) item {
            CaregiverStaleDataCard("caregiver-today-stale", onRetry)
        }
        mutationError?.let { error ->
            item {
                TodayCard(MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(if (error == CaregiverTodayMutationError.INSUFFICIENT_INVENTORY) R.string.patient_inventory_insufficient else R.string.caregiver_today_mutation_failed),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("caregiver-today-mutation-error"),
                    )
                }
            }
        }
        mutationMessage?.let { message ->
            item {
                Text(
                    caregiverMutationMessage(message, lastUpdatedCount, lastInsufficientCount),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("caregiver-today-mutation-message"),
                )
            }
        }
        if (doses.isEmpty() && prnCount == 0) {
            item { CaregiverTodayEmpty(onOpenMedications) }
        } else {
            if (missedRows > 0) item {
                TodayCard(colors.caregiverRed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        TodayIcon(Icons.Rounded.Warning, colors.caregiverRed)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.caregiver_today_missed_title), color = colors.caregiverRed, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_today_missed_message, missedRows), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (doses.isNotEmpty()) item {
                TodayCard(if (pending == null) MaterialTheme.colorScheme.primary else colors.orange) {
                    Text(stringResource(R.string.caregiver_today_next_action), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TodayIcon(if (pending == null) Icons.Rounded.CheckCircle else Icons.Rounded.AccessTime, if (pending == null) MaterialTheme.colorScheme.primary else colors.orange, 58)
                        Column {
                            Text(stringResource(if (pending == null) R.string.caregiver_today_all_done else R.string.caregiver_today_next_label), fontWeight = FontWeight.Bold)
                            Text(
                                pending?.let { "${slotLabel(resolveSlot(it, slotTimes))} ${TIME_FORMAT.format(it.scheduledAt.atZone(TOKYO))}" }
                                    ?: stringResource(R.string.caregiver_today_no_pending),
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.primaryTealText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    pending?.let {
                        Text(it.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (it.medicationId in outOfStockMedicationIds) {
                            Text(stringResource(R.string.patient_inventory_check), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (rows.isNotEmpty()) item {
                TodayCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { takenRows.toFloat() / rows.size.coerceAtLeast(1) }, modifier = Modifier.size(72.dp), strokeWidth = 8.dp)
                            Text("$takenRows/${rows.size}", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(stringResource(R.string.caregiver_today_progress_title), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_today_progress_format, takenRows, rows.size), style = MaterialTheme.typography.titleMedium, color = colors.primaryTealText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (prnCount > 0) item {
                TodayCard(colors.orange) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TodayIcon(Icons.Rounded.LocalHospital, colors.orange, 54)
                        Column {
                            Text(stringResource(R.string.caregiver_today_prn_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_today_prn_message, prnCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = onOpenPrn, modifier = Modifier.fillMaxWidth().testTag("caregiver-today-prn-open")) {
                        Text(stringResource(R.string.caregiver_today_prn_open))
                    }
                }
            }
            if (rows.isNotEmpty()) {
                item { Text(stringResource(R.string.caregiver_today_timeline_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                rows.forEach { (slot, items) ->
                    item(key = slot.name) {
                        CaregiverTimelineCard(
                            slot = slot,
                            doses = items,
                            slotTimes = slotTimes,
                            outOfStockMedicationIds = outOfStockMedicationIds,
                            updatingDoseKey = updatingDoseKey,
                            updatingSlot = updatingSlot,
                            enabled = enabled,
                            onRecordDose = onRecordDose,
                            onDeleteDose = onDeleteDose,
                            onRecordSlot = onRecordSlot,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CaregiverTodayEmpty(onOpenMedications: () -> Unit) {
    TodayCard(MaterialTheme.colorScheme.primary) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            TodayIcon(Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary, 58)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.caregiver_today_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.caregiver_today_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onOpenMedications, modifier = Modifier.fillMaxWidth().testTag("caregiver-today-open-medications")) {
            Icon(Icons.Rounded.Medication, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.caregiver_today_empty_action))
        }
    }
}

@Composable
private fun CaregiverTodayLoadingState() {
    CaregiverTodayCentered {
        CircularProgressIndicator(modifier = Modifier.size(52.dp), color = MedicationTheme.colors.primaryTealText)
        Text(
            stringResource(R.string.patient_today_loading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("caregiver-today-loading"),
        )
    }
}

@Composable
private fun CaregiverTodayUpdatingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("caregiver-today-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), color = MedicationTheme.colors.primaryTealText)
                Text(stringResource(R.string.patient_today_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CaregiverPrnMedicationCard(
    medication: PatientMedication,
    insufficient: Boolean,
    disabled: Boolean,
    onRecord: () -> Unit,
) {
    val orange = MedicationTheme.colors.orange
    val dosage = medication.dosageText.trim()
    val displayName = if (dosage.isEmpty() || dosage == "不明") medication.name else "${medication.name} $dosage"
    val note = medication.prnInstructions?.trim().takeUnless { it.isNullOrEmpty() }
        ?: medication.notes?.trim().takeUnless { it.isNullOrEmpty() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, orange.copy(alpha = 0.38f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TodayIcon(Icons.Rounded.LocalHospital, orange, 48)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(
                        stringResource(R.string.patient_prn_dose_count, formatTodayNumber(medication.doseCountPerIntake)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                    if (insufficient) {
                        Text(
                            stringResource(R.string.caregiver_today_prn_out_of_stock),
                            modifier = Modifier.background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Button(
                onClick = onRecord,
                enabled = !disabled && !insufficient,
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("caregiver-today-prn-${medication.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (insufficient) MaterialTheme.colorScheme.error else orange),
            ) {
                Icon(if (insufficient) Icons.Rounded.Warning else Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(if (insufficient) R.string.caregiver_today_prn_out_of_stock else R.string.caregiver_today_prn_record_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CaregiverTimelineCard(
    slot: MedicationSlot,
    doses: List<PatientDose>,
    slotTimes: CaregiverSlotTimes?,
    outOfStockMedicationIds: Set<String>,
    updatingDoseKey: String?,
    updatingSlot: MedicationSlot?,
    enabled: Boolean,
    onRecordDose: (PatientDose) -> Unit,
    onDeleteDose: (PatientDose) -> Unit,
    onRecordSlot: (MedicationSlot, List<PatientDose>) -> Unit,
) {
    val status = when {
        doses.all { it.status == DoseStatus.TAKEN } -> DoseStatus.TAKEN
        doses.any { it.status == DoseStatus.MISSED } -> DoseStatus.MISSED
        else -> DoseStatus.PENDING
    }
    val tint = when (status) {
        DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        DoseStatus.MISSED -> MedicationTheme.colors.caregiverRed
        DoseStatus.PENDING -> MedicationTheme.colors.orange
    }
    TodayCard(tint) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${slotLabel(slot)} ${configuredTime(slot, slotTimes)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(statusLabel(status), color = tint, fontWeight = FontWeight.Bold)
        }
        doses.forEach { dose ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dose.medicationName, fontWeight = FontWeight.Bold)
                    Text("${dose.dosageText}・1回${formatTodayNumber(dose.doseCount)}錠", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (dose.status == DoseStatus.TAKEN) {
                    Text(
                        stringResource(if (dose.recordedByType == RecordedByType.CAREGIVER) R.string.caregiver_today_recorded_by_caregiver else R.string.caregiver_today_recorded_by_patient),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (updatingDoseKey == dose.key) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                } else {
                    val canRecord = dose.medicationId !in outOfStockMedicationIds
                    IconButton(
                        onClick = { if (dose.status == DoseStatus.TAKEN) onDeleteDose(dose) else onRecordDose(dose) },
                        enabled = enabled && updatingSlot == null && (dose.status == DoseStatus.TAKEN || canRecord),
                        modifier = Modifier.testTag("caregiver-today-dose-action-${dose.key}"),
                    ) {
                        Icon(
                            if (dose.status == DoseStatus.TAKEN) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(
                                if (dose.status == DoseStatus.TAKEN) R.string.caregiver_today_delete_individual_accessibility
                                else R.string.caregiver_today_record_individual_accessibility,
                                dose.medicationName,
                            ),
                            tint = if (dose.status == DoseStatus.TAKEN) MaterialTheme.colorScheme.onSurfaceVariant else if (canRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        val recordable = doses.filter { it.status != DoseStatus.TAKEN }
        if (recordable.isNotEmpty()) {
            Button(
                onClick = { onRecordSlot(slot, doses) },
                enabled = enabled && updatingDoseKey == null && updatingSlot == null,
                modifier = Modifier.fillMaxWidth().testTag("caregiver-today-slot-action-${slot.name.lowercase()}"),
            ) {
                if (updatingSlot == slot) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.caregiver_today_record_slot, recordable.size))
            }
        }
    }
}

@Composable
private fun caregiverMutationMessage(message: CaregiverTodayMutationMessage, updated: Int, insufficient: Int): String = when (message) {
    CaregiverTodayMutationMessage.RECORDED -> stringResource(R.string.caregiver_today_recorded)
    CaregiverTodayMutationMessage.DELETED -> stringResource(R.string.caregiver_today_deleted)
    CaregiverTodayMutationMessage.SLOT_RECORDED -> stringResource(R.string.caregiver_today_slot_recorded, updated)
    CaregiverTodayMutationMessage.SLOT_PARTIAL -> stringResource(R.string.caregiver_today_slot_partial, updated, insufficient)
    CaregiverTodayMutationMessage.NOTHING_TO_RECORD -> stringResource(R.string.caregiver_today_nothing_to_record)
    CaregiverTodayMutationMessage.PRN_RECORDED -> stringResource(R.string.caregiver_today_prn_recorded)
}

@Composable
private fun TodayCard(accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.24f) ?: MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TodayIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun statusLabel(status: DoseStatus) = stringResource(when (status) {
    DoseStatus.TAKEN -> R.string.patient_status_taken
    DoseStatus.MISSED -> R.string.patient_status_missed
    DoseStatus.PENDING -> R.string.patient_status_pending
})

@Composable
private fun slotLabel(slot: MedicationSlot) = stringResource(when (slot) {
    MedicationSlot.MORNING -> R.string.caregiver_today_slot_morning
    MedicationSlot.NOON -> R.string.caregiver_today_slot_noon
    MedicationSlot.EVENING -> R.string.caregiver_today_slot_evening
    MedicationSlot.BEDTIME -> R.string.caregiver_today_slot_bedtime
})

private fun resolveSlot(dose: PatientDose, slotTimes: CaregiverSlotTimes?): MedicationSlot {
    val time = TIME_FORMAT.format(dose.scheduledAt.atZone(TOKYO))
    slotTimes?.let {
        mapOf(it.morning to MedicationSlot.MORNING, it.noon to MedicationSlot.NOON, it.evening to MedicationSlot.EVENING, it.bedtime to MedicationSlot.BEDTIME)[time]?.let { return it }
    }
    return when (dose.scheduledAt.atZone(TOKYO).hour) {
        in 4..10 -> MedicationSlot.MORNING
        in 11..15 -> MedicationSlot.NOON
        in 16..20 -> MedicationSlot.EVENING
        else -> MedicationSlot.BEDTIME
    }
}

private fun configuredTime(slot: MedicationSlot, times: CaregiverSlotTimes?) = when (slot) {
    MedicationSlot.MORNING -> times?.morning ?: "08:00"
    MedicationSlot.NOON -> times?.noon ?: "13:00"
    MedicationSlot.EVENING -> times?.evening ?: "19:00"
    MedicationSlot.BEDTIME -> times?.bedtime ?: "22:00"
}

@Composable
private fun CaregiverTodayMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    CaregiverTodayCentered {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
private fun CaregiverTodayCentered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
}

private val TOKYO = ZoneId.of("Asia/Tokyo")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private fun formatTodayNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
