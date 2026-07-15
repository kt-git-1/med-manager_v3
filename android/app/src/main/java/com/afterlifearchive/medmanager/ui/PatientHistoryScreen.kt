package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.HistoryStreakTodayStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PrnActorType
import com.afterlifearchive.medmanager.data.patient.PrnHistoryItem
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.data.patient.PatientHistoryStreak
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HistoryContent(
    days: List<HistoryDay>,
    loading: Boolean,
    error: String?,
    retentionCutoffDate: String?,
    retentionDays: Int?,
    onRetry: () -> Unit,
    now: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo")),
    streak: PatientHistoryStreak? = null,
) {
    val byDate = days.associateBy(HistoryDay::date)
    val today = byDate[now.toString()]
    val weekDates = patientHistoryWeekDates(now)
    val weeklyRecorded = weekDates.count { patientSimpleStatus(byDate[it.toString()]) == PatientSimpleHistoryStatus.TAKEN }
    val consecutiveTaken = patientHistoryConsecutiveTaken(weekDates, now, byDate)

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("patient-history-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PatientHistoryHeader() }
        if (loading && days.isEmpty()) item { PatientHistoryLoadingState() }
        if (error != null) item { PatientHistoryErrorState(error, onRetry) }
        if (retentionCutoffDate != null) item { RetentionLockCard(retentionCutoffDate, retentionDays ?: 30) }
        if (!loading && error == null && retentionCutoffDate == null) {
            item { PatientTodayProgressCard(today) }
            streak?.let { item { PatientHistoryStreakCard(it) } }
            item { PatientWeekHistoryCard(weekDates, byDate, weeklyRecorded, consecutiveTaken, now) }
            item { Text(stringResource(R.string.patient_history_recent_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            items(listOf(now, now.minusDays(1)), key = LocalDate::toString) { date ->
                PatientRecentHistoryCard(date, byDate[date.toString()], now)
            }
        }
        item { Spacer(Modifier.height(104.dp)) }
    }
}

@Composable
private fun PatientHistoryStreakCard(streak: PatientHistoryStreak) {
    val value = when {
        streak.currentStreakDays == 0 -> stringResource(R.string.patient_history_streak_start_value)
        streak.isAtLeast -> stringResource(R.string.patient_history_streak_days_at_least, streak.currentStreakDays)
        else -> stringResource(R.string.patient_history_streak_days, streak.currentStreakDays)
    }
    val valueAccessibility = stringResource(R.string.patient_history_streak_accessibility, value)
    val achievement = when (streak.currentStreakDays) {
        0 -> stringResource(R.string.patient_history_streak_start)
        1 -> stringResource(R.string.patient_history_streak_first)
        3 -> stringResource(R.string.patient_history_streak_milestone_3)
        7 -> stringResource(R.string.patient_history_streak_milestone_7)
        14 -> stringResource(R.string.patient_history_streak_milestone_14)
        30 -> stringResource(R.string.patient_history_streak_milestone_30)
        else -> stringResource(R.string.patient_history_streak_continuing, streak.currentStreakDays)
    }
    val next = when {
        streak.currentStreakDays == 0 && streak.todayStatus == HistoryStreakTodayStatus.MISSED -> stringResource(R.string.patient_history_streak_next_restart)
        streak.currentStreakDays == 0 && streak.todayStatus == HistoryStreakTodayStatus.NO_SCHEDULE -> stringResource(R.string.patient_history_streak_next_no_schedule)
        streak.currentStreakDays == 0 -> stringResource(R.string.patient_history_streak_next_start)
        streak.todayStatus == HistoryStreakTodayStatus.COMPLETE -> stringResource(R.string.patient_history_streak_next_tomorrow, streak.currentStreakDays + 1)
        streak.todayStatus == HistoryStreakTodayStatus.IN_PROGRESS -> stringResource(R.string.patient_history_streak_next_today, streak.currentStreakDays + 1)
        streak.todayStatus == HistoryStreakTodayStatus.MISSED -> stringResource(R.string.patient_history_streak_next_restart)
        else -> stringResource(R.string.patient_history_streak_next_no_schedule)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-history-streak-card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PatientTeal.copy(alpha = 0.18f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).background(PatientTeal, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(stringResource(R.string.patient_history_streak_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    value,
                    fontSize = if (streak.currentStreakDays == 0) 28.sp else 50.sp,
                    color = PatientTeal,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = valueAccessibility },
                )
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Verified, contentDescription = null, tint = PatientTeal)
                Text(achievement, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Text(
                next,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PatientTeal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(PatientTeal.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PatientHistoryLoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("patient-history-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = PatientTeal)
        Text(
            stringResource(R.string.patient_today_loading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PatientHistoryErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("patient-history-error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(onClick = onRetry, modifier = Modifier.testTag("patient-history-retry")) {
            Text(stringResource(R.string.patient_detail_retry))
        }
    }
}

private enum class PatientSimpleHistoryStatus { TAKEN, PENDING, MISSED, NONE }

@Composable
private fun PatientHistoryHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PatientHeaderIcon(Icons.Rounded.History)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.patient_history_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.patient_history_subtitle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PatientTodayProgressCard(day: HistoryDay?) {
    val statuses = patientActiveStatuses(day)
    val total = statuses.size
    val taken = statuses.count { it == HistoryStatus.TAKEN }
    val pending = statuses.count { it == HistoryStatus.PENDING }
    val missed = statuses.count { it == HistoryStatus.MISSED }
    val fraction = if (total == 0) 0f else taken.toFloat() / total
    val accent = when {
        missed > 0 -> MaterialTheme.colorScheme.error
        total > 0 && taken == total -> PatientTeal
        taken > 0 -> Color(0xFFF36A00)
        else -> Color(0xFF3478F6)
    }
    val title = if (total == 0) stringResource(R.string.patient_history_today_progress_no_schedule)
    else stringResource(R.string.patient_history_today_progress_format, taken, total)
    val encouragement = stringResource(
        when {
            total == 0 -> R.string.patient_history_encouragement_no_schedule
            missed > 0 -> R.string.patient_history_encouragement_missed
            taken == total && pending == 0 -> R.string.patient_history_encouragement_complete
            taken > 0 -> R.string.patient_history_encouragement_partial
            else -> R.string.patient_history_encouragement_start
        },
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.16f),
                    strokeWidth = 10.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (total == 0) "--" else "$taken/$total", fontWeight = FontWeight.Bold, color = accent, fontSize = 24.sp)
                    Text(stringResource(R.string.patient_history_today_progress_unit), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.patient_history_today_progress_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(encouragement, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryStatusPill(stringResource(R.string.patient_history_today_taken, taken), PatientTeal, Icons.Rounded.CheckCircle)
                    if (pending > 0) HistoryStatusPill(stringResource(R.string.patient_history_today_remaining, pending), Color(0xFFF36A00), Icons.Rounded.AccessTime)
                    if (missed > 0) HistoryStatusPill(stringResource(R.string.patient_history_today_missed, missed), MaterialTheme.colorScheme.error, Icons.Rounded.Warning)
                }
            }
        }
    }
}

@Composable
private fun PatientWeekHistoryCard(
    dates: List<LocalDate>,
    byDate: Map<String, HistoryDay>,
    recordedCount: Int,
    consecutiveTaken: Int,
    now: LocalDate,
) {
    val encouragement = stringResource(
        when {
            consecutiveTaken >= 3 -> R.string.patient_history_week_streak_strong
            consecutiveTaken >= 2 -> R.string.patient_history_week_streak
            recordedCount >= 5 -> R.string.patient_history_week_many
            recordedCount > 0 -> R.string.patient_history_week_some
            else -> R.string.patient_history_week_start
        },
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, PatientTeal.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.patient_history_week_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.patient_history_week_count, recordedCount), fontSize = 50.sp, color = PatientTeal, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.patient_history_week_recorded), style = MaterialTheme.typography.titleLarge, color = PatientTeal, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dates.forEach { date -> PatientWeekDay(date, byDate[date.toString()], now, Modifier.weight(1f)) }
            }
            Text(encouragement, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PatientWeekDay(date: LocalDate, day: HistoryDay?, now: LocalDate, modifier: Modifier = Modifier) {
    val status = patientSimpleStatus(day)
    val isUpcoming = date > now && status == PatientSimpleHistoryStatus.PENDING
    val accent = patientSimpleStatusColor(status, isUpcoming)
    val icon = when (status) {
        PatientSimpleHistoryStatus.TAKEN -> Icons.Rounded.CheckCircle
        PatientSimpleHistoryStatus.PENDING -> Icons.Rounded.AccessTime
        PatientSimpleHistoryStatus.MISSED -> Icons.Rounded.Warning
        PatientSimpleHistoryStatus.NONE -> Icons.Rounded.Remove
    }
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.JAPANESE)
    val dateText = "${date.monthValue}月${date.dayOfMonth}日"
    val accessibilityText = stringResource(R.string.patient_history_week_day_accessibility, weekday, dateText, patientSimpleStatusText(status))
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = accessibilityText },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(weekday, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(34.dp).background(if (status == PatientSimpleHistoryStatus.NONE || isUpcoming) accent.copy(alpha = 0.14f) else accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (status == PatientSimpleHistoryStatus.NONE || isUpcoming) accent else Color.White)
        }
        Text("${date.monthValue}/${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PatientRecentHistoryCard(date: LocalDate, day: HistoryDay?, now: LocalDate) {
    val status = patientSimpleStatus(day)
    val accent = patientSimpleStatusColor(status, false)
    val icon = when (status) {
        PatientSimpleHistoryStatus.TAKEN -> Icons.Rounded.CheckCircle
        PatientSimpleHistoryStatus.PENDING -> Icons.Rounded.AccessTime
        PatientSimpleHistoryStatus.MISSED -> Icons.Rounded.Warning
        PatientSimpleHistoryStatus.NONE -> Icons.Rounded.CalendarMonth
    }
    val formatted = date.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
    val title = stringResource(if (date == now) R.string.patient_history_today_format else R.string.patient_history_yesterday_format, formatted)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(patientHistorySubtitle(day), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            HistoryStatusPill(
                patientSimpleStatusText(status),
                accent,
                when (status) {
                    PatientSimpleHistoryStatus.TAKEN -> Icons.Rounded.CheckCircle
                    PatientSimpleHistoryStatus.MISSED -> Icons.Rounded.Warning
                    PatientSimpleHistoryStatus.PENDING, PatientSimpleHistoryStatus.NONE -> null
                },
            )
        }
    }
}

@Composable
private fun HistoryStatusPill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.13f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) }
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

private fun patientActiveStatuses(day: HistoryDay?): List<HistoryStatus> = day?.let {
    listOf(it.morning, it.noon, it.evening, it.bedtime).filter { status -> status != HistoryStatus.NONE }
}.orEmpty()

private fun patientSimpleStatus(day: HistoryDay?): PatientSimpleHistoryStatus {
    val statuses = patientActiveStatuses(day)
    return when {
        HistoryStatus.MISSED in statuses -> PatientSimpleHistoryStatus.MISSED
        HistoryStatus.PENDING in statuses -> PatientSimpleHistoryStatus.PENDING
        HistoryStatus.TAKEN in statuses || (day?.prnCount ?: 0) > 0 -> PatientSimpleHistoryStatus.TAKEN
        else -> PatientSimpleHistoryStatus.NONE
    }
}

@Composable
private fun patientSimpleStatusColor(status: PatientSimpleHistoryStatus, upcoming: Boolean): Color = when (status) {
    PatientSimpleHistoryStatus.TAKEN -> PatientTeal
    PatientSimpleHistoryStatus.PENDING -> if (upcoming) Color(0xFF3478F6) else Color(0xFFF36A00)
    PatientSimpleHistoryStatus.MISSED -> MaterialTheme.colorScheme.error
    PatientSimpleHistoryStatus.NONE -> MaterialTheme.colorScheme.outline
}

@Composable
private fun patientSimpleStatusText(status: PatientSimpleHistoryStatus): String = stringResource(when (status) {
    PatientSimpleHistoryStatus.TAKEN -> R.string.patient_history_simple_done
    PatientSimpleHistoryStatus.PENDING -> R.string.patient_history_simple_pending
    PatientSimpleHistoryStatus.MISSED -> R.string.patient_history_simple_missed
    PatientSimpleHistoryStatus.NONE -> R.string.patient_history_simple_none
})

@Composable
private fun patientHistorySubtitle(day: HistoryDay?): String {
    if (day == null) return stringResource(R.string.patient_history_no_medication_schedule)
    val slots = buildList {
        if (day.morning != HistoryStatus.NONE) add(stringResource(R.string.patient_history_accessibility_slot_morning))
        if (day.noon != HistoryStatus.NONE) add(stringResource(R.string.patient_history_accessibility_slot_noon))
        if (day.evening != HistoryStatus.NONE) add(stringResource(R.string.patient_history_accessibility_slot_evening))
        if (day.bedtime != HistoryStatus.NONE) add(stringResource(R.string.patient_history_accessibility_slot_bedtime))
    }
    return when {
        slots.isNotEmpty() -> stringResource(R.string.patient_history_slots_format, slots.joinToString("・"))
        day.prnCount > 0 -> stringResource(R.string.patient_history_prn_only)
        else -> stringResource(R.string.patient_history_no_medication_schedule)
    }
}

private fun patientHistoryWeekDates(now: LocalDate): List<LocalDate> {
    val monday = now.minusDays(((now.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
    return (0L..6L).map(monday::plusDays)
}

private fun patientHistoryConsecutiveTaken(
    weekDates: List<LocalDate>,
    now: LocalDate,
    byDate: Map<String, HistoryDay>,
): Int {
    var streak = 0
    for (date in weekDates.filter { it <= now }.asReversed()) {
        if (patientSimpleStatus(byDate[date.toString()]) == PatientSimpleHistoryStatus.TAKEN) streak += 1 else break
    }
    return streak
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
    highlightedSlot: MedicationSlot? = null,
    onRecordMissed: ((HistoryScheduledDose) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("history-day-detail-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(date.format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_history_day_date_pattern), Locale.JAPANESE)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (loading) item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("history-day-detail-loading"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = PatientTeal)
                Text(stringResource(R.string.patient_today_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (retentionCutoffDate != null) item { RetentionLockCard(retentionCutoffDate, retentionDays ?: 30) }
        if (error != null) item { PatientNoticeCard(stringResource(R.string.patient_history_day_error), MaterialTheme.colorScheme.errorContainer, onRetry) }
        if (!loading && error == null && retentionCutoffDate == null && detail != null) {
            if (detail.doses.isEmpty() && detail.prnItems.isEmpty()) {
                item { PatientDetailCard(stringResource(R.string.patient_history_day_empty_title), stringResource(R.string.patient_history_day_empty_message)) }
            }
            items(
                patientHistoryTimelineItems(detail),
                key = PatientHistoryTimelineItem::key,
            ) { item ->
                when (item) {
                    is PatientHistoryTimelineItem.Scheduled -> HistoryScheduledDoseRow(item.dose, highlightedSlot == item.dose.slot, onRecordMissed)
                    is PatientHistoryTimelineItem.Prn -> PrnHistoryRow(item.item)
                }
            }
        }
    }
}

@Composable
internal fun HistoryScheduledDoseRow(
    dose: HistoryScheduledDose,
    highlighted: Boolean = false,
    onRecordMissed: ((HistoryScheduledDose) -> Unit)? = null,
    style: HistoryDayRowStyle = HistoryDayRowStyle.PATIENT,
) {
    val isPatientStyle = style == HistoryDayRowStyle.PATIENT
    val slotColor = historySlotColor(dose.slot)
    val statusColor = historyDoseColor(dose.status)
    val dosage = dose.dosageText.trim()
    val displayName = if (dosage.isEmpty() || dosage == "不明") dose.medicationName else "${dose.medicationName} $dosage"
    Card(
        colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = when {
            highlighted -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            !isPatientStyle -> null
            dose.status == DoseStatus.MISSED -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.30f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        },
        shape = RoundedCornerShape(if (isPatientStyle) 18.dp else 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPatientStyle) 4.dp else 0.dp),
        modifier = Modifier.testTag(
            if (highlighted) "history-dose-highlighted-${dose.slot.name.lowercase()}"
            else "history-dose-${dose.slot.name.lowercase()}",
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(if (isPatientStyle) 16.dp else 14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(historyTime(dose.scheduledAt), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        patientSlotTitle(dose.slot),
                        modifier = Modifier.background(slotColor.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                        color = slotColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                dose.recordedByType?.let {
                    Text(
                        stringResource(if (it == RecordedByType.PATIENT) R.string.patient_history_recorded_by_patient else R.string.patient_history_recorded_by_caregiver),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPatientStyle) PatientTeal else MedicationTheme.colors.primaryTealText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (isPatientStyle) {
                Text(
                    patientDoseStatusText(dose.status),
                    modifier = Modifier.background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Box(
                    modifier = Modifier.size(36.dp).background(statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when (dose.status) {
                            DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                            DoseStatus.MISSED -> Icons.Rounded.Warning
                            DoseStatus.PENDING -> Icons.Rounded.AccessTime
                        },
                        contentDescription = patientDoseStatusText(dose.status),
                        tint = statusColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (dose.status == DoseStatus.MISSED && onRecordMissed != null) {
            if (isPatientStyle) {
                TextButton(
                    onClick = { onRecordMissed(dose) },
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp).testTag("history-backfill-${dose.medicationId}"),
                ) { Text(stringResource(R.string.caregiver_history_backfill_action)) }
            } else {
                Button(
                    onClick = { onRecordMissed(dose) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).height(46.dp).testTag("history-backfill-${dose.medicationId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.caregiver_history_backfill_action), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun PrnHistoryRow(item: PrnHistoryItem, style: HistoryDayRowStyle = HistoryDayRowStyle.PATIENT) {
    val isPatientStyle = style == HistoryDayRowStyle.PATIENT
    val prnColor = if (isPatientStyle) MedicationTheme.colors.indigo else Color(0xFF8E24AA)
    val recordedBy = stringResource(
        if (item.actorType == PrnActorType.PATIENT) R.string.patient_history_recorded_by_patient
        else R.string.patient_history_recorded_by_caregiver,
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isPatientStyle) BorderStroke(1.dp, prnColor.copy(alpha = 0.26f)) else null,
        shape = RoundedCornerShape(if (isPatientStyle) 18.dp else 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPatientStyle) 4.dp else 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(if (isPatientStyle) 16.dp else 14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(historyTime(item.takenAt), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.patient_history_prn_name_format, stringResource(R.string.patient_history_prn_section), item.medicationName), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(recordedBy, color = if (isPatientStyle) PatientTeal else MedicationTheme.colors.primaryTealText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                stringResource(R.string.patient_history_prn_section),
                modifier = Modifier.background(prnColor.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
                color = prnColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal enum class HistoryDayRowStyle { PATIENT, CAREGIVER }

internal fun patientHistoryTimelineItems(detail: HistoryDayDetail): List<PatientHistoryTimelineItem> =
    (detail.doses.map { PatientHistoryTimelineItem.Scheduled(it) } +
        detail.prnItems.map { PatientHistoryTimelineItem.Prn(it) })
        .sortedWith(compareBy<PatientHistoryTimelineItem>({ it.instant }, { it.sortName }))

internal sealed interface PatientHistoryTimelineItem {
    val instant: Instant
    val sortName: String
    val key: String

    data class Scheduled(val dose: HistoryScheduledDose) : PatientHistoryTimelineItem {
        override val instant = dose.scheduledAt
        override val sortName = dose.medicationName
        override val key = "scheduled:${dose.medicationId}:${dose.scheduledAt}"
    }

    data class Prn(val item: PrnHistoryItem) : PatientHistoryTimelineItem {
        override val instant = item.takenAt
        override val sortName = item.medicationName
        override val key = "prn:${item.medicationId}:${item.takenAt}"
    }
}

@Composable
private fun historyDoseColor(status: DoseStatus): Color = when (status) {
    DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
    DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    DoseStatus.PENDING -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun historySlotColor(slot: MedicationSlot): Color = when (slot) {
    MedicationSlot.MORNING -> MedicationTheme.colors.slotMorning
    MedicationSlot.NOON -> MedicationTheme.colors.slotNoon
    MedicationSlot.EVENING -> MedicationTheme.colors.slotEvening
    MedicationSlot.BEDTIME -> MedicationTheme.colors.slotBedtime
}

private fun historyTime(instant: Instant) = instant.atZone(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("HH:mm"))
