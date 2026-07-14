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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.afterlifearchive.medmanager.ReminderScheduler
import com.afterlifearchive.medmanager.PatientNotificationPreferences
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.PatientNotificationPlanBuilder
import com.afterlifearchive.medmanager.PatientNotificationScheduler
import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
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
import com.afterlifearchive.medmanager.data.patient.PatientMaintenanceWarning
import com.afterlifearchive.medmanager.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class PatientTab(val titleResource: Int, val symbol: String) {
    TODAY(R.string.patient_tab_today, "●"),
    HISTORY(R.string.patient_tab_history, "◷"),
    SETTINGS(R.string.patient_tab_settings, "⚙"),
}

@Composable
fun PatientModePreview() {
    val morningName = stringResource(R.string.patient_preview_morning_name)
    val bloodPressureName = stringResource(R.string.patient_preview_blood_pressure_name)
    val eveningName = stringResource(R.string.patient_preview_evening_name)
    val oneTablet = stringResource(R.string.patient_preview_one_tablet)
    val twoTablets = stringResource(R.string.patient_preview_two_tablets)
    val previewDoses = remember(morningName, bloodPressureName, eveningName, oneTablet, twoTablets) {
        listOf(
            PatientDose("preview-1", "med-1", Instant.now().minusSeconds(600), DoseStatus.PENDING, morningName, oneTablet, 1.0),
            PatientDose("preview-2", "med-2", Instant.now().plusSeconds(300), DoseStatus.PENDING, bloodPressureName, oneTablet, 1.0),
            PatientDose("preview-3", "med-3", Instant.now().plusSeconds(14_400), DoseStatus.TAKEN, eveningName, twoTablets, 2.0),
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
                        label = { Text(stringResource(item.titleResource)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PatientTeal,
                            selectedTextColor = PatientTeal,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
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
                maintenanceWarning = null,
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
    val freshness by repository.freshness.collectAsStateWithLifecycle()
    val errorText = state.error?.let { patientUserMessageText(it) }
    val messageText = state.message?.let { patientUserMessageText(it) }
    val navigation = rememberPatientNavigationState()
    val tab = navigation.tab
    var confirmDose by remember { mutableStateOf<PatientDose?>(null) }
    var confirmPrn by remember { mutableStateOf<PatientMedication?>(null) }
    var confirmSlot by remember { mutableStateOf<MedicationSlot?>(null) }
    var notificationHighlightedSlot by remember { mutableStateOf<MedicationSlot?>(null) }
    val context = LocalContext.current
    val tutorialPreferences = remember { context.getSharedPreferences("patient_tutorial", android.content.Context.MODE_PRIVATE) }
    var tutorialStep by rememberSaveable { mutableStateOf(if (tutorialPreferences.getBoolean("seen", false)) -1 else 0) }
    val scope = rememberCoroutineScope()
    val notificationPreferences = remember { PatientNotificationPreferences(context) }
    var notificationSettings by remember { mutableStateOf(notificationPreferences.load()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyFreshnessCursor = remember(repository) {
        repository.newFreshnessCursor(FreshnessConsumer.PATIENT_HISTORY)
    }

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

    LaunchedEffect(tab, freshness.dose) {
        if (tab == PatientTab.HISTORY) {
            historyFreshnessCursor.refreshIfStale { repository.loadHistory() }
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
        val route = patientRouteFor(target)
        navigation.selectTab(route.tab)
        notificationHighlightedSlot = route.highlightedSlot
        repository.loadToday()
        repository.consumeNotificationTarget()
        delay(PATIENT_NOTIFICATION_HIGHLIGHT_MILLIS)
        if (notificationHighlightedSlot == route.highlightedSlot) notificationHighlightedSlot = null
    }

    Scaffold(
        containerColor = PatientBackground,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                PatientTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { navigation.selectTab(item) },
                        icon = { Text(item.symbol, color = if (tab == item) PatientTeal else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(stringResource(item.titleResource)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PatientTeal,
                            selectedTextColor = PatientTeal,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        PatientPersistentTabHost(
            selectedTab = tab,
            loadedTabs = navigation.loadedTabs,
            modifier = Modifier.fillMaxSize().padding(padding).safeDrawingPadding(),
        ) { item ->
            when (item) {
                PatientTab.TODAY -> TodayContent(
                    doses = state.doses,
                    loading = state.loading,
                    updatingKey = state.updatingDoseKey,
                    error = errorText,
                    message = messageText,
                    maintenanceWarning = state.maintenanceWarning?.let { warning ->
                        when (warning) {
                            PatientMaintenanceWarning.REMINDER_REFRESH_FAILED -> stringResource(R.string.patient_reminder_refresh_failed)
                        }
                    },
                    medications = state.medicationById,
                    nextSlot = notificationHighlightedSlot ?: repository.nextActionSlot(),
                    updatingSlot = state.updatingSlot,
                    prnMedications = state.prnMedications,
                    updatingPrnMedicationId = state.updatingPrnMedicationId,
                    onRetry = { scope.launch { repository.loadToday() } },
                    onRecord = { confirmDose = it },
                    onDetail = { navigation.showDose(it.key) },
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
                    error = errorText,
                    retentionCutoffDate = state.retentionCutoffDate,
                    retentionDays = state.retentionDays,
                    onPreviousMonth = { month -> scope.launch { repository.loadHistory(month.minusMonths(1).atDay(1)) } },
                    onNextMonth = { month -> scope.launch { repository.loadHistory(month.plusMonths(1).atDay(1)) } },
                    onSelectDate = { date -> navigation.showHistoryDate(date); scope.launch { repository.loadHistoryDay(date) } },
                    onRetry = { scope.launch { repository.loadHistory() } },
                )
                PatientTab.SETTINGS -> SettingsContent(
                    loading = state.loading,
                    error = errorText,
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
            title = { Text(stringResource(R.string.patient_record_confirm_title)) },
            text = { Text(stringResource(R.string.patient_record_confirm_message, dose.medicationName)) },
            confirmButton = {
                Button(onClick = {
                    confirmDose = null
                    scope.launch { repository.record(dose); repository.refreshTodayAfterAction() }
                }) { Text(stringResource(R.string.patient_record)) }
            },
            dismissButton = { TextButton(onClick = { confirmDose = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    confirmPrn?.let { medication ->
        AlertDialog(
            onDismissRequest = { confirmPrn = null },
            title = { Text(stringResource(R.string.patient_prn_confirm_title)) },
            text = { Text(stringResource(R.string.patient_prn_confirm_message, medication.name)) },
            confirmButton = {
                Button(onClick = {
                    confirmPrn = null
                    scope.launch { repository.recordPrn(medication); repository.refreshTodayAfterAction() }
                }) { Text(stringResource(R.string.patient_record)) }
            },
            dismissButton = { TextButton(onClick = { confirmPrn = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    confirmSlot?.let { slot ->
        val slotDoses = state.doses.filter { it.slot == slot && it.status != DoseStatus.TAKEN }
        val recordable = slotDoses.count { !state.insufficientMedicationIds.contains(it.medicationId) }
        val totalPills = slotDoses.filter { !state.insufficientMedicationIds.contains(it.medicationId) }.sumOf(PatientDose::doseCount)
        AlertDialog(
            onDismissRequest = { confirmSlot = null },
            title = { Text(stringResource(R.string.patient_slot_confirm_title, patientSlotTitle(slot))) },
            text = { Text(stringResource(R.string.patient_slot_confirm_message, recordable, formatPatientAmount(totalPills))) },
            confirmButton = {
                Button(onClick = {
                    confirmSlot = null
                    scope.launch { repository.recordSlot(slot); repository.refreshTodayAfterAction() }
                }) { Text(stringResource(R.string.patient_record_bulk)) }
            },
            dismissButton = { TextButton(onClick = { confirmSlot = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    navigation.selectedDoseKey?.let { key -> state.doses.firstOrNull { it.key == key } }?.let { dose ->
        ModalBottomSheet(onDismissRequest = navigation::dismissDose) {
            PatientDoseDetailContent(dose, state.medicationById[dose.medicationId])
        }
    }
    navigation.selectedHistoryDate?.let { date ->
        ModalBottomSheet(onDismissRequest = { navigation.dismissHistoryDate(); repository.clearHistoryDay() }) {
            HistoryDayDetailContent(
                date = date,
                detail = state.historyDayDetail,
                loading = state.historyDayLoading,
                error = errorText,
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
                    navigation.selectTab(when (tutorialStep) {
                        0 -> PatientTab.TODAY
                        1 -> PatientTab.HISTORY
                        2 -> PatientTab.SETTINGS
                        else -> PatientTab.TODAY
                    })
                }
            },
        )
    }
}

internal data class PatientNotificationRoute(
    val tab: PatientTab,
    val highlightedSlot: MedicationSlot,
)

internal fun patientRouteFor(target: com.afterlifearchive.medmanager.data.patient.PatientNotificationTarget) =
    PatientNotificationRoute(PatientTab.TODAY, target.slot)

@Composable
internal fun PatientPersistentTabHost(
    selectedTab: PatientTab,
    loadedTabs: Set<PatientTab>,
    modifier: Modifier = Modifier,
    content: @Composable (PatientTab) -> Unit,
) {
    Box(modifier) {
        PatientTab.entries.filter { it in loadedTabs }.forEach { item ->
            key(item) {
                Box(
                    Modifier.fillMaxSize()
                        .patientTabVisibility(item == selectedTab)
                        .testTag("patient-tab-${item.name.lowercase()}"),
                ) {
                    content(item)
                }
            }
        }
    }
}

private fun Modifier.patientTabVisibility(isVisible: Boolean): Modifier =
    zIndex(if (isVisible) 1f else 0f)
        .alpha(if (isVisible) 1f else 0f)
        .then(
            if (isVisible) Modifier else Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
        )

private const val PATIENT_NOTIFICATION_HIGHLIGHT_MILLIS = 4_000L
