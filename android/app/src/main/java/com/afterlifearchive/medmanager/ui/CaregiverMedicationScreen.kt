package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

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
    val filter = CaregiverMedicationFilter.valueOf(filterName)

    LaunchedEffect(enabled, selected?.id, freshness.medication, freshness.inventory, freshness.slotTimes) {
        if (enabled && selected != null) cursor.refreshIfStale { repository.load(selected.id) }
        if (selected == null) repository.clear()
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
            Text(stringResource(R.string.caregiver_medication_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            CaregiverMedicationCard(medication, slotTimes, ended(medication))
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
private fun CaregiverMedicationCard(item: PatientMedication, slotTimes: CaregiverSlotTimes?, ended: Boolean) {
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

private val TOKYO = ZoneId.of("Asia/Tokyo")
private val DAY_LABELS = mapOf("MON" to "月", "TUE" to "火", "WED" to "水", "THU" to "木", "FRI" to "金", "SAT" to "土", "SUN" to "日")
