package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    prnError: String? = null,
    prnSuccessRevision: Long = 0,
    onClearPrnFeedback: () -> Unit = {},
    refreshing: Boolean = false,
    now: Instant = Instant.now(),
) {
    if (loading && doses.isEmpty()) {
        PatientTodayInitialLoading()
        return
    }
    if (!loading && error != null && doses.isEmpty() && prnMedications.isEmpty()) {
        PatientTodayInitialError(error)
        return
    }

    val today = now.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
    val date = today.format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_today_date_pattern), Locale.JAPANESE))
    val grouped = doses.groupBy { it.slot ?: PatientSlotTimes.DEFAULT.resolve(it.scheduledAt) }
    val nextDoses = nextSlot?.let { grouped[it] }.orEmpty()
    val takenCount = doses.count { it.status == DoseStatus.TAKEN }
    val screenUpdating = refreshing || updatingKey != null || updatingSlot != null
    var showPrnSheet by rememberSaveable { mutableStateOf(false) }
    var observedPrnSuccessRevision by rememberSaveable { mutableStateOf(prnSuccessRevision) }

    LaunchedEffect(prnSuccessRevision) {
        if (prnSuccessRevision > observedPrnSuccessRevision && showPrnSheet) {
            showPrnSheet = false
            onClearPrnFeedback()
        }
        observedPrnSuccessRevision = prnSuccessRevision
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PatientTodayHeader(date) }
            error?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.errorContainer, onRetry) } }
            message?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.primaryContainer, null) } }
            maintenanceWarning?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.tertiaryContainer, null) } }

            item {
                NextDoseHeroCard(
                    slot = nextSlot,
                    doses = nextDoses,
                    medications = medications,
                    loading = screenUpdating,
                    updating = nextSlot != null && updatingSlot == nextSlot,
                    now = now,
                    onRecordSlot = onRecordSlot,
                    onDetail = onDetail,
                )
            }

            if (prnMedications.isNotEmpty()) {
                item {
                    PrnEntryCard(prnMedications.size) {
                        onClearPrnFeedback()
                        showPrnSheet = true
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("patient-today-planned"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.patient_today_planned_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.patient_today_progress, takenCount, doses.size),
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
                        color = PatientTeal,
                        fontWeight = FontWeight.Bold,
                    )
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
                        updating = updatingSlot == slot || screenUpdating,
                        onRecordSlot = onRecordSlot,
                    )
                }
                items(slotDoses, key = PatientDose::key) { dose ->
                    DoseCard(
                        dose,
                        updatingKey == dose.key,
                        screenUpdating,
                        medications[dose.medicationId]?.isInsufficientForDose == true,
                        onRecord,
                        onRemind,
                        onDetail,
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (screenUpdating && !showPrnSheet) PatientTodayUpdatingOverlay()

        if (showPrnSheet) {
            ModalBottomSheet(onDismissRequest = {
                showPrnSheet = false
                onClearPrnFeedback()
            }) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.85f).testTag("patient-prn-sheet")) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.patient_prn_sheet_title),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(stringResource(R.string.patient_prn_list_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        prnError?.let { PatientNoticeCard(it, MaterialTheme.colorScheme.errorContainer, null) }
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(prnMedications, key = PatientMedication::id) { medication ->
                                PrnMedicationCard(
                                    medication = medication,
                                    disabled = screenUpdating || updatingPrnMedicationId != null,
                                    onRecordPrn = onRecordPrn,
                                )
                            }
                        }
                    }
                    if (updatingPrnMedicationId != null) PatientPrnUpdatingOverlay()
                }
            }
        }
    }
}

@Composable
private fun PatientTodayInitialLoading() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("patient-today-initial-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(52.dp), color = PatientTeal)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.patient_today_loading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PatientTodayInitialError(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp).testTag("patient-today-initial-error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PatientTodayUpdatingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("patient-today-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), color = PatientTeal)
                Text(stringResource(R.string.patient_today_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PatientTodayHeader(date: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = PatientTeal, modifier = Modifier.size(32.dp))
        }
        Column {
            Text(stringResource(R.string.patient_today_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NextDoseHeroCard(
    slot: MedicationSlot?,
    doses: List<PatientDose>,
    medications: Map<String, PatientMedication>,
    loading: Boolean,
    updating: Boolean,
    now: Instant,
    onRecordSlot: (MedicationSlot) -> Unit,
    onDetail: (PatientDose) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-next"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(2.dp, PatientTeal.copy(alpha = 0.55f)),
    ) {
        if (slot == null || doses.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PatientTeal, modifier = Modifier.size(46.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.patient_today_next_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.patient_today_next_done_message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
            return@Card
        }

        val remaining = doses.filter { it.status != DoseStatus.TAKEN }
        val insufficient = remaining.count { medications[it.medicationId]?.isInsufficientForDose == true }
        val scheduledAt = doses.minOf(PatientDose::scheduledAt)
        val withinWindow = now >= scheduledAt.minusSeconds(30 * 60) && now <= scheduledAt.plusSeconds(60 * 60)
        val recordableCount = remaining.size - insufficient
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.patient_today_next_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(66.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = PatientTeal, modifier = Modifier.size(36.dp))
                }
                Column {
                    Text(patientSlotTitle(slot), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PatientTeal)
                    Text(timeText(doses.first()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                stringResource(R.string.patient_today_bulk_summary, formatPatientAmount(doses.sumOf(PatientDose::doseCount)), doses.map(PatientDose::medicationId).distinct().size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                doses.forEach { dose ->
                    Card(
                        onClick = { onDetail(dose) },
                        modifier = Modifier.testTag("patient-today-next-dose-${dose.key}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dose.medicationName,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(dose.dosageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                if (dose.status == DoseStatus.TAKEN) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (dose.status == DoseStatus.TAKEN) PatientTeal else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onRecordSlot(slot) },
                enabled = !loading && !updating && withinWindow && recordableCount > 0,
                modifier = Modifier.fillMaxWidth().height(64.dp).testTag("patient-today-primary-bulk-record"),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.patient_today_bulk_action), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (insufficient > 0) Text(stringResource(R.string.patient_slot_insufficient_count, insufficient), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            if (!withinWindow) Text(stringResource(R.string.patient_slot_wait_for_window), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrnEntryCard(count: Int, onClick: () -> Unit) {
    val orange = Color(0xFFF36A00)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("patient-today-prn-entry"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(2.dp, orange.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(64.dp).background(orange.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocalHospital, contentDescription = null, tint = orange, modifier = Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.patient_prn_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.patient_prn_entry_message, count), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrnMedicationCard(
    medication: PatientMedication,
    disabled: Boolean,
    onRecordPrn: (PatientMedication) -> Unit,
) {
    val orange = Color(0xFFF36A00)
    val unavailable = disabled || medication.isInsufficientForDose
    val dosage = medication.dosageText.trim()
    val displayName = if (dosage.isEmpty() || dosage == "不明") medication.name else "${medication.name} $dosage"
    val note = medication.prnInstructions?.trim().takeUnless { it.isNullOrEmpty() }
        ?: medication.notes?.trim().takeUnless { it.isNullOrEmpty() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, orange.copy(alpha = 0.32f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(50.dp).background(orange.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.LocalHospital, contentDescription = null, tint = orange, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.patient_prn_dose_count, formatPatientAmount(medication.doseCountPerIntake)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                    if (medication.isInsufficientForDose) {
                        Text(
                            stringResource(R.string.patient_inventory_insufficient),
                            modifier = Modifier.background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Button(
                onClick = { onRecordPrn(medication) },
                enabled = !unavailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .alpha(if (unavailable) 0.55f else 1f)
                    .testTag("prn-record-${medication.id}"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PatientTeal,
                    disabledContainerColor = PatientTeal,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.patient_prn_record_action), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PatientPrnUpdatingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).testTag("patient-prn-updating"),
    ) {
        Card(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 160.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), color = PatientTeal)
                Text(stringResource(R.string.patient_prn_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
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
                    Text(
                        dose.medicationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
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
internal fun PatientDoseDetailContent(
    dose: PatientDose,
    medication: PatientMedication?,
    loading: Boolean = false,
    error: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    dose.medicationName,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dose.medicationName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(dose.dosageText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(dateTimeText(dose), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            patientDetailStatusText(dose.status),
                            modifier = Modifier.background(patientDetailStatusColor(dose.status), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            item {
                PatientDoseDetailCard(
                    stringResource(R.string.patient_detail_notes),
                    medication?.notes?.trim().takeUnless { it.isNullOrEmpty() } ?: stringResource(R.string.patient_detail_no_notes),
                    insetValue = true,
                )
            }
            item {
                PatientDoseDetailCard(
                    stringResource(R.string.patient_detail_dose_amount),
                    stringResource(R.string.patient_detail_dose_value, formatPatientAmount(dose.doseCount)),
                    emphasizeValue = true,
                )
            }
            if (error) {
                item { PatientDoseDetailError(onRetry) }
            }
        }
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .testTag("patient-dose-detail-loading"),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(44.dp), color = PatientTeal)
                        Text(
                            stringResource(R.string.patient_detail_loading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientDoseDetailError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    stringResource(R.string.patient_detail_error),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Button(onClick = onRetry) { Text(stringResource(R.string.patient_detail_retry)) }
    }
}

@Composable
private fun PatientDoseDetailCard(
    title: String,
    value: String,
    emphasizeValue: Boolean = false,
    insetValue: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                value,
                modifier = if (insetValue) {
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
                } else {
                    Modifier
                },
                style = if (emphasizeValue) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                color = if (emphasizeValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (emphasizeValue) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun dateTimeText(dose: PatientDose): String = dose.scheduledAt.atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_detail_date_pattern), Locale.JAPANESE))

@Composable
private fun patientDetailStatusText(status: DoseStatus) = stringResource(
    when (status) {
        DoseStatus.PENDING -> R.string.patient_detail_status_pending
        DoseStatus.TAKEN -> R.string.patient_detail_status_taken
        DoseStatus.MISSED -> R.string.patient_detail_status_missed
    },
)

@Composable
private fun patientDetailStatusColor(status: DoseStatus) = when (status) {
    DoseStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    DoseStatus.TAKEN -> PatientTeal.copy(alpha = 0.14f)
    DoseStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
}

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
