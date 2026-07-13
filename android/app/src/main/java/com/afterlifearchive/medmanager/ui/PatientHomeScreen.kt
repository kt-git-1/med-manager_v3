package com.afterlifearchive.medmanager.ui

import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.afterlifearchive.medmanager.ReminderScheduler
import com.afterlifearchive.medmanager.PatientNotificationPreferences
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.PatientNotificationPlanBuilder
import com.afterlifearchive.medmanager.PatientNotificationScheduler
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.PrnHistoryItem
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PatientTeal: Color @Composable get() = MaterialTheme.colorScheme.primary
private val PatientBackground: Color @Composable get() = MaterialTheme.colorScheme.background

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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                PatientTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == PatientTab.TODAY,
                        onClick = {},
                        icon = { Text(item.symbol, color = if (item == PatientTab.TODAY) PatientTeal else MaterialTheme.colorScheme.onSurfaceVariant) },
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
                medications = emptyMap(),
                nextSlot = MedicationSlot.MORNING,
                updatingSlot = null,
                prnMedications = emptyList(),
                updatingPrnMedicationId = null,
                onRetry = {},
                onRecord = {},
                onDetail = {},
                onRecordSlot = {},
                onRecordPrn = {},
                onRemind = {},
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PatientHomeScreen(repository: PatientRepository, onUnlink: () -> Unit) {
    val state by repository.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(PatientTab.TODAY) }
    var confirmDose by remember { mutableStateOf<PatientDose?>(null) }
    var confirmPrn by remember { mutableStateOf<PatientMedication?>(null) }
    var confirmSlot by remember { mutableStateOf<MedicationSlot?>(null) }
    var selectedDose by remember { mutableStateOf<PatientDose?>(null) }
    var selectedHistoryDate by remember { mutableStateOf<LocalDate?>(null) }
    var notificationHighlightedSlot by remember { mutableStateOf<MedicationSlot?>(null) }
    val context = LocalContext.current
    val tutorialPreferences = remember { context.getSharedPreferences("patient_tutorial", android.content.Context.MODE_PRIVATE) }
    var tutorialStep by remember { mutableStateOf(if (tutorialPreferences.getBoolean("seen", false)) -1 else 0) }
    val scope = rememberCoroutineScope()
    val notificationPreferences = remember { PatientNotificationPreferences(context) }
    var notificationSettings by remember { mutableStateOf(notificationPreferences.load()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    fun applyNotificationSettings(settings: PatientNotificationSettings) {
        notificationSettings = settings
        notificationPreferences.save(settings)
        scope.launch {
            if (!settings.masterEnabled) PatientNotificationScheduler.cancelAll(context)
            else runCatching { repository.notificationHistory() }.onSuccess { (days, slotTimes) ->
                PatientNotificationScheduler.replace(
                    context,
                    PatientNotificationPlanBuilder.build(days, slotTimes, settings, Instant.now()),
                )
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> applyNotificationSettings(notificationSettings.copy(masterEnabled = granted)) }

    LaunchedEffect(tab) {
        when (tab) {
            PatientTab.TODAY -> Unit
            PatientTab.HISTORY -> repository.loadHistory()
            PatientTab.SETTINGS -> Unit
        }
    }

    DisposableEffect(lifecycleOwner, tab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && tab == PatientTab.TODAY) {
                scope.launch { repository.loadToday() }
            }
            if (event == Lifecycle.Event.ON_RESUME && notificationSettings.masterEnabled) {
                applyNotificationSettings(notificationSettings)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.notificationTarget) {
        val target = state.notificationTarget ?: return@LaunchedEffect
        if (target.date == LocalDate.now(ZoneId.of("Asia/Tokyo"))) {
            tab = PatientTab.TODAY
            notificationHighlightedSlot = target.slot
            repository.loadToday()
        } else {
            tab = PatientTab.HISTORY
            selectedHistoryDate = target.date
            repository.loadHistory(target.date)
            repository.loadHistoryDay(target.date)
        }
        repository.consumeNotificationTarget()
    }

    Scaffold(
        containerColor = PatientBackground,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                PatientTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.symbol, color = if (tab == item) PatientTeal else MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    medications = state.medicationById,
                    nextSlot = notificationHighlightedSlot ?: repository.nextActionSlot(),
                    updatingSlot = state.updatingSlot,
                    prnMedications = state.prnMedications,
                    updatingPrnMedicationId = state.updatingPrnMedicationId,
                    onRetry = { scope.launch { repository.loadToday() } },
                    onRecord = { confirmDose = it },
                    onDetail = { selectedDose = it },
                    onRecordSlot = { confirmSlot = it },
                    onRecordPrn = { confirmPrn = it },
                    onRemind = { dose ->
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        ReminderScheduler.schedule(context, dose.key, dose.medicationName)
                    },
                )
                PatientTab.HISTORY -> HistoryContent(
                    days = state.history,
                    yearMonth = YearMonth.of(state.historyYear ?: LocalDate.now().year, state.historyMonth ?: LocalDate.now().monthValue),
                    loading = state.loading,
                    error = state.error,
                    retentionCutoffDate = state.retentionCutoffDate,
                    retentionDays = state.retentionDays,
                    onPreviousMonth = { month -> scope.launch { repository.loadHistory(month.minusMonths(1).atDay(1)) } },
                    onNextMonth = { month -> scope.launch { repository.loadHistory(month.plusMonths(1).atDay(1)) } },
                    onSelectDate = { date -> selectedHistoryDate = date; scope.launch { repository.loadHistoryDay(date) } },
                    onRetry = { scope.launch { repository.loadHistory() } },
                )
                PatientTab.SETTINGS -> SettingsContent(
                    loading = state.loading,
                    error = state.error,
                    notificationSettings = notificationSettings,
                    onNotificationSettingsChange = { updated ->
                        if (updated.masterEnabled && !notificationSettings.masterEnabled && Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else applyNotificationSettings(updated)
                    },
                    onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    onUnlink = {
                        scope.launch {
                            if (repository.revokeSession()) {
                                applyNotificationSettings(notificationSettings.copy(masterEnabled = false))
                                onUnlink()
                            }
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
                    scope.launch { repository.record(dose); repository.refreshTodayAfterAction() }
                }) { Text("記録する") }
            },
            dismissButton = { TextButton(onClick = { confirmDose = null }) { Text("キャンセル") } },
        )
    }
    confirmPrn?.let { medication ->
        AlertDialog(
            onDismissRequest = { confirmPrn = null },
            title = { Text("頓服を記録しますか？") },
            text = { Text("${medication.name}を服用した記録を追加します。") },
            confirmButton = {
                Button(onClick = {
                    confirmPrn = null
                    scope.launch { repository.recordPrn(medication); repository.refreshTodayAfterAction() }
                }) { Text("記録する") }
            },
            dismissButton = { TextButton(onClick = { confirmPrn = null }) { Text("キャンセル") } },
        )
    }
    confirmSlot?.let { slot ->
        val slotDoses = state.doses.filter { it.slot == slot && it.status != DoseStatus.TAKEN }
        val recordable = slotDoses.count { !state.insufficientMedicationIds.contains(it.medicationId) }
        val totalPills = slotDoses.filter { !state.insufficientMedicationIds.contains(it.medicationId) }.sumOf(PatientDose::doseCount)
        AlertDialog(
            onDismissRequest = { confirmSlot = null },
            title = { Text("${slotTitle(slot)}をまとめて記録しますか？") },
            text = { Text("$recordable 種類、合計${formatAmount(totalPills)}錠を「飲みました」として記録します。") },
            confirmButton = {
                Button(onClick = {
                    confirmSlot = null
                    scope.launch { repository.recordSlot(slot); repository.refreshTodayAfterAction() }
                }) { Text("まとめて記録") }
            },
            dismissButton = { TextButton(onClick = { confirmSlot = null }) { Text("キャンセル") } },
        )
    }
    selectedDose?.let { dose ->
        ModalBottomSheet(onDismissRequest = { selectedDose = null }) {
            PatientDoseDetailContent(dose, state.medicationById[dose.medicationId])
        }
    }
    selectedHistoryDate?.let { date ->
        ModalBottomSheet(onDismissRequest = { selectedHistoryDate = null; repository.clearHistoryDay() }) {
            HistoryDayDetailContent(
                date = date,
                detail = state.historyDayDetail,
                loading = state.historyDayLoading,
                error = state.error,
                retentionCutoffDate = state.retentionCutoffDate,
                retentionDays = state.retentionDays,
                onRetry = { scope.launch { repository.loadHistoryDay(date) } },
            )
        }
    }
    if (tutorialStep >= 0) {
        PatientTutorialOverlay(
            step = tutorialStep,
            onSkip = { tutorialPreferences.edit().putBoolean("seen", true).apply(); tutorialStep = -1 },
            onPrevious = { if (tutorialStep > 0) tutorialStep -= 1 },
            onNext = {
                if (tutorialStep == 3) {
                    tutorialPreferences.edit().putBoolean("seen", true).apply()
                    tutorialStep = -1
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else applyNotificationSettings(notificationSettings.copy(masterEnabled = true))
                } else {
                    tutorialStep += 1
                    tab = when (tutorialStep) {
                        0 -> PatientTab.TODAY
                        1 -> PatientTab.HISTORY
                        2 -> PatientTab.SETTINGS
                        else -> PatientTab.TODAY
                    }
                }
            },
        )
    }
}

private data class PatientTutorialCopy(val title: String, val message: String)

@Composable
internal fun PatientTutorialOverlay(step: Int, onSkip: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val steps = listOf(
        PatientTutorialCopy("今日のお薬", "飲むお薬を確認します。飲んだら「飲みました」を押します。"),
        PatientTutorialCopy("記録を見る", "飲めた日や、まだ飲んでいないお薬を確認できます。"),
        PatientTutorialCopy("通知を使う", "お薬の時間に、この端末へお知らせできます。"),
        PatientTutorialCopy("お薬の時間に通知しますか？", "朝・昼・夜など、設定した時間にお薬の通知を受け取れます。"),
    )
    val copy = steps[step.coerceIn(steps.indices)]
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)).padding(20.dp)
            .semantics { paneTitle = "患者モードの使い方 ${step + 1}/${steps.size}" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(copy.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(copy.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { index -> Box(Modifier.size(if (index == step) 20.dp else 7.dp, 7.dp).background(if (index == step) PatientTeal else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))) }
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1}/${steps.size}", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("スキップ") }
                    if (step > 0) OutlinedButton(onClick = onPrevious) { Text("戻る") }
                    Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text(if (step == steps.lastIndex) "通知を設定" else "次へ") }
                }
            }
        }
    }
}

@Composable
internal fun TodayContent(
    doses: List<PatientDose>,
    loading: Boolean,
    updatingKey: String?,
    error: String?,
    message: String?,
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
    val date = today.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("今日のお薬", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        if (loading && doses.isEmpty()) item { CenteredProgress() }
        if (loading && doses.isNotEmpty()) item { NoticeCard("最新の情報に更新しています…", MaterialTheme.colorScheme.surfaceVariant, null) }
        error?.let { item { NoticeCard(it, MaterialTheme.colorScheme.errorContainer, onRetry) } }
        message?.let { item { NoticeCard(it, MaterialTheme.colorScheme.primaryContainer, null) } }
        if (prnMedications.isNotEmpty()) {
            item { Text("必要なときのお薬", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(prnMedications, key = PatientMedication::id) { medication ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(medication.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(medication.prnInstructions ?: medication.dosageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (medication.isInsufficientForDose) Text("在庫不足", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onRecordPrn(medication) },
                            enabled = !loading && !medication.isInsufficientForDose && updatingPrnMedicationId == null,
                            modifier = Modifier.testTag("prn-record-${medication.id}"),
                        ) { Text(if (updatingPrnMedicationId == medication.id) "記録中…" else "飲みました") }
                    }
                }
            }
        }
        if (!loading && error == null && doses.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✓", color = PatientTeal, style = MaterialTheme.typography.displaySmall)
                        Text("今日のお薬はありません", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("ゆっくりお過ごしください", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val grouped = doses.groupBy { it.slot ?: com.afterlifearchive.medmanager.data.patient.PatientSlotTimes.DEFAULT.resolve(it.scheduledAt) }
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
                    taken -> "服用済み"
                    inventoryInsufficient -> "在庫不足"
                    dose.status == DoseStatus.MISSED -> "未達"
                    else -> "未服用"
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
                ) { Text(if (inventoryInsufficient) "在庫を確認してください" else if (updating) "記録中…" else "飲みました", style = MaterialTheme.typography.titleMedium) }
                TextButton(modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !screenUpdating, onClick = { onRemind(dose) }) {
                    Text("10分後に知らせる", color = PatientTeal)
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
                    Text("${dateTimeText(dose)} ・ ${doseStatusText(dose.status)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            DetailCard("備考", medication?.notes?.trim().takeUnless { it.isNullOrEmpty() } ?: "備考はありません")
        }
        item {
            DetailCard("1回の服用量", "${formatAmount(dose.doseCount)}錠")
        }
        if (medication != null) {
            item { DetailCard("薬の強さ", "${formatAmount(medication.dosageStrengthValue)} ${medication.dosageStrengthUnit}") }
            if (medication.inventoryEnabled) {
                item { DetailCard("現在の在庫", "${formatAmount(medication.inventoryQuantity)} ${medication.inventoryUnit ?: ""}".trim()) }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun doseStatusText(status: DoseStatus) = when (status) {
    DoseStatus.PENDING -> "未服用"
    DoseStatus.TAKEN -> "服用済み"
    DoseStatus.MISSED -> "未達"
}

private fun dateTimeText(dose: PatientDose): String = dose.scheduledAt.atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern("M月d日 H:mm", Locale.JAPANESE))

private fun formatAmount(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

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
                Text(slotTitle(slot), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PatientTeal)
                Spacer(Modifier.weight(1f))
                if (isNext) Text("次のお薬", color = PatientTeal, fontWeight = FontWeight.Bold)
            }
            if (insufficientCount > 0) {
                Text("在庫不足のお薬が${insufficientCount}件あります", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
            if (!isWithinRecordingWindow && recordableCount == 0) {
                Text("記録できる時間になるまでお待ちください", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recordableCount > 0) {
                Button(
                    onClick = { onRecordSlot(slot) },
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (updating) "まとめて記録中…" else "この時間帯をまとめて記録（${recordableCount}件）") }
            }
        }
    }
}

private fun slotTitle(slot: MedicationSlot) = when (slot) {
    MedicationSlot.MORNING -> "朝のお薬"
    MedicationSlot.NOON -> "昼のお薬"
    MedicationSlot.EVENING -> "夕方のお薬"
    MedicationSlot.BEDTIME -> "寝る前のお薬"
}

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
        Text("服薬履歴", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onPreviousMonth(yearMonth) }, enabled = !loading) { Text("‹ 前月") }
            Text("${yearMonth.year}年${yearMonth.monthValue}月", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onNextMonth(yearMonth) }, enabled = !loading) { Text("翌月 ›") }
        }
        HistoryLegend()
        if (loading && days.isEmpty()) CenteredProgress()
        if (error != null) NoticeCard(error, MaterialTheme.colorScheme.errorContainer, onRetry)
        if (retentionCutoffDate != null) RetentionLockCard(retentionCutoffDate, retentionDays ?: 30)
        if (!loading && error == null && retentionCutoffDate == null) {
            HistoryCalendar(yearMonth, days, onSelectDate)
        }
    }
}

@Composable
private fun HistoryLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendItem("服用済み", MaterialTheme.colorScheme.primary)
        LegendItem("未達", MaterialTheme.colorScheme.error)
        LegendItem("未服用", MaterialTheme.colorScheme.tertiary)
        LegendItem("予定なし", MaterialTheme.colorScheme.outline)
    }
}

@Composable private fun LegendItem(text: String, color: Color) {
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
        Row(Modifier.fillMaxWidth()) { listOf("日", "月", "火", "水", "木", "金", "土").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) } }
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
    Column(
        Modifier.height(68.dp).padding(2.dp)
            .semantics { contentDescription = historyDayDescription(date, day) }
            .clickable { onSelectDate(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(date.dayOfMonth.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            statuses.forEach { status -> Box(Modifier.size(6.dp).background(historyStatusColor(status), CircleShape)) }
        }
        if ((day?.prnCount ?: 0) > 0) Text("頓${day?.prnCount}", style = MaterialTheme.typography.labelSmall, color = PatientTeal)
    }
}

private fun historyDayDescription(date: LocalDate, day: HistoryDay?): String {
    val dateText = date.format(DateTimeFormatter.ofPattern("M月d日 E曜日", Locale.JAPANESE))
    if (day == null) return "$dateText、予定なし"
    fun status(name: String, value: HistoryStatus) = "$name${when (value) {
        HistoryStatus.TAKEN -> "服用済み"
        HistoryStatus.MISSED -> "未達"
        HistoryStatus.PENDING -> "未服用"
        HistoryStatus.NONE -> "予定なし"
    }}"
    val slots = listOf(status("朝", day.morning), status("昼", day.noon), status("夕", day.evening), status("寝る前", day.bedtime))
    return buildString {
        append(dateText).append("、").append(slots.joinToString("、"))
        if (day.prnCount > 0) append("、頓服").append(day.prnCount).append("回")
    }
}

@Composable private fun historyStatusColor(status: HistoryStatus): Color = when (status) {
    HistoryStatus.TAKEN -> MaterialTheme.colorScheme.primary
    HistoryStatus.MISSED -> MaterialTheme.colorScheme.error
    HistoryStatus.PENDING -> MaterialTheme.colorScheme.tertiary
    HistoryStatus.NONE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
}

@Composable
private fun RetentionLockCard(cutoffDate: String, retentionDays: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("この履歴は表示できません", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("無料版では直近${retentionDays}日間の履歴を確認できます。\n閲覧可能な最初の日：$cutoffDate", textAlign = TextAlign.Center)
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
        item { Text(date.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (loading) item { CenteredProgress() }
        if (retentionCutoffDate != null) item { RetentionLockCard(retentionCutoffDate, retentionDays ?: 30) }
        if (error != null) item { NoticeCard(error, MaterialTheme.colorScheme.errorContainer, onRetry) }
        if (!loading && error == null && retentionCutoffDate == null && detail != null) {
            if (detail.doses.isEmpty() && detail.prnItems.isEmpty()) {
                item { DetailCard("この日の記録", "予定されたお薬や頓服の記録はありません") }
            }
            if (detail.doses.isNotEmpty()) item { Text("予定されたお薬", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(detail.doses, key = { "${it.medicationId}:${it.scheduledAt}" }) { HistoryScheduledDoseRow(it) }
            if (detail.prnItems.isNotEmpty()) item { Text("頓服", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
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
                dose.recordedByType?.let { Text(if (it == com.afterlifearchive.medmanager.data.patient.RecordedByType.PATIENT) "本人が記録" else "家族が記録", style = MaterialTheme.typography.labelMedium) }
            }
            Text(doseStatusText(dose.status), color = historyDoseColor(dose.status), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrnHistoryRow(item: PrnHistoryItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.medicationName, fontWeight = FontWeight.Bold)
                Text("${historyTime(item.takenAt)}　${formatAmount(item.quantityTaken)}錠", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (item.actorType == com.afterlifearchive.medmanager.data.patient.PrnActorType.PATIENT) "本人" else "家族", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun historyDoseColor(status: DoseStatus): Color = when (status) {
    DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
    DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    DoseStatus.PENDING -> MaterialTheme.colorScheme.tertiary
}

private fun historyTime(instant: Instant) = instant.atZone(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("H:mm"))

@Composable
internal fun SettingsContent(
    loading: Boolean,
    error: String?,
    notificationSettings: PatientNotificationSettings,
    onNotificationSettingsChange: (PatientNotificationSettings) -> Unit,
    onOpenUrl: (String) -> Unit,
    onUnlink: () -> Unit,
) {
    var confirmUnlink by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("patient-settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 18.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("通知とこの端末の連携を管理します", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard("服薬通知") {
                SettingsSwitchRow("通知を受け取る", "予定時刻にお薬をお知らせします", notificationSettings.masterEnabled) {
                    onNotificationSettingsChange(notificationSettings.copy(masterEnabled = it))
                }
                MedicationSlot.entries.forEach { slot ->
                    SettingsSwitchRow(slotTitle(slot), "${slot.name.lowercase()}の通知", slot in notificationSettings.enabledSlots, notificationSettings.masterEnabled) { enabled ->
                        val slots = notificationSettings.enabledSlots.toMutableSet().apply { if (enabled) add(slot) else remove(slot) }
                        onNotificationSettingsChange(notificationSettings.copy(enabledSlots = slots))
                    }
                }
                SettingsSwitchRow("15分後にもう一度通知", "飲み忘れ防止の再通知です", notificationSettings.rereminderEnabled, notificationSettings.masterEnabled) {
                    onNotificationSettingsChange(notificationSettings.copy(rereminderEnabled = it))
                }
            }
        }
        item {
            SettingsCard("連携状態") {
                Text("この端末は本人モードとして家族と連携されています。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("法務・サポート") {
                SettingsLink("プライバシーポリシー") { onOpenUrl("https://www.okusuri-mimamori.com/privacy") }
                SettingsLink("利用規約") { onOpenUrl("https://www.okusuri-mimamori.com/terms") }
                SettingsLink("サポート") { onOpenUrl("https://www.okusuri-mimamori.com/support") }
            }
        }
        error?.let { item { NoticeCard(it, MaterialTheme.colorScheme.errorContainer, null) } }
        item {
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("patient-unlink-button"),
                enabled = !loading,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = { confirmUnlink = true },
            ) { Text(if (loading) "解除中…" else "この端末の連携を解除") }
        }
    }
    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("連携を解除しますか？") },
            text = { Text("サーバー上のこの端末のセッションを無効にして、利用方法の選択画面へ戻ります。") },
            confirmButton = { Button(onClick = { confirmUnlink = false; onUnlink() }) { Text("解除する") } },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("キャンセル") } },
        )
    }
}

@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable private fun SettingsLink(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Start)
        Text("›")
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

private fun slotLabel(dose: PatientDose): String = when (dose.slot ?: com.afterlifearchive.medmanager.data.patient.PatientSlotTimes.DEFAULT.resolve(dose.scheduledAt)) {
    MedicationSlot.MORNING -> "朝のお薬"
    MedicationSlot.NOON -> "昼のお薬"
    MedicationSlot.EVENING -> "夕方のお薬"
    MedicationSlot.BEDTIME -> "寝る前のお薬"
}
