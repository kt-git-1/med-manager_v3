package com.afterlifearchive.medmanager.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverCreateError
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.caregiver.CaregiverLinkingCode
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.push.CaregiverPushRepository
import com.afterlifearchive.medmanager.AnalyticsCaregiverTab
import com.afterlifearchive.medmanager.AnalyticsService
import com.afterlifearchive.medmanager.AnalyticsAppMode
import com.afterlifearchive.medmanager.AnalyticsCoreAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import kotlinx.coroutines.launch

enum class CaregiverTab(val label: Int, val icon: ImageVector) {
    TODAY(R.string.caregiver_tab_today, Icons.Rounded.Home),
    MEDICATIONS(R.string.caregiver_tab_medications, Icons.Rounded.Medication),
    INVENTORY(R.string.caregiver_tab_inventory, Icons.Rounded.Inventory2),
    HISTORY(R.string.caregiver_tab_history, Icons.Rounded.History),
    SETTINGS(R.string.caregiver_tab_settings, Icons.Rounded.Settings),
}

@Composable
fun CaregiverHomeScreen(
    repository: CaregiverPatientRepository,
    medicationRepository: CaregiverMedicationRepository? = null,
    todayRepository: CaregiverTodayRepository? = null,
    inventoryRepository: CaregiverInventoryRepository? = null,
    historyRepository: CaregiverHistoryRepository? = null,
    reportRepository: CaregiverReportRepository? = null,
    pushRepository: CaregiverPushRepository? = null,
    analyticsService: AnalyticsService? = null,
    onLogout: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
    tutorialEnabled: Boolean = true,
    requestNotificationPermission: (() -> Unit)? = null,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tutorialPreferences = remember { context.getSharedPreferences("caregiver_tutorial", Context.MODE_PRIVATE) }
    var selectedTabName by rememberSaveable { mutableStateOf(CaregiverTab.TODAY.name) }
    var loadedTabNames by rememberSaveable { mutableStateOf(setOf(CaregiverTab.TODAY.name)) }
    var tutorialStep by rememberSaveable {
        mutableStateOf(if (tutorialEnabled && !tutorialPreferences.getBoolean("seen", false)) 0 else -1)
    }
    var postTutorialFocusTag by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTab = CaregiverTab.valueOf(selectedTabName)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val historyState = historyRepository?.state?.collectAsStateWithLifecycle()?.value

    fun selectTab(tab: CaregiverTab) {
        val changed = selectedTabName != tab.name
        selectedTabName = tab.name
        loadedTabNames = loadedTabNames + tab.name
        if (changed) analyticsService?.logCaregiverTabViewed(tab.analyticsValue())
    }

    fun finishTutorial(openRegistration: Boolean) {
        tutorialPreferences.edit().putBoolean("seen", true).apply()
        tutorialStep = -1
        analyticsService?.logTutorialFinished(AnalyticsAppMode.CAREGIVER, skipped = !openRegistration)
        if (openRegistration) {
            selectTab(CaregiverTab.SETTINGS)
            postTutorialFocusTag = "caregiver-create-name"
        }
    }

    LaunchedEffect(Unit) {
        repository.refresh()
        analyticsService?.logCaregiverTabViewed(CaregiverTab.TODAY.analyticsValue())
        if (tutorialStep >= 0) analyticsService?.logTutorialStarted(AnalyticsAppMode.CAREGIVER)
    }
    LaunchedEffect(historyState?.navigationRequestId, state.patients) {
        val targetPatientId = historyState?.notificationPatientId
        if ((historyState?.navigationRequestId ?: 0) > 0 && targetPatientId != null && state.patients.any { it.id == targetPatientId }) {
            repository.selectPatient(targetPatientId)
            selectTab(CaregiverTab.HISTORY)
        }
    }
    LaunchedEffect(tutorialStep) {
        if (tutorialStep >= 0) {
            selectTab(caregiverTutorialTab(tutorialStep))
            analyticsService?.logTutorialStepViewed(AnalyticsAppMode.CAREGIVER, tutorialStep + 1)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("caregiver-home")) {
            if (state.refreshFailed) {
                CaregiverStaleDataCard(
                    testTag = "caregiver-patient-stale",
                    onRetry = { scope.launch { repository.refresh() } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CaregiverTab.entries.forEach { tab ->
                    if (tab.name in loadedTabNames) {
                        val visible = tab == selectedTab
                        Box(
                            Modifier
                                .fillMaxSize()
                                .alpha(if (visible) 1f else 0f)
                                .zIndex(if (visible) 1f else 0f)
                                .testTag("caregiver-content-${tab.name.lowercase()}")
                                .then(if (visible) Modifier else Modifier.semantics { hideFromAccessibility() }),
                        ) {
                            CaregiverTabContent(
                                tab,
                                state,
                                repository,
                                medicationRepository,
                                todayRepository,
                                inventoryRepository,
                                historyRepository,
                                reportRepository,
                                pushRepository,
                                analyticsService,
                                visible,
                                onLogout,
                                onAccountDeleted,
                                if (tutorialStep >= 0) caregiverTutorialFocusTag(tutorialStep) else postTutorialFocusTag,
                                onOpenMedications = { selectTab(CaregiverTab.MEDICATIONS) },
                            )
                        }
                    }
                }
            }
            NavigationBar {
                CaregiverTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        modifier = Modifier.testTag("caregiver-tab-${tab.name.lowercase()}"),
                    )
                }
            }
        }
        if (tutorialStep >= 0) {
            CaregiverTutorialOverlay(
                step = tutorialStep,
                onSkip = { finishTutorial(openRegistration = false) },
                onPrevious = { if (tutorialStep > 0) tutorialStep -= 1 },
                onNext = {
                    if (tutorialStep == CAREGIVER_TUTORIAL_STEP_COUNT - 1) {
                        finishTutorial(openRegistration = true)
                        if (requestNotificationPermission != null) requestNotificationPermission()
                        else if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        tutorialStep += 1
                    }
                },
            )
        }
    }
}

@Composable
private fun CaregiverTabContent(
    tab: CaregiverTab,
    state: CaregiverPatientState,
    repository: CaregiverPatientRepository,
    medicationRepository: CaregiverMedicationRepository?,
    todayRepository: CaregiverTodayRepository?,
    inventoryRepository: CaregiverInventoryRepository?,
    historyRepository: CaregiverHistoryRepository?,
    reportRepository: CaregiverReportRepository?,
    pushRepository: CaregiverPushRepository?,
    analyticsService: AnalyticsService?,
    visible: Boolean,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    tutorialFocusTag: String?,
    onOpenMedications: () -> Unit,
) {
    when (tab) {
        CaregiverTab.SETTINGS -> CaregiverPatientSelectionScreen(state, repository, pushRepository, analyticsService, visible, onLogout, onAccountDeleted, tutorialFocusTag)
        CaregiverTab.MEDICATIONS -> if (medicationRepository != null) {
            CaregiverMedicationScreen(
                repository = medicationRepository,
                patientState = state,
                enabled = visible,
            )
        } else CaregiverFeatureLanding(tab, state, repository, visible)
        CaregiverTab.TODAY -> if (todayRepository != null) {
            CaregiverTodayScreen(todayRepository, state, visible, onOpenMedications, onLogout)
        } else CaregiverFeatureLanding(tab, state, repository, visible)
        CaregiverTab.INVENTORY -> if (inventoryRepository != null) {
            CaregiverInventoryScreen(inventoryRepository, state, visible, onOpenMedications)
        } else CaregiverFeatureLanding(tab, state, repository, visible)
        CaregiverTab.HISTORY -> if (historyRepository != null) {
            CaregiverHistoryScreen(historyRepository, state, visible, reportRepository = reportRepository)
        } else CaregiverFeatureLanding(tab, state, repository, visible)
    }
}

@Composable
private fun CaregiverFeatureLanding(
    tab: CaregiverTab,
    state: CaregiverPatientState,
    repository: CaregiverPatientRepository,
    visible: Boolean,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(tab.label), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.testTag("caregiver-feature-state")) {
            CaregiverPatientGate(state, visible) { scope.launch { repository.refresh() } }
        }
    }
}

@Composable
private fun CaregiverPatientGate(
    state: CaregiverPatientState,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    val selectedPatient = state.selectedPatient
    when {
        state.loading && state.patients.isEmpty() -> CircularProgressIndicator()
        state.loadFailed -> CaregiverMessage(
            stringResource(R.string.caregiver_data_unavailable_title),
            stringResource(R.string.caregiver_data_unavailable_message),
        ) {
            Button(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.common_retry))
            }
        }
        state.patients.isEmpty() -> CaregiverMessage(
            stringResource(R.string.caregiver_no_patient_title),
            stringResource(R.string.caregiver_no_patient_message),
        )
        selectedPatient == null -> CaregiverMessage(
            stringResource(R.string.caregiver_no_selection_title),
            stringResource(R.string.caregiver_no_selection_message),
        )
        else -> Text(
            stringResource(R.string.caregiver_selected_patient, selectedPatient.displayName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CaregiverPatientSelectionScreen(
    state: CaregiverPatientState,
    repository: CaregiverPatientRepository,
    pushRepository: CaregiverPushRepository?,
    analyticsService: AnalyticsService?,
    enabled: Boolean,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    tutorialFocusTag: String?,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pushState = pushRepository?.state?.collectAsStateWithLifecycle()?.value
    val analyticsState = analyticsService?.state?.collectAsStateWithLifecycle()?.value
    val patientActionsEnabled = enabled && !state.refreshFailed
    val updating = state.creating || state.savingSlotTimes || state.issuingLinkingCode ||
        state.destructiveActionInProgress || pushState?.syncing == true
    val pushPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { pushRepository?.enable() }
    }
    val selectedPatient = state.selectedPatient
    var morning by rememberSaveable { mutableStateOf("08:00") }
    var noon by rememberSaveable { mutableStateOf("12:00") }
    var evening by rememberSaveable { mutableStateOf("18:00") }
    var bedtime by rememberSaveable { mutableStateOf("21:00") }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var showingSlotTimes by rememberSaveable { mutableStateOf(false) }
    val linkingCodeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slotTimesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(selectedPatient?.id, selectedPatient?.slotTimes) {
        val times = selectedPatient?.slotTimes ?: CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")
        morning = times.morning
        noon = times.noon
        evening = times.evening
        bedtime = times.bedtime
    }
    LaunchedEffect(tutorialFocusTag, state.patients.size, selectedPatient?.id) {
        val targetIndex = when (tutorialFocusTag) {
            "caregiver-create-name" -> if (state.patients.isEmpty()) 2 else 1
            "caregiver-slot-times" -> if (selectedPatient != null) 3 else 1
            "caregiver-linking-code" -> if (selectedPatient != null) 2 else 1
            else -> null
        }
        if (targetIndex != null) listState.animateScrollToItem(targetIndex)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp).testTag("caregiver-settings-list"),
            state = listState,
            contentPadding = PaddingValues(top = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        if (!state.loading && !state.loadFailed) item {
            CaregiverSettingsHeader(selectedPatient?.displayName)
        }
        if (state.loading && state.patients.isEmpty()) item {
            CaregiverSettingsLoadingState()
        }
        if (state.loadFailed) item {
            CaregiverMessage(
                stringResource(R.string.caregiver_data_unavailable_title),
                stringResource(R.string.caregiver_data_unavailable_message),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { repository.refresh() } },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-settings-retry"),
                    ) { Text(stringResource(R.string.common_retry)) }
                    TextButton(
                        onClick = onLogout,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-settings-return-login"),
                    ) { Text(stringResource(R.string.caregiver_account_logout)) }
                }
            }
        }
        if (!state.loading && !state.loadFailed) {
        if (!state.loading && !state.loadFailed && state.patients.isEmpty()) item {
            CaregiverSettingsEmptyState(Modifier.testTag("caregiver-settings-empty"))
        }
        if (!state.loading && !state.loadFailed && (state.patients.isEmpty() || state.refreshFailed)) item {
            CaregiverCreatePatientCard(
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                error = state.createError,
                enabled = patientActionsEnabled && !state.creating,
                creating = state.creating,
                onCreate = {
                    scope.launch {
                        if (repository.createPatient(displayName)) {
                            displayName = ""
                            analyticsService?.logCoreActionCompleted(AnalyticsCoreAction.CAREGIVER_PATIENT_CREATED)
                        }
                    }
                },
            )
        }
        if (state.patients.isNotEmpty()) item {
            CaregiverPatientSelectionCard(
                state = state,
                enabled = patientActionsEnabled,
                onSelect = repository::selectPatient,
            )
        }
        if (selectedPatient != null) item {
            CaregiverSelectedPatientCard(
                patientName = selectedPatient.displayName,
                issuing = state.issuingLinkingCode,
                enabled = patientActionsEnabled,
                destructiveActionInProgress = state.destructiveActionInProgress,
                linkingCodeFailed = state.linkingCodeFailed,
                onIssue = { scope.launch {
                    if (repository.issueLinkingCode()) analyticsService?.logCoreActionCompleted(AnalyticsCoreAction.LINK_CODE_ISSUED)
                } },
                onRevoke = { confirmation = "revoke" },
                onDelete = { confirmation = "delete" },
            )
        }
        if (selectedPatient != null) item {
            CaregiverSlotTimesEntryCard(
                enabled = patientActionsEnabled,
                onOpen = { showingSlotTimes = true },
            )
        }
        if (pushRepository != null && pushState != null) item {
            Card(
                Modifier.fillMaxWidth().testTag("caregiver-push-settings"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CaregiverSettingsSectionHeader(
                        title = stringResource(R.string.caregiver_push_title),
                        message = stringResource(
                            if (pushState.enabled) R.string.caregiver_push_enabled
                            else R.string.caregiver_push_description,
                        ),
                        icon = Icons.Rounded.Notifications,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.patient_settings_notifications), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = pushState.enabled,
                            enabled = enabled && !pushState.syncing,
                            onCheckedChange = { checked ->
                                if (!checked) {
                                    scope.launch { pushRepository.disable() }
                                } else if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    scope.launch { pushRepository.enable() }
                                } else {
                                    pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("caregiver-push-switch"),
                        )
                    }
                    when {
                        pushState.syncing -> Text(stringResource(R.string.caregiver_push_syncing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        pushState.configurationMissing -> Text(stringResource(R.string.caregiver_push_configuration_missing), color = MaterialTheme.colorScheme.error)
                        pushState.syncFailed -> {
                            Text(stringResource(R.string.caregiver_push_sync_failed), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(
                                onClick = { scope.launch { pushRepository.retry() } },
                                enabled = enabled,
                                modifier = Modifier.testTag("caregiver-push-retry"),
                            ) { Text(stringResource(R.string.common_retry)) }
                        }
                        pushState.registered -> Text(stringResource(R.string.caregiver_push_enabled), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (analyticsService != null && analyticsState != null) item {
            Card(
                Modifier.fillMaxWidth().testTag("caregiver-analytics-settings"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaregiverSettingsSectionHeader(
                        title = stringResource(R.string.patient_settings_analytics_title),
                        message = stringResource(R.string.analytics_settings_message),
                        icon = Icons.Rounded.BarChart,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.patient_settings_analytics_toggle), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = analyticsState.enabled,
                            enabled = enabled,
                            onCheckedChange = analyticsService::setCollectionEnabled,
                            modifier = Modifier.testTag("caregiver-analytics-toggle"),
                        )
                    }
                    Text(stringResource(R.string.patient_settings_analytics_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().testTag("caregiver-legal-support"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaregiverSettingsSectionHeader(
                        title = stringResource(R.string.caregiver_legal_title),
                        message = stringResource(R.string.caregiver_legal_message),
                        icon = Icons.Rounded.Description,
                    )
                    CaregiverLegalRow(
                        stringResource(R.string.patient_settings_privacy),
                        stringResource(R.string.patient_settings_privacy_detail),
                        Icons.Rounded.PrivacyTip,
                        MedicationTheme.colors.primaryTealText,
                        "caregiver-privacy-link",
                    ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.okusuri-mimamori.com/privacy"))) }
                    CaregiverLegalRow(
                        stringResource(R.string.patient_settings_terms),
                        stringResource(R.string.patient_settings_terms_detail),
                        Icons.Rounded.Description,
                        MedicationTheme.colors.caregiverBlue,
                        "caregiver-terms-link",
                    ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.okusuri-mimamori.com/terms"))) }
                    CaregiverLegalRow(
                        stringResource(R.string.patient_settings_support),
                        stringResource(R.string.patient_settings_support_detail),
                        Icons.AutoMirrored.Rounded.Help,
                        MedicationTheme.colors.orange,
                        "caregiver-support-link",
                    ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.okusuri-mimamori.com/support"))) }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().testTag("caregiver-account-actions"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaregiverSettingsSectionHeader(
                        title = stringResource(R.string.caregiver_account_title),
                        message = stringResource(R.string.caregiver_account_message),
                        icon = Icons.Rounded.AccountCircle,
                    )
                    Button(
                        onClick = { confirmation = "logout" },
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MedicationTheme.colors.caregiverRed),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("caregiver-logout"),
                    ) {
                        Text(stringResource(R.string.caregiver_account_logout))
                    }
                    OutlinedButton(
                        onClick = { confirmation = "account" },
                        enabled = enabled && !state.destructiveActionInProgress,
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("caregiver-account-delete"),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.caregiver_account_delete), color = MaterialTheme.colorScheme.error)
                    }
                    if (state.destructiveActionFailed) Text(stringResource(R.string.caregiver_destructive_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        }
        }
        if (updating) CaregiverSettingsUpdatingOverlay()
    }
    state.linkingCode?.let { code ->
        ModalBottomSheet(
            onDismissRequest = repository::dismissLinkingCode,
            sheetState = linkingCodeSheetState,
            modifier = Modifier.testTag("caregiver-linking-code-sheet"),
        ) {
            CaregiverLinkingCodeSheet(code)
        }
    }
    if (showingSlotTimes && selectedPatient != null) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.savingSlotTimes) showingSlotTimes = false },
            sheetState = slotTimesSheetState,
            modifier = Modifier.testTag("caregiver-slot-times-sheet"),
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.9f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp)
                    .testTag("caregiver-slot-times-sheet-content"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.caregiver_slot_times_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                CaregiverSlotTimesCard(
                    morning = morning,
                    noon = noon,
                    evening = evening,
                    bedtime = bedtime,
                    enabled = patientActionsEnabled && !state.savingSlotTimes,
                    saveFailed = state.slotTimesSaveFailed,
                    onMorning = { morning = it },
                    onNoon = { noon = it },
                    onEvening = { evening = it },
                    onBedtime = { bedtime = it },
                    onSave = {
                        scope.launch {
                            if (repository.updateSelectedPatientSlotTimes(CaregiverSlotTimes(morning, noon, evening, bedtime))) {
                                showingSlotTimes = false
                            }
                        }
                    },
                )
            }
        }
    }
    confirmation?.let { action ->
        val account = action == "account"
        val revoke = action == "revoke"
        val logout = action == "logout"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(if (logout) R.string.caregiver_logout_confirm_title else if (account) R.string.caregiver_account_delete_confirm_title else if (revoke) R.string.caregiver_patient_revoke_confirm_title else R.string.caregiver_patient_delete_confirm_title)) },
            text = { Text(stringResource(if (logout) R.string.caregiver_logout_confirm_message else if (account) R.string.caregiver_account_delete_confirm_message else if (revoke) R.string.caregiver_patient_revoke_confirm_message else R.string.caregiver_patient_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        if (logout) onLogout() else scope.launch {
                            val success = when (action) {
                                "revoke" -> repository.revokeSelectedPatient()
                                "delete" -> repository.deleteSelectedPatient()
                                else -> repository.deleteCaregiverAccount()
                            }
                            if (account && success) onAccountDeleted()
                        }
                    },
                    enabled = logout || account || patientActionsEnabled,
                ) { Text(stringResource(if (logout) R.string.caregiver_account_logout else if (account) R.string.caregiver_account_delete else if (revoke) R.string.caregiver_patient_revoke_confirm else R.string.caregiver_patient_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CaregiverPatientSelectionCard(
    state: CaregiverPatientState,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CaregiverSettingsSectionHeader(
                title = stringResource(R.string.caregiver_settings_patient_title),
                message = stringResource(
                    if (state.patients.size > 1) R.string.caregiver_settings_patient_multiple_message
                    else R.string.caregiver_settings_patient_single_message,
                ),
                icon = Icons.Rounded.Group,
            )
            state.patients.forEach { patient ->
                val selected = patient.id == state.selectedPatientId
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MedicationTheme.colors.primaryTealText.copy(alpha = if (selected) 0.10f else 0.04f))
                        .selectable(selected, enabled = enabled) { onSelect(patient.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("caregiver-patient-${patient.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        patient.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MedicationTheme.colors.primaryTealText else MaterialTheme.colorScheme.onSurface,
                    )
                    if (selected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.caregiver_patient_selected),
                            tint = MedicationTheme.colors.primaryTealText,
                        )
                    } else {
                        RadioButton(selected = false, onClick = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaregiverSelectedPatientCard(
    patientName: String,
    issuing: Boolean,
    enabled: Boolean,
    destructiveActionInProgress: Boolean,
    linkingCodeFailed: Boolean,
    onIssue: () -> Unit,
    onRevoke: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().testTag("caregiver-patient-danger"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.primaryTealText.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MedicationTheme.colors.primaryTealText,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    patientName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.caregiver_patient_selected),
                    color = MedicationTheme.colors.primaryTealText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clip(CircleShape)
                        .background(MedicationTheme.colors.primaryTealText.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxWidth().testTag("caregiver-linking-code")) {
                CaregiverSettingsActionButton(
                    title = stringResource(if (issuing) R.string.caregiver_linking_code_issuing else R.string.caregiver_linking_code_issue),
                    icon = Icons.Rounded.Link,
                    tint = MedicationTheme.colors.primaryTealText,
                    enabled = enabled && !issuing,
                    modifier = Modifier.testTag("caregiver-linking-code-issue"),
                    onClick = onIssue,
                )
            }
            if (linkingCodeFailed) {
                Text(stringResource(R.string.caregiver_linking_code_failed), color = MaterialTheme.colorScheme.error)
            }
            CaregiverSettingsActionButton(
                title = stringResource(R.string.caregiver_patient_revoke),
                icon = Icons.Rounded.LinkOff,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                enabled = enabled && !destructiveActionInProgress,
                modifier = Modifier.testTag("caregiver-patient-revoke"),
                onClick = onRevoke,
            )
            CaregiverSettingsActionButton(
                title = stringResource(R.string.caregiver_patient_delete),
                icon = Icons.Rounded.Delete,
                tint = MedicationTheme.colors.caregiverRed,
                enabled = enabled && !destructiveActionInProgress,
                modifier = Modifier.testTag("caregiver-patient-delete"),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CaregiverSettingsActionButton(
    title: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.size(8.dp))
        Text(title, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaregiverSlotTimesEntryCard(enabled: Boolean, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag("caregiver-detail-settings"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CaregiverSettingsSectionHeader(
                title = stringResource(R.string.caregiver_detail_settings_title),
                message = stringResource(R.string.caregiver_detail_settings_message),
                icon = Icons.Rounded.Tune,
            )
            TextButton(
                onClick = onOpen,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("caregiver-slot-times"),
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(MedicationTheme.colors.caregiverBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.caregiver_slot_times_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.caregiver_slot_times_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CaregiverSettingsHeader(patientName: String?) {
    Row(
        Modifier.fillMaxWidth().testTag("caregiver-settings-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(62.dp).shadow(8.dp, CircleShape).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (patientName.isNullOrBlank()) {
                Icon(
                    Icons.Rounded.Group,
                    contentDescription = null,
                    tint = MedicationTheme.colors.primaryTealText,
                    modifier = Modifier.size(38.dp),
                )
            } else {
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(MedicationTheme.colors.primaryTealText),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(patientName.take(1), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.caregiver_settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                patientName?.let { stringResource(R.string.caregiver_settings_patient_name, it) }
                    ?: stringResource(R.string.caregiver_settings_patient_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CaregiverSettingsLoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 96.dp).testTag("caregiver-settings-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(52.dp), color = MedicationTheme.colors.primaryTealText)
        Text(
            stringResource(R.string.patient_today_loading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CaregiverSettingsEmptyState(modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.caregiver_no_patient_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.caregiver_no_patient_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CaregiverSettingsOnboardingStep(1, stringResource(R.string.caregiver_empty_step_register))
            CaregiverSettingsOnboardingStep(2, stringResource(R.string.caregiver_empty_step_code))
            CaregiverSettingsOnboardingStep(3, stringResource(R.string.caregiver_empty_step_share))
        }
    }
}

@Composable
private fun CaregiverSettingsOnboardingStep(number: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(MedicationTheme.colors.caregiverBlue.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = MedicationTheme.colors.caregiverBlue, fontWeight = FontWeight.Bold)
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaregiverCreatePatientCard(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    error: CaregiverCreateError?,
    enabled: Boolean,
    creating: Boolean,
    onCreate: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.caregiverBlue.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CaregiverSettingsSectionHeader(
                title = stringResource(R.string.caregiver_create_patient),
                message = stringResource(R.string.caregiver_create_patient_message),
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                enabled = enabled,
                label = { Text(stringResource(R.string.caregiver_patient_display_name)) },
                supportingText = {
                    error?.let {
                        Text(caregiverCreateErrorText(it), modifier = Modifier.testTag("caregiver-create-error"))
                    }
                },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("caregiver-create-name"),
            )
            Button(
                onClick = onCreate,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("caregiver-create-submit"),
            ) {
                Text(stringResource(if (creating) R.string.caregiver_creating_patient else R.string.caregiver_create_patient_action))
            }
        }
    }
}

@Composable
private fun CaregiverSettingsSectionHeader(
    title: String,
    message: String,
    icon: ImageVector = Icons.Rounded.Person,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(MedicationTheme.colors.primaryTealText.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MedicationTheme.colors.primaryTealText,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaregiverSettingsUpdatingOverlay() {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))
            .clickable(onClick = {})
            .testTag("caregiver-settings-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), color = MedicationTheme.colors.primaryTealText)
                Text(
                    stringResource(R.string.patient_today_updating),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun CaregiverTab.analyticsValue(): AnalyticsCaregiverTab = when (this) {
    CaregiverTab.TODAY -> AnalyticsCaregiverTab.TODAY
    CaregiverTab.MEDICATIONS -> AnalyticsCaregiverTab.MEDICATIONS
    CaregiverTab.INVENTORY -> AnalyticsCaregiverTab.INVENTORY
    CaregiverTab.HISTORY -> AnalyticsCaregiverTab.HISTORY
    CaregiverTab.SETTINGS -> AnalyticsCaregiverTab.SETTINGS
}

@Composable
private fun CaregiverLegalRow(
    title: String,
    message: String,
    icon: ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CaregiverLinkingCodeSheet(code: CaregiverLinkingCode) {
    val context = LocalContext.current
    val expiry = formatLinkingCodeExpiry(code)
    var copied by rememberSaveable(code.code) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp).testTag("caregiver-linking-code-content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.caregiver_linking_code_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.caregiver_linking_code_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.fillMaxWidth().testTag("caregiver-linking-code-value"),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            code.code.forEach { character ->
                Box(
                    Modifier.size(width = 44.dp, height = 56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(character.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { copyLinkingCode(context, code.code); copied = true },
                modifier = Modifier.weight(1f).height(48.dp).testTag("caregiver-linking-code-copy"),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.caregiver_linking_code_copy))
            }
            OutlinedButton(
                onClick = { shareLinkingCode(context, code, expiry) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("caregiver-linking-code-share"),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.caregiver_linking_code_share))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.caregiver_linking_code_expires, expiry), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (copied) {
        AlertDialog(
            onDismissRequest = { copied = false },
            text = { Text(stringResource(R.string.caregiver_linking_code_copied)) },
            confirmButton = { TextButton(onClick = { copied = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

private fun formatLinkingCodeExpiry(code: CaregiverLinkingCode): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy/MM/dd H:mm", Locale.JAPANESE)
        .withZone(ZoneId.of("Asia/Tokyo"))
        .format(Instant.parse(code.expiresAt))
}.getOrDefault(code.expiresAt)

private fun copyLinkingCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.caregiver_linking_code_title), code))
}

private fun shareLinkingCode(context: Context, code: CaregiverLinkingCode, expiry: String) {
    val message = context.getString(R.string.caregiver_linking_code_share_message, code.code, expiry)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.caregiver_linking_code_share)))
}

@Composable
private fun CaregiverSlotTimesCard(
    morning: String,
    noon: String,
    evening: String,
    bedtime: String,
    enabled: Boolean,
    saveFailed: Boolean,
    onMorning: (String) -> Unit,
    onNoon: (String) -> Unit,
    onEvening: (String) -> Unit,
    onBedtime: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().testTag("caregiver-slot-times"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.caregiver_slot_times_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.caregiver_slot_times_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CaregiverTimeRow(R.string.caregiver_slot_morning, morning, enabled, onMorning)
            CaregiverTimeRow(R.string.caregiver_slot_noon, noon, enabled, onNoon)
            CaregiverTimeRow(R.string.caregiver_slot_evening, evening, enabled, onEvening)
            CaregiverTimeRow(R.string.caregiver_slot_bedtime, bedtime, enabled, onBedtime)
            if (saveFailed) Text(stringResource(R.string.caregiver_slot_times_save_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("caregiver-slot-times-save")) {
                Text(stringResource(R.string.caregiver_slot_times_save))
            }
        }
    }
}

@Composable
private fun CaregiverTimeRow(label: Int, value: String, enabled: Boolean, onValue: (String) -> Unit) {
    val context = LocalContext.current
    val parts = value.split(':').mapNotNull(String::toIntOrNull)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onValue("%02d:%02d".format(hour, minute)) },
                    parts.getOrElse(0) { 0 },
                    parts.getOrElse(1) { 0 },
                    true,
                ).show()
            },
            enabled = enabled,
            modifier = Modifier.testTag("caregiver-time-${stringResource(label)}"),
        ) { Text(value) }
    }
}

@Composable
private fun caregiverCreateErrorText(error: CaregiverCreateError): String = stringResource(
    when (error) {
        CaregiverCreateError.REQUIRED -> R.string.caregiver_create_required
        CaregiverCreateError.TOO_LONG -> R.string.caregiver_create_too_long
        CaregiverCreateError.PATIENT_LIMIT -> R.string.caregiver_create_limit
        CaregiverCreateError.FAILED -> R.string.common_error_generic
    },
)

@Composable
private fun CaregiverMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
