package com.afterlifearchive.medmanager.ui

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
        item { Spacer(Modifier.height(20.dp)) }
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
