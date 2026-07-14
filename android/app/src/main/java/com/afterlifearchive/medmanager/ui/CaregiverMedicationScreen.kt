package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDraft
import com.afterlifearchive.medmanager.data.caregiver.CaregiverScheduleDay
import com.afterlifearchive.medmanager.data.caregiver.CaregiverScheduleFrequency
import com.afterlifearchive.medmanager.data.caregiver.CaregiverScheduleSlot
import com.afterlifearchive.medmanager.data.caregiver.validate
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch
import android.app.DatePickerDialog

private enum class CaregiverMedicationFilter(val label: Int) {
    ALL(R.string.caregiver_medication_filter_all),
    SCHEDULED(R.string.caregiver_medication_filter_scheduled),
    PRN(R.string.caregiver_medication_filter_prn),
    ENDED(R.string.caregiver_medication_filter_ended),
}

@Composable
internal fun CaregiverMedicationScreen(
    repository: CaregiverMedicationRepository,
    patientState: CaregiverPatientState,
    enabled: Boolean,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val freshness by repository.freshness.collectAsStateWithLifecycle()
    val selected = patientState.selectedPatient
    val cursor = androidx.compose.runtime.remember(repository) { repository.newFreshnessCursor() }
    val scope = rememberCoroutineScope()
    var filterName by rememberSaveable { mutableStateOf(CaregiverMedicationFilter.ALL.name) }
    var editingMedicationId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingMedication by rememberSaveable { mutableStateOf(false) }
    val filter = CaregiverMedicationFilter.valueOf(filterName)

    LaunchedEffect(enabled, selected?.id, freshness.medication, freshness.inventory, freshness.slotTimes) {
        if (enabled && selected != null) cursor.refreshIfStale { repository.load(selected.id) }
        if (selected == null) repository.clear()
    }

    val editingMedication = state.items.firstOrNull { it.id == editingMedicationId }
    if (selected != null && (addingMedication || editingMedication != null)) {
        CaregiverMedicationEditor(
            patientId = selected.id,
            medication = editingMedication,
            slotTimes = selected.slotTimes,
            repository = repository,
            enabled = enabled,
            onClose = {
                addingMedication = false
                editingMedicationId = null
            },
        )
        return
    }

    when {
        patientState.loading && patientState.patients.isEmpty() -> CaregiverMedicationCentered { CircularProgressIndicator() }
        patientState.loadFailed -> CaregiverMedicationMessage(
            stringResource(R.string.caregiver_data_unavailable_title),
            stringResource(R.string.caregiver_data_unavailable_message),
        )
        patientState.patients.isEmpty() -> CaregiverMedicationMessage(
            stringResource(R.string.caregiver_no_patient_title),
            stringResource(R.string.caregiver_no_patient_message),
        )
        selected == null -> CaregiverMedicationMessage(
            stringResource(R.string.caregiver_no_selection_title),
            stringResource(R.string.caregiver_no_selection_message),
        )
        state.loading -> CaregiverMedicationCentered { CircularProgressIndicator() }
        state.loadFailed -> CaregiverMedicationMessage(
            stringResource(R.string.caregiver_data_unavailable_title),
            stringResource(R.string.caregiver_data_unavailable_message),
        ) {
            Button(onClick = { scope.launch { repository.load(selected.id) } }, enabled = enabled) {
                Text(stringResource(R.string.common_retry))
            }
        }
        else -> CaregiverMedicationList(
            items = state.items,
            patientName = selected.displayName,
            slotTimes = selected.slotTimes,
            filter = filter,
            onFilter = { filterName = it.name },
            onAdd = { addingMedication = true },
            onEdit = { editingMedicationId = it.id },
            enabled = enabled,
        )
    }
}

@Composable
private fun CaregiverMedicationList(
    items: List<PatientMedication>,
    patientName: String,
    slotTimes: CaregiverSlotTimes?,
    filter: CaregiverMedicationFilter,
    onFilter: (CaregiverMedicationFilter) -> Unit,
    onAdd: () -> Unit,
    onEdit: (PatientMedication) -> Unit,
    enabled: Boolean,
) {
    val today = LocalDate.now(TOKYO)
    fun ended(item: PatientMedication) = item.endDate?.atZone(TOKYO)?.toLocalDate()?.isBefore(today) == true || item.isArchived
    val visible = items.filter { item ->
        when (filter) {
            CaregiverMedicationFilter.ALL -> true
            CaregiverMedicationFilter.SCHEDULED -> !item.isPrn && !ended(item)
            CaregiverMedicationFilter.PRN -> item.isPrn && !ended(item)
            CaregiverMedicationFilter.ENDED -> ended(item)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("caregiver-medication-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.caregiver_medication_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onAdd, enabled = enabled, modifier = Modifier.testTag("caregiver-medication-add")) {
                    Text(stringResource(R.string.caregiver_medication_add))
                }
            }
            Text(stringResource(R.string.caregiver_medication_patient, patientName), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaregiverMetric(stringResource(R.string.caregiver_medication_metric_scheduled), items.count { !it.isPrn && !ended(it) }, Modifier.weight(1f))
                CaregiverMetric(stringResource(R.string.caregiver_medication_metric_prn), items.count { it.isPrn && !ended(it) }, Modifier.weight(1f))
                CaregiverMetric(stringResource(R.string.caregiver_medication_metric_ended), items.count(::ended), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaregiverMedicationFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { onFilter(item) },
                        label = { Text(stringResource(item.label)) },
                        modifier = Modifier.testTag("caregiver-medication-filter-${item.name.lowercase()}"),
                    )
                }
            }
        }
        if (items.isEmpty()) item {
            CaregiverMedicationMessage(
                stringResource(R.string.caregiver_medication_empty_title),
                stringResource(R.string.caregiver_medication_empty_message),
            )
        } else if (visible.isEmpty()) item {
            CaregiverMedicationMessage(
                stringResource(R.string.caregiver_medication_filter_empty_title),
                stringResource(R.string.caregiver_medication_filter_empty_message),
            )
        }
        items(visible, key = { it.id }) { medication ->
            CaregiverMedicationCard(medication, slotTimes, ended(medication), { onEdit(medication) }, enabled)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CaregiverMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaregiverMedicationCard(
    item: PatientMedication,
    slotTimes: CaregiverSlotTimes?,
    ended: Boolean,
    onEdit: () -> Unit,
    enabled: Boolean,
) {
    Card(Modifier.fillMaxWidth().testTag("caregiver-medication-${item.id}")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedicationBadge(stringResource(if (item.isPrn) R.string.caregiver_medication_type_prn else R.string.caregiver_medication_type_scheduled))
                if (ended) MedicationBadge(stringResource(R.string.caregiver_medication_type_ended))
            }
            item.dosageText.trim().takeUnless { it.isEmpty() || it == "不明" }?.let {
                Text(stringResource(R.string.caregiver_medication_dosage, it))
            }
            Text(stringResource(R.string.caregiver_medication_dose, formatMedicationNumber(item.doseCountPerIntake)))
            medicationSchedule(item, slotTimes)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (item.inventoryEnabled) {
                Text(
                    if (item.inventoryOut) stringResource(R.string.caregiver_medication_inventory_out)
                    else stringResource(
                        R.string.caregiver_medication_inventory_remaining,
                        formatMedicationNumber(item.inventoryQuantity),
                        item.inventoryUnit ?: stringResource(R.string.caregiver_medication_inventory_unit),
                    ),
                    color = if (item.inventoryOut || item.isInsufficientForDose) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("caregiver-medication-edit-${item.id}")) {
                Text(stringResource(R.string.caregiver_medication_edit))
            }
        }
    }
}

@Composable
private fun CaregiverMedicationEditor(
    patientId: String,
    medication: PatientMedication?,
    slotTimes: CaregiverSlotTimes?,
    repository: CaregiverMedicationRepository,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    var draft by remember(medication?.id) {
        mutableStateOf(medication?.let { CaregiverMedicationDraft.from(it, slotTimes) } ?: CaregiverMedicationDraft())
    }
    var submitted by rememberSaveable(medication?.id) { mutableStateOf(false) }
    var submitting by rememberSaveable(medication?.id) { mutableStateOf(false) }
    var saveFailed by rememberSaveable(medication?.id) { mutableStateOf(false) }
    var showingDeleteConfirm by rememberSaveable(medication?.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(medication?.id) { mutableStateOf(false) }
    var deleteFailed by rememberSaveable(medication?.id) { mutableStateOf(false) }
    val errors = if (submitted) draft.validate() else emptyList()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun pickDate(initial: LocalDate, onDate: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).show()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("caregiver-medication-form"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, enabled = !submitting) { Text(stringResource(R.string.caregiver_medication_form_cancel)) }
                Text(
                    stringResource(if (medication == null) R.string.caregiver_medication_add_title else R.string.caregiver_medication_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        item {
            FormSection(stringResource(R.string.caregiver_medication_form_kind)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !draft.isPrn,
                        onClick = { draft = draft.copy(isPrn = false) },
                        label = { Text(stringResource(R.string.caregiver_medication_type_scheduled)) },
                        modifier = Modifier.testTag("medication-kind-scheduled"),
                    )
                    FilterChip(
                        selected = draft.isPrn,
                        onClick = {
                            draft = draft.copy(
                                isPrn = true,
                                scheduleFrequency = CaregiverScheduleFrequency.DAILY,
                                selectedDays = emptySet(),
                                selectedSlots = emptySet(),
                            )
                        },
                        label = { Text(stringResource(R.string.caregiver_medication_type_prn)) },
                        modifier = Modifier.testTag("medication-kind-prn"),
                    )
                }
            }
        }
        item {
            FormSection(stringResource(R.string.caregiver_medication_form_name)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    modifier = Modifier.fillMaxWidth().testTag("medication-name"),
                    singleLine = true,
                    isError = errors.any { it.field.name == "NAME" },
                    label = { Text(stringResource(R.string.caregiver_medication_form_name)) },
                )
            }
        }
        item {
            FormSection(stringResource(R.string.caregiver_medication_form_strength)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.dosageStrengthValue,
                        onValueChange = { draft = draft.copy(dosageStrengthValue = it) },
                        modifier = Modifier.weight(1f).testTag("medication-strength-value"),
                        enabled = draft.dosageStrengthUnit != "不明",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(stringResource(R.string.caregiver_medication_form_strength_value)) },
                    )
                    OutlinedTextField(
                        value = draft.dosageStrengthUnit,
                        onValueChange = { draft = draft.copy(dosageStrengthUnit = it) },
                        modifier = Modifier.weight(1f).testTag("medication-strength-unit"),
                        singleLine = true,
                        label = { Text(stringResource(R.string.caregiver_medication_form_strength_unit)) },
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mg", "μg", "g", "mL", "不明").forEach { unit ->
                        FilterChip(selected = draft.dosageStrengthUnit == unit, onClick = { draft = draft.copy(dosageStrengthUnit = unit) }, label = { Text(unit) })
                    }
                }
                OutlinedTextField(
                    value = draft.doseCountPerIntake,
                    onValueChange = { draft = draft.copy(doseCountPerIntake = it) },
                    modifier = Modifier.fillMaxWidth().testTag("medication-dose-count"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.caregiver_medication_form_dose_count)) },
                )
            }
        }
        if (draft.isPrn) item {
            OutlinedTextField(
                value = draft.prnInstructions,
                onValueChange = { draft = draft.copy(prnInstructions = it) },
                modifier = Modifier.fillMaxWidth().testTag("medication-prn-instructions"),
                label = { Text(stringResource(R.string.caregiver_medication_form_prn_instructions)) },
                minLines = 2,
            )
        }
        if (!draft.isPrn) item {
            FormSection(stringResource(R.string.caregiver_medication_form_schedule)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.scheduleFrequency == CaregiverScheduleFrequency.DAILY,
                        onClick = { draft = draft.copy(scheduleFrequency = CaregiverScheduleFrequency.DAILY, selectedDays = emptySet()) },
                        label = { Text(stringResource(R.string.caregiver_medication_form_daily)) },
                        modifier = Modifier.testTag("medication-frequency-daily"),
                    )
                    FilterChip(
                        selected = draft.scheduleFrequency == CaregiverScheduleFrequency.WEEKLY,
                        onClick = { draft = draft.copy(scheduleFrequency = CaregiverScheduleFrequency.WEEKLY) },
                        label = { Text(stringResource(R.string.caregiver_medication_form_weekly)) },
                        modifier = Modifier.testTag("medication-frequency-weekly"),
                    )
                }
                if (draft.scheduleFrequency == CaregiverScheduleFrequency.WEEKLY) {
                    Text(stringResource(R.string.caregiver_medication_form_schedule_days), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CaregiverScheduleDay.entries.forEach { day ->
                            FilterChip(
                                selected = day in draft.selectedDays,
                                onClick = { draft = draft.copy(selectedDays = draft.selectedDays.toggle(day)) },
                                label = { Text(DAY_LABELS.getValue(day.apiValue)) },
                                modifier = Modifier.testTag("medication-day-${day.apiValue.lowercase()}")
                            )
                        }
                    }
                }
                Text(stringResource(R.string.caregiver_medication_form_schedule_slots), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CaregiverScheduleSlot.entries.forEach { slot ->
                        val label = when (slot) {
                            CaregiverScheduleSlot.MORNING -> R.string.caregiver_medication_form_slot_morning
                            CaregiverScheduleSlot.NOON -> R.string.caregiver_medication_form_slot_noon
                            CaregiverScheduleSlot.EVENING -> R.string.caregiver_medication_form_slot_evening
                            CaregiverScheduleSlot.BEDTIME -> R.string.caregiver_medication_form_slot_bedtime
                        }
                        FilterChip(
                            selected = slot in draft.selectedSlots,
                            onClick = { draft = draft.copy(selectedSlots = draft.selectedSlots.toggle(slot)) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.testTag("medication-slot-${slot.apiValue}"),
                        )
                    }
                }
            }
        }
        item {
            FormSection(stringResource(R.string.caregiver_medication_form_start_date)) {
                OutlinedButton(onClick = { pickDate(draft.startDate) { draft = draft.copy(startDate = it) } }, modifier = Modifier.fillMaxWidth().testTag("medication-start-date")) {
                    Text(draft.startDate.toString())
                }
                OutlinedButton(onClick = { pickDate(draft.endDate ?: draft.startDate) { draft = draft.copy(endDate = it) } }, modifier = Modifier.fillMaxWidth().testTag("medication-end-date")) {
                    Text(draft.endDate?.toString() ?: stringResource(R.string.caregiver_medication_form_end_date))
                }
                if (draft.endDate != null) TextButton(onClick = { draft = draft.copy(endDate = null) }) {
                    Text(stringResource(R.string.caregiver_medication_form_no_end_date))
                }
            }
        }
        item {
            OutlinedTextField(
                value = draft.inventoryCount,
                onValueChange = { draft = draft.copy(inventoryCount = it) },
                modifier = Modifier.fillMaxWidth().testTag("medication-inventory"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(stringResource(R.string.caregiver_medication_form_inventory)) },
                supportingText = { Text(stringResource(R.string.caregiver_medication_form_inventory_support)) },
            )
        }
        item {
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                modifier = Modifier.fillMaxWidth().testTag("medication-notes"),
                label = { Text(stringResource(R.string.caregiver_medication_form_notes)) },
                minLines = 3,
            )
        }
        if (errors.isNotEmpty()) item {
            Text(errors.joinToString("\n") { "・${it.message}" }, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("medication-validation-errors"))
        }
        if (saveFailed) item {
            Text(stringResource(R.string.caregiver_medication_form_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("medication-save-error"))
        }
        if (deleteFailed) item {
            Text(stringResource(R.string.caregiver_medication_delete_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("medication-delete-error"))
        }
        item {
            Button(
                onClick = {
                    submitted = true
                    saveFailed = false
                    if (draft.validate().isEmpty()) {
                        submitting = true
                        scope.launch {
                            val result = repository.save(patientId, medication?.id, draft)
                            submitting = false
                            if (result.isSuccess) onClose() else saveFailed = true
                        }
                    }
                },
                enabled = enabled && !submitting,
                modifier = Modifier.fillMaxWidth().testTag("medication-save"),
            ) {
                Text(stringResource(if (submitting) R.string.caregiver_medication_form_saving else R.string.caregiver_medication_form_save))
            }
            if (medication != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showingDeleteConfirm = true },
                    enabled = enabled && !submitting && !deleting,
                    modifier = Modifier.fillMaxWidth().testTag("medication-delete"),
                ) {
                    Text(stringResource(R.string.caregiver_medication_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showingDeleteConfirm && medication != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showingDeleteConfirm = false },
            title = { Text(stringResource(R.string.caregiver_medication_delete_confirm_title)) },
            text = { Text(stringResource(R.string.caregiver_medication_delete_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { showingDeleteConfirm = false }, enabled = !deleting) {
                    Text(stringResource(R.string.caregiver_medication_form_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = true
                        deleteFailed = false
                        scope.launch {
                            val deleted = repository.delete(patientId, medication.id)
                            deleting = false
                            showingDeleteConfirm = false
                            if (deleted) onClose() else deleteFailed = true
                        }
                    },
                    enabled = !deleting,
                    modifier = Modifier.testTag("medication-delete-confirm"),
                ) {
                    Text(stringResource(R.string.caregiver_medication_delete_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.testTag("medication-delete-dialog"),
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun medicationSchedule(item: PatientMedication, slotTimes: CaregiverSlotTimes?): String? {
    if (item.isPrn || item.regimenTimes.isNullOrEmpty()) return null
    val slots = slotTimes?.let { mapOf(it.morning to "朝", it.noon to "昼", it.evening to "夕", it.bedtime to "寝る前") }.orEmpty()
    val timeText = item.regimenTimes.joinToString("・") { slots[it] ?: it }
    val days = item.regimenDaysOfWeek.orEmpty()
    return if (days.isEmpty()) stringResource(R.string.caregiver_medication_schedule_daily, timeText)
    else stringResource(R.string.caregiver_medication_schedule_weekly, days.mapNotNull(DAY_LABELS::get).joinToString("・"), timeText)
}

@Composable
private fun CaregiverMedicationMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    CaregiverMedicationCentered {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
private fun CaregiverMedicationCentered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun MedicationBadge(text: String) {
    Card {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private fun formatMedicationNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private val TOKYO = ZoneId.of("Asia/Tokyo")
private val DAY_LABELS = mapOf("MON" to "月", "TUE" to "火", "WED" to "水", "THU" to "木", "FRI" to "金", "SAT" to "土", "SUN" to "日")
