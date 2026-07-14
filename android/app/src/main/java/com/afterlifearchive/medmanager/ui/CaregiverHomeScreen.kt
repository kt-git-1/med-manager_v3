package com.afterlifearchive.medmanager.ui

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverCreateError
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.caregiver.CaregiverLinkingCode
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
fun CaregiverHomeScreen(repository: CaregiverPatientRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    var selectedTabName by rememberSaveable { mutableStateOf(CaregiverTab.TODAY.name) }
    var loadedTabNames by rememberSaveable { mutableStateOf(setOf(CaregiverTab.TODAY.name)) }
    val selectedTab = CaregiverTab.valueOf(selectedTabName)

    LaunchedEffect(Unit) { repository.refresh() }

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
                        CaregiverTabContent(tab, state, repository, visible)
                    }
                }
            }
        }
        NavigationBar {
            CaregiverTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selectedTab,
                    onClick = {
                        selectedTabName = tab.name
                        loadedTabNames = loadedTabNames + tab.name
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                    modifier = Modifier.testTag("caregiver-tab-${tab.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun CaregiverTabContent(
    tab: CaregiverTab,
    state: CaregiverPatientState,
    repository: CaregiverPatientRepository,
    visible: Boolean,
) {
    when (tab) {
        CaregiverTab.SETTINGS -> CaregiverPatientSelectionScreen(state, repository, visible)
        else -> CaregiverFeatureLanding(tab, state, repository, visible)
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
    enabled: Boolean,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selectedPatient = state.selectedPatient
    var morning by rememberSaveable { mutableStateOf("08:00") }
    var noon by rememberSaveable { mutableStateOf("12:00") }
    var evening by rememberSaveable { mutableStateOf("18:00") }
    var bedtime by rememberSaveable { mutableStateOf("21:00") }
    LaunchedEffect(selectedPatient?.id, selectedPatient?.slotTimes) {
        val times = selectedPatient?.slotTimes ?: CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")
        morning = times.morning
        noon = times.noon
        evening = times.evening
        bedtime = times.bedtime
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("caregiver-settings-list"),
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
        item { Spacer(Modifier.height(20.dp)) }
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
