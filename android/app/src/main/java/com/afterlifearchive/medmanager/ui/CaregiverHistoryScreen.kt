package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CaregiverHistoryScreen(
    repository: CaregiverHistoryRepository,
    patientState: CaregiverPatientState,
    enabled: Boolean,
    highlightDurationMillis: Long? = 4_000,
    reportRepository: CaregiverReportRepository? = null,
    billingEnabled: Boolean = com.afterlifearchive.medmanager.BuildConfig.BILLING_ENABLED,
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
        patientState.loading && patientState.patients.isEmpty() -> HistoryCentered { CircularProgressIndicator() }
        patientState.loadFailed -> HistoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message))
        patientState.patients.isEmpty() -> HistoryMessage(stringResource(R.string.caregiver_no_patient_title), stringResource(R.string.caregiver_no_patient_message))
        selectedPatient == null -> HistoryMessage(stringResource(R.string.caregiver_no_selection_title), stringResource(R.string.caregiver_no_selection_message))
        state.loadingMonth && state.days.isEmpty() -> CaregiverHistoryLoadingState()
        state.monthFailed -> HistoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message)) {
            Button(onClick = { scope.launch { repository.loadMonth(selectedPatient.id, state.displayedMonth) } }) { Text(stringResource(R.string.common_retry)) }
        }
        else -> Box(Modifier.fillMaxSize()) {
            CaregiverHistoryMonth(
                patientName = selectedPatient.displayName,
                displayedMonth = state.displayedMonth,
                days = state.days,
                selectedDate = state.selectedDate,
                dayDetail = state.dayDetail,
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
                onMonth = { scope.launch { repository.loadMonth(selectedPatient.id, it) } },
                onDate = repository::selectDate,
                onRecordMissed = { backfillDose = it },
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
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onRecordMissed: (HistoryScheduledDose) -> Unit,
    reportAction: (@Composable () -> Unit)?,
) {
    val now = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    val currentMonth = YearMonth.from(now)
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-history-month"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(stringResource(R.string.caregiver_history_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_history_patient, patientName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (refreshFailed) item { CaregiverStaleDataCard("caregiver-history-stale", onRetry) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onMonth(displayedMonth.minusMonths(1)) }, modifier = Modifier.testTag("caregiver-history-previous-month")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.caregiver_history_previous_month))
                }
                Text("${displayedMonth.year}年 ${displayedMonth.monthValue}月", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onMonth(displayedMonth.plusMonths(1)) }, enabled = displayedMonth < currentMonth, modifier = Modifier.testTag("caregiver-history-next-month")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.caregiver_history_next_month))
                }
            }
        }
        if (retentionCutoffDate != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.patient_history_locked_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.patient_history_locked_message, retentionDays ?: 30, retentionCutoffDate))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.caregiver_history_calendar_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_history_calendar_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CaregiverCalendarContent(displayedMonth, days, selectedDate, onDate)
                    HistoryLegendRow()
                }
            }
        }
        if (selectedDate != null) {
            item { CaregiverSelectedDaySummary(selectedDate, days.firstOrNull { it.date == selectedDate.toString() }) }
            if (dayRefreshFailed) item { CaregiverStaleDataCard("caregiver-history-day-stale", onRetryDay) }
            if (mutationFailed) item { Text(stringResource(R.string.caregiver_history_backfill_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            if (mutationSucceeded) item { Text(stringResource(R.string.caregiver_history_backfill_success), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            item { Text(selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.JAPANESE)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("caregiver-history-day-detail")) }
            if (loadingDay) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (dayFailed) item { HistoryMessageCard(stringResource(R.string.caregiver_data_unavailable_message), onRetryDay) }
            if (!loadingDay && !dayFailed && dayDetail != null) {
                if (dayDetail.doses.isEmpty() && dayDetail.prnItems.isEmpty()) item { HistoryMessageCard(stringResource(R.string.patient_history_day_empty_message)) }
                if (dayDetail.doses.isNotEmpty()) item { Text(stringResource(R.string.patient_history_scheduled_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(dayDetail.doses, key = { "${it.medicationId}:${it.scheduledAt}" }) { dose -> HistoryScheduledDoseRow(dose, highlightedSlot == dose.slot, onRecordMissed, HistoryDayRowStyle.CAREGIVER) }
                if (dayDetail.prnItems.isNotEmpty()) item { Text(stringResource(R.string.patient_history_prn_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(dayDetail.prnItems, key = { "${it.medicationId}:${it.takenAt}" }) { PrnHistoryRow(it, HistoryDayRowStyle.CAREGIVER) }
            }
        }
        reportAction?.let { item { it() } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CaregiverCalendarContent(yearMonth: YearMonth, days: List<HistoryDay>, selectedDate: LocalDate?, onDate: (LocalDate) -> Unit) {
    val byDate = days.associateBy(HistoryDay::date)
    val leading = yearMonth.atDay(1).dayOfWeek.value % 7
    val cells = (List<LocalDate?>(leading) { null } + (1..yearMonth.lengthOfMonth()).map(yearMonth::atDay)).let {
        it + List((7 - it.size % 7) % 7) { null }
    }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                stringResource(R.string.patient_history_weekdays).split(',').forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
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
            .padding(2.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onDate(date) }
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                this.selected = selected
            }
            .testTag("caregiver-history-day-$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            statuses.filter { it != HistoryStatus.NONE }.forEach { Box(Modifier.size(5.dp).background(historyDotColor(it), CircleShape)) }
            if ((day?.prnCount ?: 0) > 0) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
        }
    }
}

@Composable
private fun historyDotColor(status: HistoryStatus) = when (status) {
    HistoryStatus.TAKEN -> MaterialTheme.colorScheme.primary
    HistoryStatus.MISSED -> MaterialTheme.colorScheme.error
    HistoryStatus.PENDING -> MaterialTheme.colorScheme.tertiary
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(
            stringResource(R.string.patient_status_taken) to MaterialTheme.colorScheme.primary,
            stringResource(R.string.patient_status_missed) to MaterialTheme.colorScheme.error,
            stringResource(R.string.patient_status_pending) to MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.caregiver_history_prn) to MaterialTheme.colorScheme.secondary,
        ).forEach { (label, color) -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(7.dp).background(color, CircleShape)); Text(label, style = MaterialTheme.typography.labelSmall) } }
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
        pending > 0 -> R.string.caregiver_history_selected_pending_help
        else -> R.string.caregiver_history_selected_complete_help
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.caregiver_history_selected_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.JAPANESE)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (total == 0) stringResource(R.string.caregiver_history_selected_none) else stringResource(R.string.caregiver_history_summary_taken, taken), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (total > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistorySummaryPill(stringResource(R.string.caregiver_history_summary_taken, taken), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                HistorySummaryPill(stringResource(R.string.caregiver_history_summary_pending, pending), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
            if (missed > 0) HistorySummaryPill(stringResource(R.string.caregiver_history_summary_missed, missed), MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun HistorySummaryPill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(tint.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(text, color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryMessageCard(message: String, onRetry: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp))
                Text(stringResource(R.string.patient_today_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HistoryMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    HistoryCentered { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center); action?.invoke() }
}

@Composable
private fun HistoryCentered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
}
