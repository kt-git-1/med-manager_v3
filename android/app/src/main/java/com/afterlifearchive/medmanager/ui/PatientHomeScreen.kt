package com.afterlifearchive.medmanager.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.ReminderScheduler
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PatientTeal = Color(0xFF148C83)
private val PatientBackground = Color(0xFFF3FAF8)

private enum class PatientTab(val title: String, val symbol: String) {
    TODAY("きょう", "●"),
    HISTORY("履歴", "◷"),
    SETTINGS("設定", "⚙"),
}

@Composable
fun PatientModePreview() {
    val previewDoses = remember {
        listOf(
            PatientDose("preview-1", "med-1", Instant.now().minusSeconds(600), DoseStatus.PENDING, "朝のお薬", "1回1錠", 1.0),
            PatientDose("preview-2", "med-2", Instant.now().plusSeconds(300), DoseStatus.PENDING, "血圧のお薬", "1回1錠", 1.0),
            PatientDose("preview-3", "med-3", Instant.now().plusSeconds(14_400), DoseStatus.TAKEN, "夕方のお薬", "1回2錠", 2.0),
        )
    }
    Scaffold(
        containerColor = PatientBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                PatientTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == PatientTab.TODAY,
                        onClick = {},
                        icon = { Text(item.symbol, color = if (item == PatientTab.TODAY) PatientTeal else Color.Gray) },
                        label = { Text(item.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PatientTeal,
                            selectedTextColor = PatientTeal,
                            indicatorColor = Color(0xFFDDF4EC),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).safeDrawingPadding()) {
            TodayContent(
                doses = previewDoses,
                loading = false,
                updatingKey = null,
                error = null,
                message = null,
                onRetry = {},
                onRecord = {},
                onRemind = {},
            )
        }
    }
}

@Composable
fun PatientHomeScreen(repository: PatientRepository, onUnlink: () -> Unit) {
    val state by repository.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(PatientTab.TODAY) }
    var confirmDose by remember { mutableStateOf<PatientDose?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(tab) {
        when (tab) {
            PatientTab.TODAY -> repository.loadToday()
            PatientTab.HISTORY -> repository.loadHistory()
            PatientTab.SETTINGS -> Unit
        }
    }

    Scaffold(
        containerColor = PatientBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                PatientTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.symbol, color = if (tab == item) PatientTeal else Color.Gray) },
                        label = { Text(item.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PatientTeal,
                            selectedTextColor = PatientTeal,
                            indicatorColor = Color(0xFFDDF4EC),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).safeDrawingPadding()) {
            when (tab) {
                PatientTab.TODAY -> TodayContent(
                    doses = state.doses,
                    loading = state.loading,
                    updatingKey = state.updatingDoseKey,
                    error = state.error,
                    message = state.message,
                    onRetry = { scope.launch { repository.loadToday() } },
                    onRecord = { confirmDose = it },
                    onRemind = { dose ->
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        ReminderScheduler.schedule(context, dose.key, dose.medicationName)
                    },
                )
                PatientTab.HISTORY -> HistoryContent(state.history, state.loading, state.error) {
                    scope.launch { repository.loadHistory() }
                }
                PatientTab.SETTINGS -> SettingsContent(
                    loading = state.loading,
                    error = state.error,
                    onUnlink = {
                        scope.launch {
                            if (repository.revokeSession()) onUnlink()
                        }
                    },
                )
            }
        }
    }

    confirmDose?.let { dose ->
        AlertDialog(
            onDismissRequest = { confirmDose = null },
            title = { Text("服薬を記録しますか？") },
            text = { Text("${dose.medicationName}を「飲みました」として記録します。") },
            confirmButton = {
                Button(onClick = {
                    confirmDose = null
                    scope.launch { repository.record(dose) }
                }) { Text("記録する") }
            },
            dismissButton = { TextButton(onClick = { confirmDose = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun TodayContent(
    doses: List<PatientDose>,
    loading: Boolean,
    updatingKey: String?,
    error: String?,
    message: String?,
    onRetry: () -> Unit,
    onRecord: (PatientDose) -> Unit,
    onRemind: (PatientDose) -> Unit,
) {
    val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    val date = today.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("今日のお薬", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(date, color = Color.DarkGray, style = MaterialTheme.typography.titleMedium)
        }
        if (loading && doses.isEmpty()) item { CenteredProgress() }
        error?.let { item { NoticeCard(it, MaterialTheme.colorScheme.errorContainer, onRetry) } }
        message?.let { item { NoticeCard(it, Color(0xFFDDF4EC), null) } }
        if (!loading && error == null && doses.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✓", color = PatientTeal, style = MaterialTheme.typography.displaySmall)
                        Text("今日のお薬はありません", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("ゆっくりお過ごしください", color = Color.Gray)
                    }
                }
            }
        }
        doses.groupBy(::slotLabel).forEach { (slot, slotDoses) ->
            item { Text(slot, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PatientTeal) }
            items(slotDoses, key = PatientDose::key) { dose ->
                DoseCard(dose, updatingKey == dose.key, onRecord, onRemind)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DoseCard(
    dose: PatientDose,
    updating: Boolean,
    onRecord: (PatientDose) -> Unit,
    onRemind: (PatientDose) -> Unit,
) {
    val taken = dose.status == DoseStatus.TAKEN
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (taken) Color(0xFFE7F5F1) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(dose.medicationName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(dose.dosageText, color = Color.DarkGray)
                    Text(timeText(dose), color = PatientTeal, fontWeight = FontWeight.SemiBold)
                }
                Text(if (taken) "服用済み" else "未服用", color = if (taken) PatientTeal else Color(0xFFC44B32), fontWeight = FontWeight.Bold)
            }
            if (!taken) {
                Spacer(Modifier.height(18.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !updating,
                    onClick = { onRecord(dose) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PatientTeal),
                ) { Text(if (updating) "記録中…" else "飲みました", style = MaterialTheme.typography.titleMedium) }
                TextButton(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = { onRemind(dose) }) {
                    Text("10分後に知らせる", color = PatientTeal)
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(days: List<HistoryDay>, loading: Boolean, error: String?, onRetry: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("服薬履歴", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("今月の記録", color = Color.DarkGray)
        }
        if (loading && days.isEmpty()) item { CenteredProgress() }
        error?.let { item { NoticeCard(it, MaterialTheme.colorScheme.errorContainer, onRetry) } }
        items(days.asReversed(), key = HistoryDay::date) { day -> HistoryDayCard(day) }
    }
}

@Composable
private fun HistoryDayCard(day: HistoryDay) {
    val statuses = listOf(day.morning, day.noon, day.evening, day.bedtime).filter { it != HistoryStatus.NONE }
    val taken = statuses.count { it == HistoryStatus.TAKEN }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day.date.drop(5).replace('-', '/'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(if (statuses.isEmpty()) "予定なし" else "$taken / ${statuses.size} 回", color = if (taken == statuses.size && statuses.isNotEmpty()) PatientTeal else Color.DarkGray)
            if (day.prnCount > 0) Text("  頓服${day.prnCount}回", color = PatientTeal)
        }
    }
}

@Composable
private fun SettingsContent(loading: Boolean, error: String?, onUnlink: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("通知", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("服薬カードの「10分後に知らせる」から通知を設定できます。", color = Color.DarkGray)
            }
        }
        Spacer(Modifier.height(20.dp))
        error?.let {
            NoticeCard(it, MaterialTheme.colorScheme.errorContainer, null)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onUnlink) {
            Text(if (loading) "解除中…" else "この端末の連携を解除")
        }
    }
}

@Composable
private fun NoticeCard(text: String, color: Color, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().background(color, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(text)
        onRetry?.let { TextButton(onClick = it) { Text("もう一度試す") } }
    }
}

@Composable
private fun CenteredProgress() = Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = PatientTeal)
}

private fun timeText(dose: PatientDose): String = dose.scheduledAt
    .atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern("H:mm"))

private fun slotLabel(dose: PatientDose): String = when (dose.scheduledAt.atZone(ZoneId.of("Asia/Tokyo")).hour) {
    in 4..10 -> "朝のお薬"
    in 11..14 -> "昼のお薬"
    in 15..20 -> "夕方のお薬"
    else -> "寝る前のお薬"
}
