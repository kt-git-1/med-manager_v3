package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
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
    val colors = MedicationTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-medication-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(patientName.trim().take(1), style = MaterialTheme.typography.headlineSmall, color = colors.primaryTealText, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.caregiver_medication_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_medication_patient, patientName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAdd, enabled = enabled, modifier = Modifier.testTag("caregiver-medication-add")) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.caregiver_medication_add_title))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_active), items.count { !ended(it) }, Icons.Rounded.Medication, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_scheduled), items.count { !it.isPrn && !ended(it) }, Icons.Rounded.Schedule, colors.caregiverBlue, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_prn), items.count { it.isPrn && !ended(it) }, Icons.Rounded.Inventory2, colors.orange, Modifier.weight(1f))
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_ended), items.count(::ended), Icons.Rounded.EventBusy, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaregiverMedicationFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { onFilter(item) },
                        label = { Text(stringResource(item.label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = colors.primaryTealText,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = filter == item,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
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
private fun CaregiverMetric(label: String, value: Int, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Column {
                Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = tint, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    val typeColor = if (item.isPrn) MedicationTheme.colors.orange else MedicationTheme.colors.caregiverBlue
    Card(
        modifier = Modifier.fillMaxWidth().testTag("caregiver-medication-${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(typeColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Medication, contentDescription = null, tint = typeColor, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedicationBadge(stringResource(if (item.isPrn) R.string.caregiver_medication_type_prn else R.string.caregiver_medication_type_scheduled), typeColor)
                if (ended) MedicationBadge(stringResource(R.string.caregiver_medication_type_ended), MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("caregiver-medication-edit-${item.id}")) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.caregiver_medication_edit), tint = MaterialTheme.colorScheme.primary)
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
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-medication-form"),
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
        item { MedicationEditorHero(medication == null, draft) }
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
                        MedicationChoiceChip(selected = draft.dosageStrengthUnit == unit, onClick = { draft = draft.copy(dosageStrengthUnit = unit) }, label = unit)
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
        item {
            FormSection(stringResource(R.string.caregiver_medication_form_kind)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MedicationChoiceChip(
                        selected = !draft.isPrn,
                        onClick = { draft = draft.copy(isPrn = false) },
                        label = stringResource(R.string.caregiver_medication_type_scheduled),
                        modifier = Modifier.testTag("medication-kind-scheduled"),
                    )
                    MedicationChoiceChip(
                        selected = draft.isPrn,
                        onClick = {
                            draft = draft.copy(
                                isPrn = true,
                                scheduleFrequency = CaregiverScheduleFrequency.DAILY,
                                selectedDays = emptySet(),
                                selectedSlots = emptySet(),
                            )
                        },
                        label = stringResource(R.string.caregiver_medication_type_prn),
                        accent = MedicationTheme.colors.orange,
                        modifier = Modifier.testTag("medication-kind-prn"),
                    )
                }
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
                    MedicationChoiceChip(
                        selected = draft.scheduleFrequency == CaregiverScheduleFrequency.DAILY,
                        onClick = { draft = draft.copy(scheduleFrequency = CaregiverScheduleFrequency.DAILY, selectedDays = emptySet()) },
                        label = stringResource(R.string.caregiver_medication_form_daily),
                        modifier = Modifier.testTag("medication-frequency-daily"),
                    )
                    MedicationChoiceChip(
                        selected = draft.scheduleFrequency == CaregiverScheduleFrequency.WEEKLY,
                        onClick = { draft = draft.copy(scheduleFrequency = CaregiverScheduleFrequency.WEEKLY) },
                        label = stringResource(R.string.caregiver_medication_form_weekly),
                        modifier = Modifier.testTag("medication-frequency-weekly"),
                    )
                }
                if (draft.scheduleFrequency == CaregiverScheduleFrequency.WEEKLY) {
                    Text(stringResource(R.string.caregiver_medication_form_schedule_days), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CaregiverScheduleDay.entries.forEach { day ->
                            MedicationChoiceChip(
                                selected = day in draft.selectedDays,
                                onClick = { draft = draft.copy(selectedDays = draft.selectedDays.toggle(day)) },
                                label = DAY_LABELS.getValue(day.apiValue),
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
                        MedicationChoiceChip(
                            selected = slot in draft.selectedSlots,
                            onClick = { draft = draft.copy(selectedSlots = draft.selectedSlots.toggle(slot)) },
                            label = stringResource(label),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MedicationEditorHero(isNew: Boolean, draft: CaregiverMedicationDraft) {
    val accent = if (draft.isPrn) MedicationTheme.colors.orange else MaterialTheme.colorScheme.primary
    val stepsDone = listOf(draft.name.isNotBlank(), draft.dosageStrengthValue.isNotBlank(), draft.isPrn || draft.selectedSlots.isNotEmpty())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Medication, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (isNew) R.string.caregiver_medication_form_hero_new else R.string.caregiver_medication_form_hero_edit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (draft.isPrn) R.string.caregiver_medication_form_hero_prn else R.string.caregiver_medication_form_hero_scheduled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val labels = listOf("薬名", "用量", "予定")
                stepsDone.forEachIndexed { index, complete ->
                    Row(
                        Modifier.clip(CircleShape).background(if (complete) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (complete) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                        Text(labels[index], style = MaterialTheme.typography.labelMedium, color = if (complete) accent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = accent.copy(alpha = 0.14f),
            selectedLabelColor = accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = accent.copy(alpha = 0.55f),
        ),
    )
}

@Composable
private fun medicationSchedule(item: PatientMedication, slotTimes: CaregiverSlotTimes?): String? {
    if (item.isPrn || item.regimenTimes.isNullOrEmpty()) return null
    val slots = buildMap {
        put("morning", "朝")
        put("noon", "昼")
        put("evening", "夕")
        put("bedtime", "寝る前")
        slotTimes?.let {
            put(it.morning, "朝")
            put(it.noon, "昼")
            put(it.evening, "夕")
            put(it.bedtime, "寝る前")
        }
    }
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
private fun MedicationBadge(text: String, tint: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f))) {
        Text(text, color = tint, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private fun formatMedicationNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private val TOKYO = ZoneId.of("Asia/Tokyo")
private val DAY_LABELS = mapOf("MON" to "月", "TUE" to "火", "WED" to "水", "THU" to "木", "FRI" to "金", "SAT" to "土", "SUN" to "日")
