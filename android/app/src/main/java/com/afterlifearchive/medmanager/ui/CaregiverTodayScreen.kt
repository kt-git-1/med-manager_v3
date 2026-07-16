package com.afterlifearchive.medmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
internal fun CaregiverTodayScreen(
    repository: CaregiverTodayRepository,
    patientState: CaregiverPatientState,
    enabled: Boolean,
    onOpenMedications: () -> Unit,
    onReturnToLogin: () -> Unit = {},
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
        if (enabled && selected != null) {
            val showProgress = state.mutationMessage == null || state.lastInsufficientCount > 0
            cursor.refreshIfStale { repository.load(selected.id, showProgress = showProgress) }
        }
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
        state.loadFailed -> CaregiverDataUnavailableState(
            enabled = enabled,
            onRetry = { scope.launch { repository.load(selected.id) } },
            onReturnToLogin = onReturnToLogin,
            testTagPrefix = "caregiver-today",
        )
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
        val dismissPrn = {
            if (state.updatingPrnMedicationId == null) {
                showingPrnPicker = false
                prnToConfirm = null
                repository.clearMutationFeedback()
            }
        }
        BackHandler(enabled = state.updatingPrnMedicationId == null, onBack = dismissPrn)
        CaregiverPrnScreen(
            medications = state.prnMedications,
            outOfStockMedicationIds = state.outOfStockMedicationIds,
            error = state.mutationError,
            updating = state.updatingPrnMedicationId != null,
            onBack = dismissPrn,
            onRecord = { prnToConfirm = it },
        )
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
    onDeleteDose: (PatientDose) -> Unit,
    onRecordSlot: (MedicationSlot, List<PatientDose>) -> Unit,
    onOpenPrn: () -> Unit,
    onOpenMedications: () -> Unit,
) {
    val prnCount = prnMedications.size
    val rows = MedicationSlot.entries.mapNotNull { slot ->
        doses.filter { resolveSlot(it, slotTimes) == slot }.takeIf { it.isNotEmpty() }?.let { slot to it }
    }
    val missedSlotRows = rows.filter { (_, items) -> items.any { it.status == DoseStatus.MISSED } }
    val missedRows = missedSlotRows.size
    val pendingRows = rows.count { (_, items) -> items.any { it.status == DoseStatus.PENDING } }
    val takenRows = rows.count { (_, items) -> items.all { it.status == DoseStatus.TAKEN } }
    val colors = MedicationTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("caregiver-today-list"),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CaregiverPatientAvatar(patientName)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.caregiver_today_patient_name, patientName), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_today_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                TodayCard(colors.caregiverRed, Modifier.testTag("caregiver-today-missed-alert")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        TodayIcon(Icons.Rounded.Warning, colors.caregiverRed)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.caregiver_today_missed_title), color = colors.caregiverRed, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (missedRows == 1) {
                                    val (slot, missedDoses) = missedSlotRows.single()
                                    stringResource(R.string.caregiver_today_missed_message_single, slotLabel(slot), timelineDisplayTime(slot, missedDoses, slotTimes))
                                } else {
                                    stringResource(R.string.caregiver_today_missed_message_multiple, missedRows)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (rows.isNotEmpty()) item {
                TodayCard(modifier = Modifier.testTag("caregiver-today-progress")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { takenRows.toFloat() / rows.size.coerceAtLeast(1) },
                                modifier = Modifier.size(76.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                strokeWidth = 9.dp,
                            )
                            Text("$takenRows/${rows.size}", color = colors.primaryTealText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.caregiver_today_progress_title), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_today_progress_format, takenRows, rows.size), fontSize = 20.sp, color = colors.primaryTealText, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    missedRows > 0 -> stringResource(R.string.caregiver_today_progress_missed, missedRows)
                                    pendingRows > 0 -> stringResource(R.string.caregiver_today_progress_pending, pendingRows)
                                    else -> stringResource(R.string.caregiver_today_progress_done)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (prnCount > 0) item {
                TodayCard(
                    accent = colors.orange,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPrn).testTag("caregiver-today-prn-open"),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TodayIcon(Icons.Rounded.LocalHospital, colors.orange, 62)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(stringResource(R.string.caregiver_today_prn_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_today_prn_message, prnCount), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (rows.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.caregiver_today_timeline_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("caregiver-today-timeline-title"),
                    )
                }
                MedicationSlot.entries.forEach { slot ->
                    val items = rows.firstOrNull { it.first == slot }?.second.orEmpty()
                    item(key = slot.name) {
                        CaregiverTimelineCard(
                            slot = slot,
                            doses = items,
                            slotTimes = slotTimes,
                            outOfStockMedicationIds = outOfStockMedicationIds,
                            updatingDoseKey = updatingDoseKey,
                            updatingSlot = updatingSlot,
                            enabled = enabled,
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
            CaregiverTodayEmptyCalendarIcon()
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.caregiver_today_empty_title), fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.caregiver_today_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, lineHeight = 23.sp)
            }
        }
        CaregiverTodayOnboardingStep(1, R.string.caregiver_today_empty_step_medication, MedicationTheme.colors.primaryTealText, Icons.Rounded.Medication, usePillsGlyph = true)
        CaregiverTodayOnboardingStep(2, R.string.caregiver_today_empty_step_schedule, MedicationTheme.colors.caregiverBlue, Icons.Rounded.AccessTime)
        CaregiverTodayOnboardingStep(3, R.string.caregiver_today_empty_step_record, MedicationTheme.colors.orange, Icons.Rounded.CheckCircle)
        Button(onClick = onOpenMedications, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("caregiver-today-open-medications")) {
            MedicationPillsGlyph(Color.White, Modifier.size(24.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.caregiver_today_empty_action))
        }
    }
}

@Composable
private fun CaregiverTodayEmptyCalendarIcon() {
    val tint = MaterialTheme.colorScheme.primary
    Box(Modifier.size(58.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f))) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp).align(Alignment.Center))
        Box(
            Modifier.size(19.dp).align(Alignment.BottomEnd).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color.White, fontSize = 16.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CaregiverTodayOnboardingStep(number: Int, label: Int, tint: Color, icon: ImageVector, usePillsGlyph: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (usePillsGlyph) MedicationPillsGlyph(tint, Modifier.size(19.dp))
            else Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(stringResource(label), modifier = Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CaregiverTodayLoadingState() {
    CaregiverTodayCentered {
        CircularProgressIndicator(modifier = Modifier.size(38.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), strokeWidth = 4.dp)
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
private fun CaregiverPrnScreen(
    medications: List<PatientMedication>,
    outOfStockMedicationIds: Set<String>,
    error: CaregiverTodayMutationError?,
    updating: Boolean,
    onBack: () -> Unit,
    onRecord: (PatientMedication) -> Unit,
) {
    val title = stringResource(R.string.caregiver_today_prn_screen_title)
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).semantics { paneTitle = title }.testTag("caregiver-today-prn-picker"),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                androidx.compose.material3.IconButton(
                    onClick = onBack,
                    enabled = !updating,
                    modifier = Modifier.align(Alignment.CenterStart).size(48.dp).testTag("caregiver-today-prn-back"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), modifier = Modifier.size(24.dp))
                }
                Text(title, modifier = Modifier.align(Alignment.Center), fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("caregiver-today-prn-list"),
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.caregiver_today_prn_select), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.caregiver_today_prn_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(medications, key = PatientMedication::id) { medication ->
                    CaregiverPrnMedicationCard(
                        medication = medication,
                        insufficient = medication.id in outOfStockMedicationIds,
                        disabled = updating,
                        onRecord = { onRecord(medication) },
                    )
                }
            }
        }
        error?.let {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
                PatientNoticeCard(
                    stringResource(if (it == CaregiverTodayMutationError.INSUFFICIENT_INVENTORY) R.string.patient_inventory_insufficient else R.string.caregiver_today_mutation_failed),
                    MaterialTheme.colorScheme.errorContainer,
                    null,
                )
            }
        }
        if (updating) CaregiverTodayUpdatingOverlay()
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
                Box(Modifier.size(50.dp).clip(CircleShape).background(orange.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    MedicationPillsGlyph(orange, Modifier.size(32.dp))
                }
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
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (insufficient) Color.Gray else MaterialTheme.colorScheme.primary,
                    disabledContainerColor = if (insufficient) Color.Gray else MaterialTheme.colorScheme.primary,
                ),
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
    onDeleteDose: (PatientDose) -> Unit,
    onRecordSlot: (MedicationSlot, List<PatientDose>) -> Unit,
) {
    val recordable = doses.filter { it.status != DoseStatus.TAKEN && it.medicationId !in outOfStockMedicationIds }
    val hasOutOfStock = doses.any { it.medicationId in outOfStockMedicationIds }
    val status = when {
        doses.isEmpty() -> null
        doses.all { it.status == DoseStatus.TAKEN } -> DoseStatus.TAKEN
        doses.all { it.status == DoseStatus.MISSED } -> DoseStatus.MISSED
        else -> DoseStatus.PENDING
    }
    val tint = when (status) {
        DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        DoseStatus.MISSED -> MedicationTheme.colors.caregiverRed
        DoseStatus.PENDING -> MedicationTheme.colors.orange
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val slotColor = caregiverTimelineSlotColor(slot)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("caregiver-today-timeline-${slot.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, slotColor.copy(alpha = 0.38f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(slotColor, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(caregiverTimelineSlotIcon(slot), contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(slotLabel(slot), color = slotColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(timelineDisplayTime(slot, doses, slotTimes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                CaregiverTodayStatusPill(
                    text = when {
                        hasOutOfStock -> stringResource(R.string.patient_inventory_insufficient)
                        status == null -> stringResource(R.string.caregiver_today_timeline_no_plan)
                        status == DoseStatus.TAKEN -> stringResource(R.string.caregiver_today_timeline_taken)
                        status == DoseStatus.MISSED -> stringResource(R.string.caregiver_today_timeline_missed)
                        else -> stringResource(R.string.caregiver_today_timeline_pending)
                    },
                    color = if (hasOutOfStock) MedicationTheme.colors.caregiverRed else tint,
                    icon = when {
                        hasOutOfStock -> Icons.Rounded.Warning
                        status == DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                        status == DoseStatus.PENDING -> Icons.Rounded.Warning
                        else -> null
                    },
                )
            }

            if (doses.isEmpty()) {
                Text(
                    stringResource(R.string.caregiver_today_timeline_no_dose),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    doses.forEach { dose ->
                        CaregiverTimelineDoseLine(
                            dose = dose,
                            outOfStock = dose.status != DoseStatus.TAKEN && dose.medicationId in outOfStockMedicationIds,
                            updating = updatingDoseKey == dose.key,
                            deleteEnabled = enabled && updatingSlot == null,
                            onDeleteDose = onDeleteDose,
                        )
                    }
                }
            }

            if (recordable.isNotEmpty()) {
                Button(
                    onClick = { onRecordSlot(slot, recordable) },
                    enabled = enabled && updatingDoseKey == null && updatingSlot == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("caregiver-today-slot-action-${slot.name.lowercase()}"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (updatingSlot == slot) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.caregiver_today_timeline_record_slot, recordable.size), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CaregiverTimelineDoseLine(
    dose: PatientDose,
    outOfStock: Boolean,
    updating: Boolean,
    deleteEnabled: Boolean,
    onDeleteDose: (PatientDose) -> Unit,
) {
    val color = when {
        outOfStock -> MedicationTheme.colors.caregiverRed
        dose.status == DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        dose.status == DoseStatus.MISSED -> MedicationTheme.colors.caregiverRed
        else -> MedicationTheme.colors.orange
    }
    val dosage = dose.dosageText.trim()
    val displayName = if (dosage.isEmpty() || dosage == "不明") dose.medicationName else "${dose.medicationName} $dosage"
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .border(1.dp, MedicationTheme.colors.cardStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp)
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.08f))), CircleShape)
                .border(1.dp, color.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Medication, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                stringResource(R.string.patient_prn_dose_count, formatTodayNumber(dose.doseCount)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (updating) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        } else {
            Box(Modifier.size(34.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        outOfStock -> Icons.Rounded.Warning
                        dose.status == DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                        dose.status == DoseStatus.MISSED -> Icons.Rounded.Warning
                        else -> Icons.Rounded.AccessTime
                    },
                    contentDescription = stringResource(
                        when {
                            outOfStock -> R.string.patient_inventory_insufficient
                            dose.status == DoseStatus.TAKEN -> R.string.caregiver_today_timeline_taken
                            dose.status == DoseStatus.MISSED -> R.string.caregiver_today_timeline_missed
                            else -> R.string.caregiver_today_timeline_pending
                        },
                    ),
                    tint = color,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        if (dose.status == DoseStatus.TAKEN) {
            Box(
                modifier = Modifier.size(48.dp)
                    .clickable(enabled = deleteEnabled, role = Role.Button) { onDeleteDose(dose) }
                    .testTag("caregiver-today-dose-action-${dose.key}"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(24.dp).background(Color.Gray, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.caregiver_today_delete_individual_accessibility, dose.medicationName),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun caregiverTimelineSlotColor(slot: MedicationSlot): Color = when (slot) {
    MedicationSlot.MORNING -> MedicationTheme.colors.slotMorning
    MedicationSlot.NOON -> MedicationTheme.colors.slotNoon
    MedicationSlot.EVENING -> MedicationTheme.colors.slotEvening
    MedicationSlot.BEDTIME -> MedicationTheme.colors.slotBedtime
}

private fun caregiverTimelineSlotIcon(slot: MedicationSlot): ImageVector = when (slot) {
    MedicationSlot.MORNING -> Icons.Rounded.WbTwilight
    MedicationSlot.NOON -> Icons.Rounded.LightMode
    MedicationSlot.EVENING -> Icons.Rounded.DarkMode
    MedicationSlot.BEDTIME -> Icons.Rounded.Bed
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
private fun CaregiverTodayStatusPill(text: String, color: Color, icon: ImageVector?) {
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.13f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TodayCard(accent: Color? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (accent == null) BorderStroke(1.dp, MedicationTheme.colors.cardStroke)
        else BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
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

private fun timelineDisplayTime(
    slot: MedicationSlot,
    doses: List<PatientDose>,
    times: CaregiverSlotTimes?,
): String = doses.minByOrNull(PatientDose::scheduledAt)
    ?.scheduledAt
    ?.atZone(TOKYO)
    ?.format(DISPLAY_TIME_FORMAT)
    ?: configuredTime(slot, times)

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
private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")
private fun formatTodayNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
