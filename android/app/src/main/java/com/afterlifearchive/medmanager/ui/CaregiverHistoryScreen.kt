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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
@OptIn(ExperimentalMaterial3Api::class)
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
    var showDetail by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(state.navigationRequestId, enabled, selectedPatient?.id) {
        if (state.navigationRequestId > 0 && enabled && selectedPatient?.id == state.notificationPatientId) showDetail = true
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
        state.loadingMonth && state.days.isEmpty() -> HistoryCentered { CircularProgressIndicator() }
        state.monthFailed -> HistoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message)) {
            Button(onClick = { scope.launch { repository.loadMonth(selectedPatient.id, state.displayedMonth) } }) { Text(stringResource(R.string.common_retry)) }
        }
        else -> CaregiverHistoryMonth(
            patientName = selectedPatient.displayName,
            displayedMonth = state.displayedMonth,
            days = state.days,
            selectedDate = state.selectedDate,
            refreshing = state.refreshingMonth,
            retentionCutoffDate = state.retentionCutoffDate,
            retentionDays = state.retentionDays,
            onMonth = { scope.launch { repository.loadMonth(selectedPatient.id, it) } },
            onDate = { repository.selectDate(it); showDetail = true },
            reportAction = if (reportRepository != null) {
                { CaregiverReportAction(reportRepository, selectedPatient.id, billingEnabled) }
            } else null,
        )
    }

    if (showDetail && selectedPatient != null && state.selectedDate != null) {
        ModalBottomSheet(onDismissRequest = { showDetail = false }, modifier = Modifier.testTag("caregiver-history-day-sheet")) {
            if (state.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.mutationFailed) Text(stringResource(R.string.caregiver_history_backfill_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            if (state.mutationSucceeded) Text(stringResource(R.string.caregiver_history_backfill_success), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold)
            HistoryDayDetailContent(
                date = state.selectedDate!!,
                detail = state.dayDetail,
                loading = state.loadingDay,
                error = if (state.dayFailed) stringResource(R.string.caregiver_data_unavailable_message) else null,
                retentionCutoffDate = state.retentionCutoffDate,
                retentionDays = state.retentionDays,
                onRetry = { scope.launch { repository.loadDay(selectedPatient.id, state.selectedDate!!) } },
                highlightedSlot = state.highlightedSlot,
                onRecordMissed = { backfillDose = it },
            )
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
    refreshing: Boolean,
    retentionCutoffDate: String?,
    retentionDays: Int?,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
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
        if (refreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
        item { CaregiverCalendar(displayedMonth, days, selectedDate, onDate) }
        item { HistoryLegendRow() }
        reportAction?.let { item { it() } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CaregiverCalendar(yearMonth: YearMonth, days: List<HistoryDay>, selectedDate: LocalDate?, onDate: (LocalDate) -> Unit) {
    val byDate = days.associateBy(HistoryDay::date)
    val leading = yearMonth.atDay(1).dayOfWeek.value % 7
    val cells = (List<LocalDate?>(leading) { null } + (1..yearMonth.lengthOfMonth()).map(yearMonth::atDay)).let {
        it + List((7 - it.size % 7) % 7) { null }
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

@Composable
private fun CaregiverCalendarDay(date: LocalDate, day: HistoryDay?, selected: Boolean, modifier: Modifier, onDate: (LocalDate) -> Unit) {
    val statuses = day?.let { listOf(it.morning, it.noon, it.evening, it.bedtime) }.orEmpty()
    Column(
        modifier.height(54.dp).padding(2.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onDate(date) }.testTag("caregiver-history-day-$date"),
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
private fun HistoryMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    HistoryCentered { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center); action?.invoke() }
}

@Composable
private fun HistoryCentered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
}
