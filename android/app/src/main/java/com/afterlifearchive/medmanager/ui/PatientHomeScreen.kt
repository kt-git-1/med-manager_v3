package com.afterlifearchive.medmanager.ui

import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.afterlifearchive.medmanager.AnalyticsConsentPreferences
import com.afterlifearchive.medmanager.AnalyticsPatientTab
import com.afterlifearchive.medmanager.AnalyticsService
import com.afterlifearchive.medmanager.AnalyticsAppMode
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

internal enum class PatientTab(val titleResource: Int, val icon: ImageVector) {
    TODAY(R.string.patient_tab_today, Icons.Rounded.CalendarMonth),
    HISTORY(R.string.patient_tab_history, Icons.Rounded.History),
    SETTINGS(R.string.patient_tab_settings, Icons.Rounded.Settings),
}

@Composable
internal fun PatientModePreview(initialTab: PatientTab = PatientTab.TODAY) {
    val morningName = stringResource(R.string.patient_preview_morning_name)
    val bloodPressureName = stringResource(R.string.patient_preview_blood_pressure_name)
    val stomachName = stringResource(R.string.patient_preview_stomach_name)
    val eveningName = stringResource(R.string.patient_preview_evening_name)
    val prnName = stringResource(R.string.patient_preview_prn_name)
    val prnInstructions = stringResource(R.string.patient_preview_prn_instructions)
    val oneTablet = stringResource(R.string.patient_preview_one_tablet)
    val twoTablets = stringResource(R.string.patient_preview_two_tablets)
    val previewNow = remember { Instant.parse("2026-07-14T03:00:00Z") }
    val previewDate = remember(previewNow) { previewNow.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate() }
    val previewHistory = remember {
        listOf(
            HistoryDay("2026-07-10", HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            HistoryDay("2026-07-11", HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            HistoryDay("2026-07-12", HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 1),
            HistoryDay("2026-07-13", HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            HistoryDay("2026-07-14", HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.PENDING, HistoryStatus.NONE, 0),
        )
    }
    val previewDoses = remember(morningName, bloodPressureName, stomachName, eveningName, oneTablet, twoTablets) {
        listOf(
            PatientDose("preview-1", "med-1", Instant.parse("2026-07-13T23:00:00Z"), DoseStatus.TAKEN, morningName, oneTablet, 1.0, slot = MedicationSlot.MORNING),
            PatientDose("preview-2", "med-2", Instant.parse("2026-07-14T03:30:00Z"), DoseStatus.PENDING, "$bloodPressureName 5 mg", oneTablet, 1.0, slot = MedicationSlot.NOON),
            PatientDose("preview-3", "med-3", Instant.parse("2026-07-14T03:30:00Z"), DoseStatus.PENDING, stomachName, oneTablet, 1.0, slot = MedicationSlot.NOON),
            PatientDose("preview-4", "med-4", Instant.parse("2026-07-14T10:00:00Z"), DoseStatus.PENDING, eveningName, twoTablets, 2.0, slot = MedicationSlot.EVENING),
        )
    }
    val prnMedication = remember(prnName, prnInstructions, oneTablet) {
        PatientMedication(
            id = "preview-prn", patientId = "preview-patient", name = prnName, dosageText = oneTablet,
            doseCountPerIntake = 1.0, dosageStrengthValue = 200.0, dosageStrengthUnit = "mg", notes = null,
            isPrn = true, prnInstructions = prnInstructions, startDate = Instant.EPOCH, endDate = null,
            inventoryCount = 12.0, inventoryUnit = "錠", inventoryEnabled = true, inventoryQuantity = 12.0,
            inventoryOut = false, isActive = true, isArchived = false, nextScheduledAt = null,
            regimenTimes = null, regimenDaysOfWeek = null,
        )
    }
    Scaffold(
        containerColor = PatientBackground,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                PatientTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == initialTab,
                        onClick = {},
                        icon = { Icon(item.icon, contentDescription = null) },
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
            when (initialTab) {
                PatientTab.HISTORY -> HistoryContent(
                    days = previewHistory, loading = false, error = null,
                    retentionCutoffDate = null, retentionDays = null, onRetry = {}, now = previewDate,
                )
                PatientTab.SETTINGS -> SettingsContent(
                    loading = false, error = null,
                    notificationSettings = PatientNotificationSettings(masterEnabled = true),
                    onNotificationSettingsChange = {}, notificationPermissionDenied = false,
                    analyticsEnabled = false, onAnalyticsEnabledChange = {}, onOpenUrl = {}, onUnlink = {},
                )
                PatientTab.TODAY -> TodayContent(
                    doses = previewDoses,
                    loading = false,
                    updatingKey = null,
                    error = null,
                    message = null,
                    maintenanceWarning = null,
                    medications = mapOf(prnMedication.id to prnMedication),
                    nextSlot = MedicationSlot.NOON,
                    updatingSlot = null,
                    prnMedications = listOf(prnMedication),
                    updatingPrnMedicationId = null,
                    onRetry = {},
                    onRecord = {},
                    onDetail = {},
                    onRecordSlot = {},
                    onRecordPrn = {},
                    onRemind = {},
                    now = previewNow,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PatientHomeScreen(repository: PatientRepository, onUnlink: () -> Unit, analyticsService: AnalyticsService? = null) {
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
    val analyticsPreferences = remember { AnalyticsConsentPreferences(context) }
    var analyticsEnabled by remember { mutableStateOf(analyticsPreferences.isEnabled()) }
    val analyticsState = analyticsService?.state?.collectAsStateWithLifecycle()?.value
    val permissionPreferences = remember { context.getSharedPreferences("notification_permission", android.content.Context.MODE_PRIVATE) }
    var notificationPermissionDenied by remember {
        mutableStateOf(permissionPreferences.getBoolean("requested", false) && !NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
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
    ) { granted ->
        notificationPermissionDenied = !granted
        applyNotificationSettings(notificationSettings.copy(masterEnabled = granted))
    }

    LaunchedEffect(tab) {
        analyticsService?.logPatientTabViewed(
            when (tab) {
                PatientTab.TODAY -> AnalyticsPatientTab.TODAY
                PatientTab.HISTORY -> AnalyticsPatientTab.HISTORY
                PatientTab.SETTINGS -> AnalyticsPatientTab.SETTINGS
            },
        )
    }
    LaunchedEffect(tab, freshness.dose) {
        if (tab == PatientTab.HISTORY) {
            historyFreshnessCursor.refreshIfStale { repository.loadHistory() }
        }
    }
    LaunchedEffect(Unit) {
        if (tutorialStep >= 0) analyticsService?.logTutorialStarted(AnalyticsAppMode.PATIENT)
    }
    LaunchedEffect(tutorialStep) {
        if (tutorialStep >= 0) analyticsService?.logTutorialStepViewed(AnalyticsAppMode.PATIENT, tutorialStep + 1)
    }

    DisposableEffect(lifecycleOwner, tab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionDenied = permissionPreferences.getBoolean("requested", false) && !NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
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
                        icon = { Icon(item.icon, contentDescription = null) },
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
                    loading = state.loading,
                    error = errorText,
                    retentionCutoffDate = state.retentionCutoffDate,
                    retentionDays = state.retentionDays,
                    onRetry = { scope.launch { repository.loadHistory() } },
                )
                PatientTab.SETTINGS -> SettingsContent(
                    loading = state.loading,
                    error = errorText,
                    notificationSettings = notificationSettings,
                    notificationPermissionDenied = notificationPermissionDenied,
                    onNotificationSettingsChange = { updated ->
                        if (updated.masterEnabled && !notificationSettings.masterEnabled && Build.VERSION.SDK_INT >= 33) {
                            permissionPreferences.edit().putBoolean("requested", true).apply()
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else applyNotificationSettings(updated)
                    },
                    analyticsEnabled = analyticsState?.enabled ?: analyticsEnabled,
                    onAnalyticsEnabledChange = { enabled ->
                        analyticsEnabled = enabled
                        if (analyticsService != null) analyticsService.setCollectionEnabled(enabled)
                        else analyticsPreferences.setEnabled(enabled)
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
            onSkip = {
                tutorialPreferences.edit().putBoolean("seen", true).apply()
                tutorialStep = -1
                analyticsService?.logTutorialFinished(AnalyticsAppMode.PATIENT, skipped = true)
            },
            onPrevious = { if (tutorialStep > 0) tutorialStep -= 1 },
            onNext = {
                if (tutorialStep == 3) {
                    tutorialPreferences.edit().putBoolean("seen", true).apply()
                    tutorialStep = -1
                    analyticsService?.logTutorialFinished(AnalyticsAppMode.PATIENT, skipped = false)
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
