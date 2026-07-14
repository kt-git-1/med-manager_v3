package com.afterlifearchive.medmanager.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
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
    onLogout: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
    tutorialEnabled: Boolean = true,
    requestNotificationPermission: (() -> Unit)? = null,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        selectedTabName = tab.name
        loadedTabNames = loadedTabNames + tab.name
    }

    fun finishTutorial(openRegistration: Boolean) {
        tutorialPreferences.edit().putBoolean("seen", true).apply()
        tutorialStep = -1
        if (openRegistration) {
            selectTab(CaregiverTab.SETTINGS)
            postTutorialFocusTag = "caregiver-create-name"
        }
    }

    LaunchedEffect(Unit) { repository.refresh() }
    LaunchedEffect(historyState?.navigationRequestId, state.patients) {
        val targetPatientId = historyState?.notificationPatientId
        if ((historyState?.navigationRequestId ?: 0) > 0 && targetPatientId != null && state.patients.any { it.id == targetPatientId }) {
            repository.selectPatient(targetPatientId)
            selectTab(CaregiverTab.HISTORY)
        }
    }
    LaunchedEffect(tutorialStep) {
        if (tutorialStep >= 0) selectTab(caregiverTutorialTab(tutorialStep))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("caregiver-home")) {
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
    visible: Boolean,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    tutorialFocusTag: String?,
    onOpenMedications: () -> Unit,
) {
    when (tab) {
        CaregiverTab.SETTINGS -> CaregiverPatientSelectionScreen(state, repository, pushRepository, visible, onLogout, onAccountDeleted, tutorialFocusTag)
        CaregiverTab.MEDICATIONS -> if (medicationRepository != null) {
            CaregiverMedicationScreen(
                repository = medicationRepository,
                patientState = state,
                enabled = visible,
            )
        } else CaregiverFeatureLanding(tab, state, repository, visible)
        CaregiverTab.TODAY -> if (todayRepository != null) {
            CaregiverTodayScreen(todayRepository, state, visible, onOpenMedications)
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
private fun CaregiverPatientSelectionScreen(
    state: CaregiverPatientState,
    repository: CaregiverPatientRepository,
    pushRepository: CaregiverPushRepository?,
    enabled: Boolean,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    tutorialFocusTag: String?,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pushState = pushRepository?.state?.collectAsStateWithLifecycle()?.value
    val pushPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { pushRepository?.enable() }
    }
    val selectedPatient = state.selectedPatient
    var morning by rememberSaveable { mutableStateOf("08:00") }
    var noon by rememberSaveable { mutableStateOf("12:00") }
    var evening by rememberSaveable { mutableStateOf("18:00") }
    var bedtime by rememberSaveable { mutableStateOf("21:00") }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedPatient?.id, selectedPatient?.slotTimes) {
        val times = selectedPatient?.slotTimes ?: CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")
        morning = times.morning
        noon = times.noon
        evening = times.evening
        bedtime = times.bedtime
    }
    LaunchedEffect(tutorialFocusTag, state.patients.size, selectedPatient?.id) {
        val statusRows = if ((state.loading && state.patients.isEmpty()) || state.loadFailed || (!state.loading && state.patients.isEmpty())) 1 else 0
        val slotIndex = 2 + statusRows + state.patients.size
        val targetIndex = when (tutorialFocusTag) {
            "caregiver-create-name" -> 1
            "caregiver-slot-times" -> if (selectedPatient != null) slotIndex else 1
            "caregiver-linking-code" -> if (selectedPatient != null) slotIndex + 1 else 1
            else -> null
        }
        if (targetIndex != null) listState.animateScrollToItem(targetIndex)
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("caregiver-settings-list"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.caregiver_settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.caregiver_settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.caregiver_create_patient), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        enabled = enabled && !state.creating,
                        label = { Text(stringResource(R.string.caregiver_patient_display_name)) },
                        supportingText = {
                            state.createError?.let { Text(caregiverCreateErrorText(it)) }
                        },
                        isError = state.createError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-create-name"),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                if (repository.createPatient(displayName)) displayName = ""
                            }
                        },
                        enabled = enabled && !state.creating,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-create-submit"),
                    ) {
                        Text(stringResource(if (state.creating) R.string.caregiver_creating_patient else R.string.caregiver_create_patient_action))
                    }
                }
            }
        }
        if (state.loading && state.patients.isEmpty()) item { CircularProgressIndicator() }
        if (state.loadFailed) item {
            CaregiverMessage(
                stringResource(R.string.caregiver_data_unavailable_title),
                stringResource(R.string.caregiver_data_unavailable_message),
            )
        }
        if (!state.loading && !state.loadFailed && state.patients.isEmpty()) item {
            Box(Modifier.testTag("caregiver-settings-empty")) {
                CaregiverMessage(
                    stringResource(R.string.caregiver_no_patient_title),
                    stringResource(R.string.caregiver_no_patient_message),
                )
            }
        }
        items(state.patients, key = { it.id }) { patient ->
            val selected = patient.id == state.selectedPatientId
            Card(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected, enabled = enabled) { repository.selectPatient(patient.id) }
                    .testTag("caregiver-patient-${patient.id}"),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(patient.displayName, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (selected) R.string.caregiver_patient_selected else R.string.caregiver_patient_select),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
        if (selectedPatient != null) item {
            CaregiverSlotTimesCard(
                morning = morning,
                noon = noon,
                evening = evening,
                bedtime = bedtime,
                enabled = enabled && !state.savingSlotTimes,
                saveFailed = state.slotTimesSaveFailed,
                onMorning = { morning = it },
                onNoon = { noon = it },
                onEvening = { evening = it },
                onBedtime = { bedtime = it },
                onSave = {
                    scope.launch {
                        repository.updateSelectedPatientSlotTimes(CaregiverSlotTimes(morning, noon, evening, bedtime))
                    }
                },
            )
        }
        if (selectedPatient != null) item {
            CaregiverLinkingCodeCard(
                code = state.linkingCode,
                issuing = state.issuingLinkingCode,
                failed = state.linkingCodeFailed,
                enabled = enabled,
                onIssue = { scope.launch { repository.issueLinkingCode() } },
            )
        }
        if (selectedPatient != null) item {
            Card(Modifier.fillMaxWidth().testTag("caregiver-patient-danger")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.caregiver_patient_danger_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = { confirmation = "revoke" },
                        enabled = enabled && !state.destructiveActionInProgress,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-patient-revoke"),
                    ) { Text(stringResource(R.string.caregiver_patient_revoke)) }
                    OutlinedButton(
                        onClick = { confirmation = "delete" },
                        enabled = enabled && !state.destructiveActionInProgress,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-patient-delete"),
                    ) { Text(stringResource(R.string.caregiver_patient_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (pushRepository != null && pushState != null) item {
            Card(Modifier.fillMaxWidth().testTag("caregiver-push-settings")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.caregiver_push_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_push_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
        item {
            Card(Modifier.fillMaxWidth().testTag("caregiver-account-actions")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.caregiver_account_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onLogout, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("caregiver-logout")) {
                        Text(stringResource(R.string.caregiver_account_logout))
                    }
                    OutlinedButton(
                        onClick = { confirmation = "account" },
                        enabled = enabled && !state.destructiveActionInProgress,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver-account-delete"),
                    ) { Text(stringResource(R.string.caregiver_account_delete), color = MaterialTheme.colorScheme.error) }
                    if (state.destructiveActionFailed) Text(stringResource(R.string.caregiver_destructive_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    confirmation?.let { action ->
        val account = action == "account"
        val revoke = action == "revoke"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(if (account) R.string.caregiver_account_delete_confirm_title else if (revoke) R.string.caregiver_patient_revoke_confirm_title else R.string.caregiver_patient_delete_confirm_title)) },
            text = { Text(stringResource(if (account) R.string.caregiver_account_delete_confirm_message else if (revoke) R.string.caregiver_patient_revoke_confirm_message else R.string.caregiver_patient_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    scope.launch {
                        val success = when (action) {
                            "revoke" -> repository.revokeSelectedPatient()
                            "delete" -> repository.deleteSelectedPatient()
                            else -> repository.deleteCaregiverAccount()
                        }
                        if (account && success) onAccountDeleted()
                    }
                }) { Text(stringResource(if (account) R.string.caregiver_account_delete else if (revoke) R.string.caregiver_patient_revoke_confirm else R.string.caregiver_patient_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CaregiverLinkingCodeCard(
    code: CaregiverLinkingCode?,
    issuing: Boolean,
    failed: Boolean,
    enabled: Boolean,
    onIssue: () -> Unit,
) {
    val context = LocalContext.current
    val expiry = code?.let(::formatLinkingCodeExpiry).orEmpty()
    Card(Modifier.fillMaxWidth().testTag("caregiver-linking-code")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.caregiver_linking_code_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.caregiver_linking_code_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (code != null) {
                Text(code.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("caregiver-linking-code-value"))
                Text(stringResource(R.string.caregiver_linking_code_expires, expiry), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copyLinkingCode(context, code.code) },
                        modifier = Modifier.weight(1f).testTag("caregiver-linking-code-copy"),
                    ) { Text(stringResource(R.string.caregiver_linking_code_copy)) }
                    Button(
                        onClick = { shareLinkingCode(context, code, expiry) },
                        modifier = Modifier.weight(1f).testTag("caregiver-linking-code-share"),
                    ) { Text(stringResource(R.string.caregiver_linking_code_share)) }
                }
            }
            if (failed) Text(stringResource(R.string.caregiver_linking_code_failed), color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = onIssue,
                enabled = enabled && !issuing,
                modifier = Modifier.fillMaxWidth().testTag("caregiver-linking-code-issue"),
            ) {
                Text(stringResource(if (issuing) R.string.caregiver_linking_code_issuing else R.string.caregiver_linking_code_issue))
            }
        }
    }
}

private fun formatLinkingCodeExpiry(code: CaregiverLinkingCode): String = runCatching {
    DateTimeFormatter.ofPattern("M/d HH:mm", Locale.JAPANESE)
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
    Card(Modifier.fillMaxWidth().testTag("caregiver-slot-times")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.caregiver_slot_times_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.caregiver_slot_times_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CaregiverTimeRow(R.string.patient_slot_morning, morning, enabled, onMorning)
            CaregiverTimeRow(R.string.patient_slot_noon, noon, enabled, onNoon)
            CaregiverTimeRow(R.string.patient_slot_evening, evening, enabled, onEvening)
            CaregiverTimeRow(R.string.patient_slot_bedtime, bedtime, enabled, onBedtime)
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
