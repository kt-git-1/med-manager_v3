package com.afterlifearchive.medmanager.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.PrnActorType
import com.afterlifearchive.medmanager.data.patient.PrnHistoryItem
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HistoryContent(
    days: List<HistoryDay>,
    yearMonth: YearMonth,
    loading: Boolean,
    error: String?,
    retentionCutoffDate: String?,
    retentionDays: Int?,
    onPreviousMonth: (YearMonth) -> Unit,
    onNextMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.patient_history_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onPreviousMonth(yearMonth) }, enabled = !loading) { Text(stringResource(R.string.patient_history_previous_month)) }
            Text(stringResource(R.string.patient_history_month, yearMonth.year, yearMonth.monthValue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onNextMonth(yearMonth) }, enabled = !loading) { Text(stringResource(R.string.patient_history_next_month)) }
        }
        HistoryLegend()
        if (loading && days.isEmpty()) PatientCenteredProgress()
        if (error != null) PatientNoticeCard(error, MaterialTheme.colorScheme.errorContainer, onRetry)
        if (retentionCutoffDate != null) RetentionLockCard(retentionCutoffDate, retentionDays ?: 30)
        if (!loading && error == null && retentionCutoffDate == null) HistoryCalendar(yearMonth, days, onSelectDate)
    }
}

@Composable
private fun HistoryLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendItem(stringResource(R.string.patient_status_taken), MaterialTheme.colorScheme.primary)
        LegendItem(stringResource(R.string.patient_status_missed), MaterialTheme.colorScheme.error)
        LegendItem(stringResource(R.string.patient_status_pending), MaterialTheme.colorScheme.tertiary)
        LegendItem(stringResource(R.string.patient_history_no_schedule), MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.height(9.dp).fillMaxWidth(0.025f).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HistoryCalendar(yearMonth: YearMonth, days: List<HistoryDay>, onSelectDate: (LocalDate) -> Unit) {
    val byDate = days.associateBy(HistoryDay::date)
    val offset = yearMonth.atDay(1).dayOfWeek.value % 7
    val cells: List<LocalDate?> = List(offset) { null } + (1..yearMonth.lengthOfMonth()).map(yearMonth::atDay)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            stringResource(R.string.patient_history_weekdays).split(',').forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
            gridItems(cells) { date ->
                if (date == null) Spacer(Modifier.height(68.dp)) else HistoryCalendarCell(date, byDate[date.toString()], onSelectDate)
            }
        }
    }
}

@Composable
private fun HistoryCalendarCell(date: LocalDate, day: HistoryDay?, onSelectDate: (LocalDate) -> Unit) {
    val statuses = day?.let { listOf(it.morning, it.noon, it.evening, it.bedtime) }.orEmpty()
    val dayDescription = historyDayDescription(date, day)
    Column(
        Modifier.height(68.dp).padding(2.dp)
            .semantics { contentDescription = dayDescription }
            .clickable { onSelectDate(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(date.dayOfMonth.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            statuses.forEach { status -> Box(Modifier.size(6.dp).background(historyStatusColor(status), CircleShape)) }
        }
        if ((day?.prnCount ?: 0) > 0) Text(stringResource(R.string.patient_history_prn_count, day?.prnCount ?: 0), style = MaterialTheme.typography.labelSmall, color = PatientTeal)
    }
}

@Composable
private fun historyDayDescription(date: LocalDate, day: HistoryDay?): String {
    val dateText = date.format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_history_accessibility_date_pattern), Locale.JAPANESE))
    val separator = "、"
    val noSchedule = stringResource(R.string.patient_history_no_schedule)
    if (day == null) return dateText + separator + noSchedule
    val taken = stringResource(R.string.patient_status_taken)
    val missed = stringResource(R.string.patient_status_missed)
    val pending = stringResource(R.string.patient_status_pending)
    fun status(name: String, value: HistoryStatus) = "$name${when (value) {
        HistoryStatus.TAKEN -> taken
        HistoryStatus.MISSED -> missed
        HistoryStatus.PENDING -> pending
        HistoryStatus.NONE -> noSchedule
    }}"
    val slots = listOf(
        status(stringResource(R.string.patient_history_accessibility_slot_morning), day.morning),
        status(stringResource(R.string.patient_history_accessibility_slot_noon), day.noon),
        status(stringResource(R.string.patient_history_accessibility_slot_evening), day.evening),
        status(stringResource(R.string.patient_history_accessibility_slot_bedtime), day.bedtime),
    )
    val prnDescription = if (day.prnCount > 0) stringResource(R.string.patient_history_accessibility_prn, day.prnCount) else null
    return buildString {
        append(dateText).append(separator).append(slots.joinToString(separator))
        prnDescription?.let { append(separator).append(it) }
    }
}

@Composable
private fun historyStatusColor(status: HistoryStatus): Color = when (status) {
    HistoryStatus.TAKEN -> MaterialTheme.colorScheme.primary
    HistoryStatus.MISSED -> MaterialTheme.colorScheme.error
    HistoryStatus.PENDING -> MaterialTheme.colorScheme.tertiary
    HistoryStatus.NONE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
}

@Composable
private fun RetentionLockCard(cutoffDate: String, retentionDays: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.patient_history_locked_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.patient_history_locked_message, retentionDays, cutoffDate), textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun HistoryDayDetailContent(
    date: LocalDate,
    detail: HistoryDayDetail?,
    loading: Boolean,
    error: String?,
    retentionCutoffDate: String?,
    retentionDays: Int?,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(date.format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_today_date_pattern), Locale.JAPANESE)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (loading) item { PatientCenteredProgress() }
        if (retentionCutoffDate != null) item { RetentionLockCard(retentionCutoffDate, retentionDays ?: 30) }
        if (error != null) item { PatientNoticeCard(error, MaterialTheme.colorScheme.errorContainer, onRetry) }
        if (!loading && error == null && retentionCutoffDate == null && detail != null) {
            if (detail.doses.isEmpty() && detail.prnItems.isEmpty()) {
                item { PatientDetailCard(stringResource(R.string.patient_history_day_empty_title), stringResource(R.string.patient_history_day_empty_message)) }
            }
            if (detail.doses.isNotEmpty()) item { Text(stringResource(R.string.patient_history_scheduled_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(detail.doses, key = { "${it.medicationId}:${it.scheduledAt}" }) { HistoryScheduledDoseRow(it) }
            if (detail.prnItems.isNotEmpty()) item { Text(stringResource(R.string.patient_history_prn_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(detail.prnItems, key = { "${it.medicationId}:${it.takenAt}" }) { PrnHistoryRow(it) }
        }
    }
}

@Composable
private fun HistoryScheduledDoseRow(dose: HistoryScheduledDose) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dose.medicationName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${historyTime(dose.scheduledAt)}　${dose.dosageText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                dose.recordedByType?.let {
                    Text(stringResource(if (it == RecordedByType.PATIENT) R.string.patient_history_recorded_by_patient else R.string.patient_history_recorded_by_caregiver), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(patientDoseStatusText(dose.status), color = historyDoseColor(dose.status), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrnHistoryRow(item: PrnHistoryItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.medicationName, fontWeight = FontWeight.Bold)
                Text("${historyTime(item.takenAt)}　${stringResource(R.string.patient_tablet_amount, formatPatientAmount(item.quantityTaken))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(if (item.actorType == PrnActorType.PATIENT) R.string.patient_actor_patient else R.string.patient_actor_caregiver), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun historyDoseColor(status: DoseStatus): Color = when (status) {
    DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
    DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    DoseStatus.PENDING -> MaterialTheme.colorScheme.tertiary
}

private fun historyTime(instant: Instant) = instant.atZone(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("H:mm"))
