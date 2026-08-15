package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationRecordingPolicy
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HistoryPendingGray = Color(0xFF8E8E93)
private val HistoryPrnPurple = Color(0xFFAF52DE)

@Composable
internal fun CaregiverHistoryScreen(
    repository: CaregiverHistoryRepository,
    patientState: CaregiverPatientState,
    enabled: Boolean,
    highlightDurationMillis: Long? = 4_000,
    reportRepository: CaregiverReportRepository? = null,
    billingEnabled: Boolean = com.afterlifearchive.medmanager.BuildConfig.BILLING_ENABLED,
    onReturnToLogin: () -> Unit = {},
    onOpenPatients: () -> Unit = {},
    onCreatePatient: () -> Unit = {},
    onRetryPatients: () -> Unit = {},
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val freshness by repository.freshness.collectAsStateWithLifecycle()
    val selectedPatient = patientState.selectedPatient
    val cursor = remember(repository) { repository.newFreshnessCursor() }
    val scope = rememberCoroutineScope()
    var backfillDose by remember { mutableStateOf<HistoryScheduledDose?>(null) }

    LaunchedEffect(enabled, selectedPatient?.id, state.displayedMonth, freshness.dose, freshness.slotTimes) {
        if (enabled && selectedPatient != null) cursor.refreshIfStale {
            repository.loadMonth(selectedPatient.id, state.displayedMonth)
        }
        if (selectedPatient == null) repository.clear()
    }
    LaunchedEffect(enabled, selectedPatient?.id, state.patientId, state.selectedDate, state.dayDetail?.date) {
        val date = state.selectedDate
        if (enabled && selectedPatient != null && date != null && state.dayDetail?.date != date.toString()) {
            repository.loadDay(selectedPatient.id, date)
        }
    }
    LaunchedEffect(state.highlightedSlot, state.dayDetail?.date) {
        if (state.highlightedSlot != null && state.dayDetail?.date == state.selectedDate?.toString() && highlightDurationMillis != null) {
            delay(highlightDurationMillis)
            repository.clearHighlight()
        }
    }

    when {
        patientState.loading && patientState.patients.isEmpty() -> CaregiverHistoryLoadingState()
        patientState.loadFailed -> CaregiverDataUnavailableState(
            enabled = enabled,
            onRetry = onRetryPatients,
            onReturnToLogin = onReturnToLogin,
            testTagPrefix = "caregiver-history-patients",
        )
        patientState.patients.isEmpty() -> CaregiverNoPatientState(
            enabled = enabled,
            onCreatePatient = onCreatePatient,
            testTagPrefix = "caregiver-history",
        )
        selectedPatient == null -> CaregiverPatientSelectionRequiredState(
            enabled = enabled,
            onOpenPatients = onOpenPatients,
            testTagPrefix = "caregiver-history",
            icon = Icons.Rounded.AccessTime,
            usePillsGlyph = false,
        )
        else -> Box(Modifier.fillMaxSize()) {
            CaregiverHistoryMonth(
                patientName = selectedPatient.displayName,
                displayedMonth = state.displayedMonth,
                days = state.days,
                selectedDate = state.selectedDate,
                dayDetail = state.dayDetail,
                loadingMonth = state.loadingMonth && state.days.isEmpty(),
                monthFailed = state.monthFailed,
                loadingDay = state.loadingDay,
                dayFailed = state.dayFailed,
                dayRefreshFailed = state.dayRefreshFailed,
                mutationFailed = state.mutationFailed,
                mutationSucceeded = state.mutationSucceeded,
                highlightedSlot = state.highlightedSlot,
                refreshFailed = state.monthRefreshFailed,
                retentionCutoffDate = state.retentionCutoffDate,
                retentionDays = state.retentionDays,
                onRetry = { scope.launch { repository.loadMonth(selectedPatient.id, state.displayedMonth) } },
                onRetryDay = { state.selectedDate?.let { scope.launch { repository.loadDay(selectedPatient.id, it) } } },
                onReturnToLogin = onReturnToLogin,
                onMonth = { scope.launch { repository.loadMonth(selectedPatient.id, it) } },
                onDate = repository::selectDate,
                onRecordMissed = { backfillDose = it },
                allowMonthNavigation = billingEnabled,
                reportAction = if (reportRepository != null) {
                    { CaregiverReportAction(reportRepository, selectedPatient.id, billingEnabled) }
                } else null,
            )
            if (state.refreshingMonth || state.updating) CaregiverHistoryUpdatingOverlay()
        }
    }

    backfillDose?.let { dose ->
        AlertDialog(
            onDismissRequest = { backfillDose = null },
            title = { Text(stringResource(R.string.caregiver_history_backfill_confirm_title)) },
            text = { Text(stringResource(R.string.caregiver_history_backfill_confirm_message, dose.medicationName)) },
            dismissButton = { TextButton(onClick = { backfillDose = null }) { Text(stringResource(R.string.caregiver_medication_form_cancel)) } },
            confirmButton = {
                TextButton(
                    onClick = { backfillDose = null; scope.launch { repository.recordMissed(selectedPatient!!.id, dose) } },
                    modifier = Modifier.testTag("caregiver-history-backfill-confirm"),
                ) { Text(stringResource(R.string.caregiver_history_backfill_action)) }
            },
        )
    }
}

@Composable
private fun CaregiverHistoryMonth(
    patientName: String,
    displayedMonth: YearMonth,
    days: List<HistoryDay>,
    selectedDate: LocalDate?,
    dayDetail: com.afterlifearchive.medmanager.data.patient.HistoryDayDetail?,
    loadingMonth: Boolean,
    monthFailed: Boolean,
    loadingDay: Boolean,
    dayFailed: Boolean,
    dayRefreshFailed: Boolean,
    mutationFailed: Boolean,
    mutationSucceeded: Boolean,
    highlightedSlot: com.afterlifearchive.medmanager.data.patient.MedicationSlot?,
    refreshFailed: Boolean,
    retentionCutoffDate: String?,
    retentionDays: Int?,
    onRetry: () -> Unit,
    onRetryDay: () -> Unit,
    onReturnToLogin: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onRecordMissed: (HistoryScheduledDose) -> Unit,
    allowMonthNavigation: Boolean,
    reportAction: (@Composable () -> Unit)?,
) {
    val now = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    val currentMonth = YearMonth.from(now)
    val slotGroups = remember(dayDetail) { caregiverHistorySlotGroups(dayDetail?.doses.orEmpty()) }
    var expandedSlotKeys by rememberSaveable(selectedDate?.toString()) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(dayDetail?.date) {
        if (dayDetail != null) {
            expandedSlotKeys = slotGroups.filter { it.status == DoseStatus.TAKEN }.mapTo(mutableSetOf()) { it.slot.name }
            highlightedSlot?.let { expandedSlotKeys = expandedSlotKeys + it.name }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-history-month"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CaregiverPatientAvatar(patientName)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.caregiver_history_title), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.caregiver_history_patient, patientName),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (refreshFailed) item { CaregiverStaleDataCard("caregiver-history-stale", onRetry) }
        item {
            CaregiverHistoryMonthNavigation(
                displayedMonth = displayedMonth,
                currentMonth = currentMonth,
                allowMonthNavigation = allowMonthNavigation,
                onMonth = onMonth,
            )
        }
        if (loadingMonth) {
            item { CaregiverHistoryInlineLoading("caregiver-history-loading") }
        } else if (monthFailed) {
            item {
                CaregiverDataUnavailableState(
                    enabled = true,
                    onRetry = onRetry,
                    onReturnToLogin = onReturnToLogin,
                    testTagPrefix = "caregiver-history-month",
                    fillScreen = false,
                )
            }
        } else {
            if (retentionCutoffDate != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.patient_history_locked_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.patient_history_locked_message, retentionDays ?: 30, retentionCutoffDate))
                    }
                }
            }
            item {
                HistoryCard {
                    Text(stringResource(R.string.caregiver_history_calendar_title), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_history_calendar_message), fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CaregiverCalendarContent(displayedMonth, days, selectedDate, onDate)
                    HistoryLegendRow()
                }
            }
            if (selectedDate != null) {
                item { CaregiverSelectedDaySummary(selectedDate, days.firstOrNull { it.date == selectedDate.toString() }) }
                if (dayRefreshFailed) item { CaregiverStaleDataCard("caregiver-history-day-stale", onRetryDay) }
                if (mutationFailed) item { Text(stringResource(R.string.caregiver_history_backfill_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                if (mutationSucceeded) item { Text(stringResource(R.string.caregiver_history_backfill_success), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                item { Text(selectedDate.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE)), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("caregiver-history-day-detail")) }
                if (loadingDay) item { CaregiverHistoryInlineLoading("caregiver-history-day-loading") }
                if (dayFailed) item {
                    CaregiverDataUnavailableState(
                        enabled = true,
                        onRetry = onRetryDay,
                        onReturnToLogin = onReturnToLogin,
                        testTagPrefix = "caregiver-history-day",
                        fillScreen = false,
                    )
                }
                if (!loadingDay && !dayFailed && dayDetail != null) {
                    if (dayDetail.doses.isEmpty() && dayDetail.prnItems.isEmpty()) {
                        item {
                            CaregiverHistoryDayEmptyState()
                        }
                    }
                    items(slotGroups, key = { it.slot.name }) { group ->
                        CaregiverHistorySlotGroupCard(
                            group = group,
                            expanded = group.slot.name in expandedSlotKeys,
                            highlighted = highlightedSlot == group.slot,
                            onToggle = {
                                expandedSlotKeys = if (group.slot.name in expandedSlotKeys) expandedSlotKeys - group.slot.name else expandedSlotKeys + group.slot.name
                            },
                            onRecordMissed = onRecordMissed,
                        )
                    }
                    items(dayDetail.prnItems.sortedBy { it.takenAt }, key = { "prn:${it.medicationId}:${it.takenAt}" }) { record ->
                        PrnHistoryRow(record, HistoryDayRowStyle.CAREGIVER)
                    }
                }
            }
            reportAction?.let { item { it() } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class CaregiverHistorySlotGroup(
    val slot: MedicationSlot,
    val doses: List<HistoryScheduledDose>,
) {
    val scheduledAt = doses.minOf(HistoryScheduledDose::scheduledAt)
    val takenAt = doses.mapNotNull(HistoryScheduledDose::takenAt).maxOrNull()
    val status: DoseStatus = when {
        doses.all { it.status == DoseStatus.TAKEN } -> DoseStatus.TAKEN
        doses.any { it.status == DoseStatus.MISSED } -> DoseStatus.MISSED
        else -> DoseStatus.PENDING
    }
    val partiallyTaken = doses.count { it.status == DoseStatus.TAKEN } in 1 until doses.size
    val maximumDelaySeconds = doses.mapNotNull { dose -> dose.takenAt?.let { MedicationRecordingPolicy.delaySeconds(dose.scheduledAt, it) } }.maxOrNull() ?: 0L
    val late = maximumDelaySeconds >= MedicationRecordingPolicy.LATE_THRESHOLD_SECONDS
    val recordedByTypes = doses.mapNotNull(HistoryScheduledDose::recordedByType).toSet()
}

private fun caregiverHistorySlotGroups(doses: List<HistoryScheduledDose>): List<CaregiverHistorySlotGroup> =
    MedicationSlot.entries.mapNotNull { slot ->
        doses.filter { it.slot == slot }.takeIf(List<HistoryScheduledDose>::isNotEmpty)?.let { CaregiverHistorySlotGroup(slot, it) }
    }

@Composable
private fun CaregiverHistorySlotGroupCard(
    group: CaregiverHistorySlotGroup,
    expanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    onRecordMissed: (HistoryScheduledDose) -> Unit,
) {
    val slotColor = caregiverHistorySlotColor(group.slot)
    val statusColor = when {
        group.partiallyTaken || group.late -> MedicationTheme.colors.orange
        group.status == DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        group.status == DoseStatus.MISSED -> MedicationTheme.colors.caregiverRed
        else -> HistoryPendingGray
    }
    val statusText = when {
        group.partiallyTaken -> stringResource(R.string.history_status_partial)
        group.late -> stringResource(R.string.patient_status_late)
        else -> stringResource(when (group.status) {
            DoseStatus.TAKEN -> R.string.patient_status_taken
            DoseStatus.MISSED -> R.string.patient_status_missed
            DoseStatus.PENDING -> R.string.patient_status_pending
        })
    }
    val recorderText = when {
        group.recordedByTypes.size > 1 -> stringResource(R.string.history_recorded_by_mixed)
        group.recordedByTypes.singleOrNull() == RecordedByType.PATIENT -> stringResource(R.string.patient_history_recorded_by_patient)
        group.recordedByTypes.singleOrNull() == RecordedByType.CAREGIVER -> stringResource(R.string.patient_history_recorded_by_caregiver)
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp))
            .testTag(if (highlighted) "history-dose-highlighted-${group.slot.name.lowercase()}" else "history-dose-${group.slot.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, slotColor.copy(alpha = if (highlighted) 1f else 0.38f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp).testTag("caregiver-history-slot-group-${group.slot.name.lowercase()}")) {
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("caregiver-history-slot-header-${group.slot.name.lowercase()}"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).background(slotColor, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(caregiverHistorySlotIcon(group.slot), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text(caregiverHistorySlotTitle(group.slot), color = slotColor, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.history_schedule_format, caregiverHistoryTime(group.scheduledAt)), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.background(statusColor.copy(alpha = 0.13f), CircleShape).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(when (group.status) {
                            DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                            DoseStatus.MISSED -> Icons.Rounded.Warning
                            DoseStatus.PENDING -> Icons.Rounded.AccessTime
                        }, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Text(statusText, color = statusColor, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                group.takenAt?.let { takenAt ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.caregiver_today_actual_time, caregiverHistoryTime(takenAt)), color = if (group.late) MedicationTheme.colors.orange else MedicationTheme.colors.primaryTealText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (group.late) Text(caregiverHistoryDelay(group.maximumDelaySeconds), color = MedicationTheme.colors.orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        recorderText?.let { Text(it, color = MedicationTheme.colors.primaryTealText, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                }
            }
            if (expanded) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.doses.sortedBy(HistoryScheduledDose::medicationName).forEach { dose ->
                        CaregiverHistoryMedicationRow(dose, onRecordMissed)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaregiverHistoryMedicationRow(dose: HistoryScheduledDose, onRecordMissed: (HistoryScheduledDose) -> Unit) {
    val statusColor = when (dose.status) {
        DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        DoseStatus.MISSED -> MedicationTheme.colors.caregiverRed
        DoseStatus.PENDING -> HistoryPendingGray
    }
    val dosage = dose.dosageText.trim()
    Column(
        Modifier.fillMaxWidth().background(MedicationTheme.colors.elevatedBackground.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
            .border(1.dp, MedicationTheme.colors.cardStroke, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("caregiver-history-medication-${dose.medicationId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).background(statusColor.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MedicalServices, contentDescription = null, tint = statusColor, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (dosage.isEmpty() || dosage == "不明") dose.medicationName else "${dose.medicationName} $dosage", fontWeight = FontWeight.Bold)
                Text("1回${formatHistoryQuantity(dose.doseCountPerIntake)}錠", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(26.dp).background(statusColor, CircleShape), contentAlignment = Alignment.Center) {
                Icon(when (dose.status) {
                    DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                    DoseStatus.MISSED -> Icons.Rounded.Warning
                    DoseStatus.PENDING -> Icons.Rounded.AccessTime
                }, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (dose.status == DoseStatus.MISSED) {
            Button(onClick = { onRecordMissed(dose) }, modifier = Modifier.fillMaxWidth().height(42.dp).testTag("history-backfill-${dose.medicationId}")) {
                Text(stringResource(R.string.caregiver_history_backfill_action), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun caregiverHistorySlotTitle(slot: MedicationSlot): String = stringResource(when (slot) {
    MedicationSlot.MORNING -> R.string.patient_slot_morning
    MedicationSlot.NOON -> R.string.patient_slot_noon
    MedicationSlot.EVENING -> R.string.patient_slot_evening
    MedicationSlot.BEDTIME -> R.string.patient_slot_bedtime
})

@Composable
private fun caregiverHistorySlotColor(slot: MedicationSlot): Color = when (slot) {
    MedicationSlot.MORNING -> MedicationTheme.colors.slotMorning
    MedicationSlot.NOON -> MedicationTheme.colors.slotNoon
    MedicationSlot.EVENING -> MedicationTheme.colors.slotEvening
    MedicationSlot.BEDTIME -> MedicationTheme.colors.slotBedtime
}

private fun caregiverHistorySlotIcon(slot: MedicationSlot): ImageVector = when (slot) {
    MedicationSlot.MORNING -> Icons.Rounded.WbTwilight
    MedicationSlot.NOON -> Icons.Rounded.LightMode
    MedicationSlot.EVENING -> Icons.Rounded.DarkMode
    MedicationSlot.BEDTIME -> Icons.Rounded.Bed
}

private fun caregiverHistoryTime(instant: java.time.Instant) = instant.atZone(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun caregiverHistoryDelay(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0L) / 60
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours > 0 && remainder > 0 -> stringResource(R.string.history_delay_hours_minutes, hours, remainder)
        hours > 0 -> stringResource(R.string.history_delay_hours, hours)
        else -> stringResource(R.string.history_delay_minutes, minutes)
    }
}

private fun formatHistoryQuantity(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

@Composable
private fun CaregiverHistoryMonthNavigation(
    displayedMonth: YearMonth,
    currentMonth: YearMonth,
    allowMonthNavigation: Boolean,
    onMonth: (YearMonth) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (allowMonthNavigation) {
            IconButton(onClick = { onMonth(displayedMonth.minusMonths(1)) }, modifier = Modifier.testTag("caregiver-history-previous-month")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.caregiver_history_previous_month))
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
        Text("${displayedMonth.year}年${displayedMonth.monthValue}月", fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
        if (allowMonthNavigation) {
            IconButton(onClick = { onMonth(displayedMonth.plusMonths(1)) }, enabled = displayedMonth < currentMonth, modifier = Modifier.testTag("caregiver-history-next-month")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.caregiver_history_next_month))
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun CaregiverCalendarContent(yearMonth: YearMonth, days: List<HistoryDay>, selectedDate: LocalDate?, onDate: (LocalDate) -> Unit) {
    val byDate = days.associateBy(HistoryDay::date)
    val leading = yearMonth.atDay(1).dayOfWeek.value - 1
    val cells = (List<LocalDate?>(leading) { null } + (1..yearMonth.lengthOfMonth()).map(yearMonth::atDay)).let {
        it + List((7 - it.size % 7) % 7) { null }
    }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                stringResource(R.string.caregiver_history_weekdays).split(',').forEachIndexed { index, weekday ->
                    Text(
                        weekday,
                        Modifier.weight(1f).testTag("caregiver-history-weekday-$index"),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { date ->
                        if (date == null) Spacer(Modifier.weight(1f).height(54.dp))
                        else CaregiverCalendarDay(date, byDate[date.toString()], date == selectedDate, Modifier.weight(1f), onDate)
                    }
                }
            }
    }
}

@Composable
private fun CaregiverCalendarDay(date: LocalDate, day: HistoryDay?, selected: Boolean, modifier: Modifier, onDate: (LocalDate) -> Unit) {
    val statuses = day?.let { listOf(it.morning, it.noon, it.evening, it.bedtime) }.orEmpty()
    val hasHistory = statuses.any { it != HistoryStatus.NONE } || (day?.prnCount ?: 0) > 0
    val slotStatuses = listOf(
        R.string.patient_slot_morning to day?.morning,
        R.string.patient_slot_noon to day?.noon,
        R.string.patient_slot_evening to day?.evening,
        R.string.patient_slot_bedtime to day?.bedtime,
    ).mapNotNull { (slot, status) ->
        status?.takeUnless { it == HistoryStatus.NONE }?.let {
            "${stringResource(slot)} ${stringResource(historyStatusResource(it))}"
        }
    }.toMutableList()
    if ((day?.prnCount ?: 0) > 0) {
        slotStatuses += stringResource(R.string.caregiver_history_prn_accessibility, day!!.prnCount)
    }
    val summary = slotStatuses.joinToString("、").ifEmpty { stringResource(R.string.patient_history_no_schedule) }
    val dateLabel = date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.JAPANESE))
    val accessibilityLabel = stringResource(R.string.caregiver_history_day_accessibility, dateLabel, summary)
    Column(
        modifier
            .height(54.dp)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    hasHistory -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                when {
                    selected -> MedicationTheme.colors.primaryTealText.copy(alpha = 0.40f)
                    hasHistory -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                },
                RoundedCornerShape(10.dp),
            )
            .clickable { onDate(date) }
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                this.selected = selected
            }
            .testTag("caregiver-history-day-$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (selected) Color.White else if (hasHistory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            statuses.filter { it != HistoryStatus.NONE }.forEach { Box(Modifier.size(6.dp).background(historyDotColor(it), CircleShape)) }
            if ((day?.prnCount ?: 0) > 0) Box(Modifier.size(6.dp).background(HistoryPrnPurple, CircleShape))
        }
    }
}

@Composable
private fun historyDotColor(status: HistoryStatus) = when (status) {
    HistoryStatus.TAKEN -> MaterialTheme.colorScheme.primary
    HistoryStatus.MISSED -> MedicationTheme.colors.caregiverRed
    HistoryStatus.PENDING -> HistoryPendingGray
    HistoryStatus.NONE -> Color.Transparent
}

private fun historyStatusResource(status: HistoryStatus) = when (status) {
    HistoryStatus.TAKEN -> R.string.patient_status_taken
    HistoryStatus.MISSED -> R.string.patient_status_missed
    HistoryStatus.PENDING -> R.string.patient_status_pending
    HistoryStatus.NONE -> R.string.patient_history_no_schedule
}

@Composable
private fun HistoryLegendRow() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.caregiver_history_legend_help),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
        listOf(
            stringResource(R.string.patient_status_taken) to MaterialTheme.colorScheme.primary,
            stringResource(R.string.patient_status_missed) to MedicationTheme.colors.caregiverRed,
            stringResource(R.string.patient_status_pending) to HistoryPendingGray,
            stringResource(R.string.caregiver_history_prn) to HistoryPrnPurple,
        ).forEach { (label, color) ->
            Row(
                Modifier.widthIn(min = 92.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        }
    }
}

@Composable
private fun CaregiverSelectedDaySummary(date: LocalDate, day: HistoryDay?) {
    val statuses = day?.let { listOf(it.morning, it.noon, it.evening, it.bedtime) }.orEmpty().filter { it != HistoryStatus.NONE }
    val taken = statuses.count { it == HistoryStatus.TAKEN }
    val pending = statuses.count { it == HistoryStatus.PENDING }
    val missed = statuses.count { it == HistoryStatus.MISSED }
    val total = statuses.size
    val help = when {
        total == 0 && (day?.prnCount ?: 0) == 0 -> R.string.caregiver_history_selected_none_help
        missed > 0 -> R.string.caregiver_history_selected_missed_help
        pending > 0 && date > LocalDate.now(ZoneId.of("Asia/Tokyo")) -> R.string.caregiver_history_selected_upcoming_help
        pending > 0 -> R.string.caregiver_history_selected_pending_help
        else -> R.string.caregiver_history_selected_complete_help
    }
    HistoryCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.caregiver_history_selected_title), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(date.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)), fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                }
                if ((day?.prnCount ?: 0) > 0) {
                    HistorySummaryPill(stringResource(R.string.caregiver_history_prn_count, day!!.prnCount), HistoryPrnPurple, Icons.Rounded.MedicalServices)
                }
            }
            Text(
                if (total == 0) stringResource(R.string.caregiver_history_selected_none)
                else stringResource(R.string.caregiver_history_summary_format, taken, total),
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(help), fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (total > 0) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    HistorySummaryPill(stringResource(R.string.caregiver_history_summary_taken, taken), MaterialTheme.colorScheme.primary, Icons.Rounded.CheckCircle)
                    HistorySummaryPill(stringResource(R.string.caregiver_history_summary_pending, pending), if (pending > 0) MedicationTheme.colors.orange else HistoryPendingGray, Icons.Rounded.AccessTime)
                    if (missed > 0) HistorySummaryPill(stringResource(R.string.caregiver_history_summary_missed, missed), MedicationTheme.colors.caregiverRed, Icons.Rounded.Warning)
                }
            }
        }
}

@Composable
private fun HistorySummaryPill(text: String, tint: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 30.dp).widthIn(min = 140.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, color = tint, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun CaregiverHistoryDayEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp).testTag("caregiver-history-day-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.patient_history_day_empty_title), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.patient_history_day_empty_message), fontSize = 20.sp, lineHeight = 27.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CaregiverHistoryInlineLoading(testTag: String) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp).testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(38.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), strokeWidth = 4.dp)
        Text(stringResource(R.string.patient_today_loading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CaregiverHistoryLoadingState() {
    HistoryCentered {
        CircularProgressIndicator(modifier = Modifier.size(52.dp))
        Text(stringResource(R.string.patient_today_loading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("caregiver-history-loading"))
    }
}

@Composable
private fun CaregiverHistoryUpdatingOverlay() {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).clickable(onClick = {}).testTag("caregiver-history-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(Modifier.width(172.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painter = painterResource(R.drawable.app_image), contentDescription = null, modifier = Modifier.size(64.dp))
                CircularProgressIndicator(modifier = Modifier.size(38.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), strokeWidth = 4.dp)
                Text(stringResource(R.string.patient_today_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoryCentered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
}
