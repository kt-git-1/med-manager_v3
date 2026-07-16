package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.format.DateTimeFormatter
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
    onReturnToLogin: () -> Unit = {},
    onOpenPatients: () -> Unit = {},
    onCreatePatient: () -> Unit = {},
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
        patientState.loading && patientState.patients.isEmpty() -> CaregiverMedicationLoadingState()
        patientState.loadFailed -> CaregiverDataUnavailableState(
            enabled = enabled,
            onRetry = onOpenPatients,
            onReturnToLogin = onReturnToLogin,
            testTagPrefix = "caregiver-medication-patients",
        )
        patientState.patients.isEmpty() -> CaregiverNoPatientState(
            enabled = enabled,
            onCreatePatient = onCreatePatient,
            testTagPrefix = "caregiver-medication",
        )
        selected == null -> CaregiverPatientSelectionRequiredState(
            enabled = enabled,
            onOpenPatients = onOpenPatients,
            testTagPrefix = "caregiver-medication",
        )
        state.loading -> CaregiverMedicationLoadingState()
        state.loadFailed -> CaregiverDataUnavailableState(
            enabled = enabled,
            onRetry = { scope.launch { repository.load(selected.id) } },
            onReturnToLogin = onReturnToLogin,
            testTagPrefix = "caregiver-medication",
        )
        else -> Box(Modifier.fillMaxSize()) {
            CaregiverMedicationList(
                items = state.items,
                patientName = selected.displayName,
                slotTimes = selected.slotTimes,
                filter = filter,
                onFilter = { filterName = it.name },
                onAdd = { addingMedication = true },
                onEdit = { editingMedicationId = it.id },
                refreshFailed = state.refreshFailed,
                onRetry = { scope.launch { repository.load(selected.id) } },
                enabled = enabled && !state.refreshFailed && !state.refreshing,
            )
            if (state.refreshing) CaregiverMedicationUpdatingOverlay()
        }
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
    refreshFailed: Boolean,
    onRetry: () -> Unit,
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("caregiver-medication-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CaregiverPatientAvatar(patientName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.caregiver_medication_title),
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.caregiver_medication_patient, patientName),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (items.isNotEmpty()) {
                    Button(
                        onClick = onAdd,
                        enabled = enabled,
                        modifier = Modifier.height(44.dp).testTag("caregiver-medication-add"),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.caregiver_medication_add_compact), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (refreshFailed) item { CaregiverStaleDataCard("caregiver-medication-stale", onRetry) }
        if (items.isEmpty()) item {
            CaregiverMedicationEmptyCard(onAdd, enabled)
        } else item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_active), items.count { !ended(it) }, Icons.Rounded.Medication, MaterialTheme.colorScheme.primary, Modifier.weight(1f), usePillsGlyph = true)
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_scheduled), items.count { !it.isPrn && !ended(it) }, Icons.Rounded.Schedule, colors.caregiverBlue, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_prn), items.count { it.isPrn && !ended(it) }, Icons.Rounded.LocalHospital, colors.orange, Modifier.weight(1f))
                    CaregiverMetric(stringResource(R.string.caregiver_medication_metric_ended), items.count(::ended), Icons.Rounded.EventBusy, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
        }
        if (items.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaregiverMedicationFilter.entries.forEach { item ->
                    CaregiverMedicationFilterChip(
                        filter = item,
                        selected = filter == item,
                        onClick = { onFilter(item) },
                    )
                }
            }
        }
        val sections = when (filter) {
            CaregiverMedicationFilter.ALL -> listOf(
                R.string.caregiver_medication_section_scheduled to visible.filter { !it.isPrn && !ended(it) },
                R.string.caregiver_medication_section_prn to visible.filter { it.isPrn && !ended(it) },
                R.string.caregiver_medication_section_ended_scheduled to visible.filter { !it.isPrn && ended(it) },
                R.string.caregiver_medication_section_ended_prn to visible.filter { it.isPrn && ended(it) },
            )
            CaregiverMedicationFilter.SCHEDULED -> listOf(R.string.caregiver_medication_section_scheduled to visible)
            CaregiverMedicationFilter.PRN -> listOf(R.string.caregiver_medication_section_prn to visible)
            CaregiverMedicationFilter.ENDED -> listOf(
                R.string.caregiver_medication_section_ended_scheduled to visible.filter { !it.isPrn },
                R.string.caregiver_medication_section_ended_prn to visible.filter { it.isPrn },
            )
        }
        sections.forEach { (title, sectionItems) ->
            if (sectionItems.isNotEmpty()) {
                item { Text(stringResource(title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                items(sectionItems, key = { it.id }) { medication ->
                    CaregiverMedicationCard(medication, slotTimes, { onEdit(medication) }, enabled)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CaregiverMetric(
    label: String,
    value: Int,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    usePillsGlyph: Boolean = false,
) {
    Card(
        modifier = modifier.heightIn(min = 124.dp).shadow(3.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                if (usePillsGlyph) MedicationPillsGlyph(tint, Modifier.size(20.dp))
                else Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(value.toString(), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaregiverMedicationFilterChip(
    filter: CaregiverMedicationFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = when (filter) {
        CaregiverMedicationFilter.ALL -> MaterialTheme.colorScheme.primary
        CaregiverMedicationFilter.SCHEDULED -> MedicationTheme.colors.caregiverBlue
        CaregiverMedicationFilter.PRN -> MedicationTheme.colors.orange
        CaregiverMedicationFilter.ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (filter) {
        CaregiverMedicationFilter.ALL -> Icons.AutoMirrored.Rounded.FormatListBulleted
        CaregiverMedicationFilter.SCHEDULED -> Icons.Rounded.Schedule
        CaregiverMedicationFilter.PRN -> Icons.Rounded.LocalHospital
        CaregiverMedicationFilter.ENDED -> Icons.Rounded.EventBusy
    }
    Row(
        modifier = Modifier.height(38.dp)
            .clip(CircleShape)
            .background(if (selected) tint else MaterialTheme.colorScheme.surface)
            .border(1.dp, tint.copy(alpha = if (selected) 1f else 0.22f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("caregiver-medication-filter-${filter.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else tint, modifier = Modifier.size(18.dp))
        Text(
            stringResource(filter.label),
            color = if (selected) Color.White else tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CaregiverMedicationCard(
    item: PatientMedication,
    slotTimes: CaregiverSlotTimes?,
    onEdit: () -> Unit,
    enabled: Boolean,
) {
    val typeColor = if (item.isPrn) MedicationTheme.colors.orange else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onEdit)
            .testTag("caregiver-medication-${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, typeColor.copy(alpha = 0.20f)),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(62.dp)
                    .background(Brush.linearGradient(listOf(typeColor.copy(alpha = 0.18f), typeColor.copy(alpha = 0.08f))), CircleShape)
                    .border(1.dp, typeColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isPrn) {
                    Icon(Icons.Rounded.LocalHospital, contentDescription = null, tint = typeColor, modifier = Modifier.size(28.dp))
                } else {
                    MedicationPillsGlyph(typeColor, Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                MedicationBadge(stringResource(if (item.isPrn) R.string.caregiver_medication_type_prn else R.string.caregiver_medication_type_scheduled), typeColor)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item.dosageText.trim().takeUnless { it.isEmpty() || it == "不明" }?.let {
                    CaregiverMedicationDetailLine(stringResource(R.string.caregiver_medication_dosage, it), Icons.Rounded.Science)
                }
                CaregiverMedicationDetailLine(
                    stringResource(R.string.caregiver_medication_dose, formatMedicationNumber(item.doseCountPerIntake)),
                    Icons.Rounded.Medication,
                    usePillsGlyph = true,
                )
                medicationSchedule(item, slotTimes)?.let { CaregiverMedicationDetailLine(it, Icons.Rounded.Schedule) }
                    ?: if (item.isPrn) CaregiverMedicationDetailLine(stringResource(R.string.caregiver_medication_prn_when_needed), Icons.Rounded.LocalHospital) else Unit
            }
            if (item.inventoryEnabled) {
                CaregiverMedicationInventoryPill(
                    text = if (item.inventoryOut) stringResource(R.string.caregiver_medication_inventory_out)
                    else stringResource(R.string.caregiver_medication_inventory_remaining, formatMedicationNumber(item.inventoryQuantity), item.inventoryUnit ?: stringResource(R.string.caregiver_medication_inventory_unit)),
                    error = item.inventoryOut || item.isInsufficientForDose,
                )
            }
            }
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(48.dp).testTag("caregiver-medication-edit-${item.id}")) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.caregiver_medication_edit_accessibility, item.name),
                        tint = MedicationTheme.colors.primaryTealText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaregiverMedicationDetailLine(text: String, icon: ImageVector, usePillsGlyph: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (usePillsGlyph) MedicationPillsGlyph(MedicationTheme.colors.primaryTealText, Modifier.size(20.dp))
        else Icon(icon, contentDescription = null, tint = MedicationTheme.colors.primaryTealText, modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
    }
}

@Composable
internal fun MedicationPillsGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension
        val capsuleTopLeft = Offset(unit * 0.05f, unit * 0.16f)
        val capsuleSize = Size(unit * 0.58f, unit * 0.30f)
        rotate(-42f, pivot = Offset(unit * 0.34f, unit * 0.31f)) {
            drawRoundRect(
                color = tint,
                topLeft = capsuleTopLeft,
                size = capsuleSize,
                cornerRadius = CornerRadius(unit * 0.15f, unit * 0.15f),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(unit * 0.34f, unit * 0.16f),
                end = Offset(unit * 0.34f, unit * 0.46f),
                strokeWidth = unit * 0.055f,
            )
        }
        drawCircle(color = tint.copy(alpha = 0.66f), radius = unit * 0.19f, center = Offset(unit * 0.73f, unit * 0.70f))
        drawLine(
            color = Color.White.copy(alpha = 0.78f),
            start = Offset(unit * 0.55f, unit * 0.70f),
            end = Offset(unit * 0.91f, unit * 0.70f),
            strokeWidth = unit * 0.055f,
        )
    }
}

@Composable
private fun CaregiverMedicationInventoryPill(text: String, error: Boolean) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CaregiverMedicationEmptyCard(onAdd: () -> Unit, enabled: Boolean) {
    Card(
        Modifier.fillMaxWidth().testTag("caregiver-medication-empty"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    MedicationPillsGlyph(MaterialTheme.colorScheme.primary, Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.caregiver_medication_empty_title), fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_medication_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            CaregiverMedicationOnboardingStep(1, stringResource(R.string.caregiver_medication_empty_step_name), null, MaterialTheme.colorScheme.primary)
            CaregiverMedicationOnboardingStep(2, stringResource(R.string.caregiver_medication_empty_step_schedule), Icons.Rounded.Schedule, MedicationTheme.colors.caregiverBlue)
            CaregiverMedicationOnboardingStep(3, stringResource(R.string.caregiver_medication_empty_step_inventory), Icons.Rounded.Inventory2, MedicationTheme.colors.orange)
            Button(onClick = onAdd, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("caregiver-medication-empty-add"), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.caregiver_medication_add), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CaregiverMedicationOnboardingStep(number: Int, label: String, icon: ImageVector?, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (icon == null) Text("ああ", color = tint, fontSize = 13.sp, fontWeight = FontWeight.Black)
            else Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CaregiverMedicationLoadingState() {
    CaregiverMedicationCentered {
        CircularProgressIndicator(modifier = Modifier.size(38.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), strokeWidth = 4.dp)
        Text(stringResource(R.string.patient_today_loading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("caregiver-medication-loading"))
    }
}

@Composable
private fun CaregiverMedicationUpdatingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).clickable(onClick = {}).testTag("caregiver-medication-updating"), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp))
                Text(stringResource(R.string.patient_today_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-medication-form"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = onClose,
                    enabled = !submitting,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) { Text(stringResource(R.string.caregiver_medication_form_cancel)) }
                Text(
                    stringResource(if (medication == null) R.string.caregiver_medication_form_hero_new else R.string.caregiver_medication_edit),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item { MedicationEditorHero(medication == null, draft) }
        item {
            MedicationFormSectionHeader(stringResource(R.string.caregiver_medication_form_basic), MedicationFormHeaderIcon.PILLS)
            Spacer(Modifier.height(8.dp))
            MedicationBasicInformation(
                draft = draft,
                nameIsError = errors.any { it.field.name == "NAME" },
                onDraftChange = { draft = it },
            )
            Text(
                stringResource(R.string.caregiver_medication_form_basic_help),
                modifier = Modifier.padding(start = 16.dp, top = 7.dp, end = 12.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            MedicationFormSectionHeader(stringResource(R.string.caregiver_medication_form_kind), MedicationFormHeaderIcon.TYPE)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MedicationTypeChoice(
                    title = stringResource(R.string.caregiver_medication_type_scheduled_title),
                    subtitle = stringResource(R.string.caregiver_medication_type_scheduled_subtitle),
                    selected = !draft.isPrn,
                    accent = MedicationTheme.colors.primaryTealText,
                    icon = MedicationFormTypeIcon.SCHEDULED,
                    onClick = { draft = draft.copy(isPrn = false) },
                    modifier = Modifier.testTag("medication-kind-scheduled"),
                )
                MedicationTypeChoice(
                    title = stringResource(R.string.caregiver_medication_type_prn_title),
                    subtitle = stringResource(R.string.caregiver_medication_type_prn_subtitle),
                    selected = draft.isPrn,
                    accent = MedicationTheme.colors.orange,
                    icon = MedicationFormTypeIcon.PRN,
                    onClick = {
                        draft = draft.copy(
                            isPrn = true,
                            scheduleFrequency = CaregiverScheduleFrequency.DAILY,
                            selectedDays = emptySet(),
                            selectedSlots = emptySet(),
                        )
                    },
                    modifier = Modifier.testTag("medication-kind-prn"),
                )
                if (draft.isPrn) {
                    OutlinedTextField(
                        value = draft.prnInstructions,
                        onValueChange = { draft = draft.copy(prnInstructions = it) },
                        modifier = Modifier.fillMaxWidth().testTag("medication-prn-instructions"),
                        label = { Text(stringResource(R.string.caregiver_medication_form_prn_instructions)) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            Text(
                stringResource(if (draft.isPrn) R.string.caregiver_medication_form_prn_help else R.string.caregiver_medication_form_scheduled_help),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 12.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            MedicationFormSectionHeader(stringResource(R.string.caregiver_medication_form_period), MedicationFormHeaderIcon.PERIOD)
            Spacer(Modifier.height(8.dp))
            MedicationPeriodCard(
                draft = draft,
                onStartDate = { pickDate(draft.startDate) { draft = draft.copy(startDate = it, endDate = draft.endDate?.coerceAtLeast(it)) } },
                onEndDateEnabled = { enabled -> draft = draft.copy(endDate = if (enabled) draft.startDate else null) },
                onEndDate = { pickDate(draft.endDate ?: draft.startDate) { draft = draft.copy(endDate = it) } },
            )
        }
        if (!draft.isPrn) item {
            MedicationFormSectionHeader(stringResource(R.string.caregiver_medication_form_schedule), MedicationFormHeaderIcon.SCHEDULE)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MedicationScheduleGuide()
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(9.dp)).padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                    MedicationChoiceChip(
                        selected = draft.scheduleFrequency == CaregiverScheduleFrequency.DAILY,
                        onClick = { draft = draft.copy(scheduleFrequency = CaregiverScheduleFrequency.DAILY, selectedDays = emptySet()) },
                        label = stringResource(R.string.caregiver_medication_form_daily),
                        modifier = Modifier.weight(1f).testTag("medication-frequency-daily"),
                        accent = MedicationTheme.colors.caregiverBlue,
                    )
                    MedicationChoiceChip(
                        selected = draft.scheduleFrequency == CaregiverScheduleFrequency.WEEKLY,
                        onClick = { draft = draft.copy(scheduleFrequency = CaregiverScheduleFrequency.WEEKLY) },
                        label = stringResource(R.string.caregiver_medication_form_weekly),
                        modifier = Modifier.weight(1f).testTag("medication-frequency-weekly"),
                        accent = MedicationTheme.colors.caregiverBlue,
                    )
                }
                if (draft.scheduleFrequency == CaregiverScheduleFrequency.WEEKLY) {
                        Text(stringResource(R.string.caregiver_medication_form_schedule_days), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CaregiverScheduleDay.entries.forEach { day ->
                                MedicationWeekdayButton(day, day in draft.selectedDays, { draft = draft.copy(selectedDays = draft.selectedDays.toggle(day)) }, Modifier.weight(1f))
                        }
                    }
                }
                    Text(stringResource(R.string.caregiver_medication_form_schedule_slots), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CaregiverScheduleSlot.entries.chunked(2).forEach { rowSlots ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowSlots.forEach { slot ->
                                MedicationTimeSlotButton(
                                    slot = slot,
                                    time = slotTimes?.valueFor(slot).orEmpty(),
                                    selected = slot in draft.selectedSlots,
                                    onClick = { draft = draft.copy(selectedSlots = draft.selectedSlots.toggle(slot)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.caregiver_medication_form_schedule_help),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 12.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (medication == null) item {
            MedicationFormSectionHeader(stringResource(R.string.caregiver_medication_form_inventory_section), MedicationFormHeaderIcon.INVENTORY)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.caregiver_medication_form_inventory), modifier = Modifier.weight(1f), fontSize = 16.sp)
                    MedicationInlineField(
                        value = draft.inventoryCount,
                        placeholder = "0",
                        onValueChange = { draft = draft.copy(inventoryCount = it) },
                        modifier = Modifier.width(72.dp).testTag("medication-inventory"),
                        keyboardType = KeyboardType.Decimal,
                        textAlign = TextAlign.End,
                        contentPadding = PaddingValues(vertical = 14.dp),
                    )
                }
            }
        }
        item {
            MedicationFormSectionHeader(stringResource(R.string.caregiver_medication_form_notes_section), MedicationFormHeaderIcon.NOTES)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
            ) {
                MedicationInlineField(
                    value = draft.notes,
                    placeholder = stringResource(R.string.caregiver_medication_form_notes),
                    onValueChange = { draft = draft.copy(notes = it) },
                    modifier = Modifier.fillMaxWidth().testTag("medication-notes"),
                    leading = { Icon(Icons.Rounded.Description, contentDescription = null, tint = MedicationTheme.colors.orange, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (errors.isNotEmpty()) item {
            MedicationFormErrorCard(errors.joinToString("\n") { it.message }, "medication-validation-errors")
        }
        if (saveFailed) item {
            MedicationFormErrorCard(stringResource(R.string.caregiver_medication_form_error), "medication-save-error")
        }
        if (deleteFailed) item {
            MedicationFormErrorCard(stringResource(R.string.caregiver_medication_delete_error), "medication-delete-error")
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("medication-save"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedicationTheme.colors.caregiverBlue),
            ) {
                Text(stringResource(if (submitting) R.string.caregiver_medication_form_saving else R.string.caregiver_medication_form_save))
            }
            if (medication != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { showingDeleteConfirm = true },
                    enabled = enabled && !submitting && !deleting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(14.dp)).testTag("medication-delete"),
                ) {
                    Text(stringResource(R.string.caregiver_medication_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
        if (submitting || deleting) CaregiverMedicationUpdatingOverlay()
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
private fun MedicationEditorHero(isNew: Boolean, draft: CaregiverMedicationDraft) {
    val accent = if (draft.isPrn) MedicationTheme.colors.orange else MedicationTheme.colors.primaryTealText
    Card(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = MedicationTheme.colors.caregiverCardShadow, spotColor = MedicationTheme.colors.caregiverCardShadow).testTag("medication-editor-hero"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, accent.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    if (isNew) MedicationPillsGlyph(accent, Modifier.size(38.dp))
                    else Icon(Icons.Rounded.Edit, contentDescription = null, tint = accent, modifier = Modifier.size(38.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (isNew || draft.name.isBlank()) stringResource(if (isNew) R.string.caregiver_medication_form_hero_new else R.string.caregiver_medication_form_hero_edit) else draft.name,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    Text(
                        stringResource(if (draft.isPrn) R.string.caregiver_medication_form_hero_prn else R.string.caregiver_medication_form_hero_scheduled),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedicationProgressPill("薬名", draft.name.isNotBlank(), MedicationTheme.colors.primaryTealText)
                MedicationProgressPill("用量", draft.dosageStrengthUnit.isNotBlank(), MedicationTheme.colors.caregiverBlue)
                MedicationProgressPill("予定", draft.isPrn || draft.selectedSlots.isNotEmpty(), accent)
            }
        }
    }
}

@Composable
private fun MedicationProgressPill(label: String, complete: Boolean, color: Color) {
    val foreground = if (complete) color else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(CircleShape).background(foreground.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (complete) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = foreground, modifier = Modifier.size(15.dp))
        else Box(Modifier.size(13.dp).border(1.5.dp, foreground, CircleShape))
        Text(label, color = foreground, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private enum class MedicationFormHeaderIcon { PILLS, TYPE, PERIOD, SCHEDULE, INVENTORY, NOTES }
private enum class MedicationFormTypeIcon { SCHEDULED, PRN }

@Composable
private fun MedicationFormSectionHeader(title: String, icon: MedicationFormHeaderIcon) {
    Row(
        Modifier.padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (icon) {
            MedicationFormHeaderIcon.PILLS -> MedicationPillsGlyph(MedicationTheme.colors.caregiverBlue, Modifier.size(17.dp))
            MedicationFormHeaderIcon.TYPE -> Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = null, tint = MedicationTheme.colors.primaryTealText, modifier = Modifier.size(17.dp))
            MedicationFormHeaderIcon.PERIOD -> Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue, modifier = Modifier.size(17.dp))
            MedicationFormHeaderIcon.SCHEDULE -> Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MedicationTheme.colors.primaryTealText, modifier = Modifier.size(17.dp))
            MedicationFormHeaderIcon.INVENTORY -> Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue, modifier = Modifier.size(17.dp))
            MedicationFormHeaderIcon.NOTES -> Icon(Icons.Rounded.Description, contentDescription = null, tint = MedicationTheme.colors.orange, modifier = Modifier.size(17.dp))
        }
        Text(title, fontSize = 14.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MedicationPeriodCard(
    draft: CaregiverMedicationDraft,
    onStartDate: () -> Unit,
    onEndDateEnabled: (Boolean) -> Unit,
    onEndDate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MedicationTheme.colors.cardStroke),
    ) {
        Column {
            MedicationDateRow(stringResource(R.string.caregiver_medication_form_start_date), draft.startDate, onStartDate, "medication-start-date")
            MedicationFormDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.caregiver_medication_form_end_date_enabled), modifier = Modifier.weight(1f), fontSize = 16.sp)
                Switch(
                    checked = draft.endDate != null,
                    onCheckedChange = onEndDateEnabled,
                    modifier = Modifier.testTag("medication-end-date-enabled"),
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759)),
                )
            }
            draft.endDate?.let { endDate ->
                MedicationFormDivider()
                MedicationDateRow(stringResource(R.string.caregiver_medication_form_end_date_label), endDate, onEndDate, "medication-end-date")
            }
        }
    }
}

@Composable
private fun MedicationDateRow(label: String, date: LocalDate, onClick: () -> Unit, testTag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 14.dp).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text(date.format(MEDICATION_DATE_FORMAT), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    }
}

@Composable
private fun MedicationScheduleGuide() {
    Row(
        Modifier.fillMaxWidth().background(MedicationTheme.colors.primaryTealText.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MedicationTheme.colors.primaryTealText.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MedicationTheme.colors.primaryTealText, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.caregiver_medication_form_schedule_guide_title), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.caregiver_medication_form_schedule_guide_message), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MedicationWeekdayButton(day: CaregiverScheduleDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MedicationTheme.colors.caregiverBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick).testTag("medication-day-${day.apiValue.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(DAY_LABELS.getValue(day.apiValue), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MedicationTimeSlotButton(
    slot: CaregiverScheduleSlot,
    time: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (slot) {
        CaregiverScheduleSlot.MORNING -> stringResource(R.string.caregiver_medication_form_slot_morning)
        CaregiverScheduleSlot.NOON -> stringResource(R.string.caregiver_medication_form_slot_noon)
        CaregiverScheduleSlot.EVENING -> stringResource(R.string.caregiver_medication_form_slot_evening)
        CaregiverScheduleSlot.BEDTIME -> stringResource(R.string.caregiver_medication_form_slot_bedtime)
    }
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MedicationTheme.colors.caregiverBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(if (selected) 1.5.dp else 0.dp, if (selected) MedicationTheme.colors.caregiverBlue.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp).testTag("medication-slot-${slot.apiValue}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue, modifier = Modifier.size(21.dp))
        else Box(Modifier.size(19.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (time.isNotEmpty()) Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MedicationFormErrorCard(message: String, testTag: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MedicationBasicInformation(
    draft: CaregiverMedicationDraft,
    nameIsError: Boolean,
    onDraftChange: (CaregiverMedicationDraft) -> Unit,
) {
    var unitMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (nameIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MedicationTheme.colors.cardStroke),
    ) {
        Column {
            MedicationInlineField(
                value = draft.name,
                placeholder = stringResource(R.string.caregiver_medication_form_name),
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth().testTag("medication-name"),
                leading = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue, modifier = Modifier.size(18.dp)) },
            )
            MedicationFormDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Science, contentDescription = null, tint = MedicationTheme.colors.orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                MedicationInlineField(
                    value = draft.dosageStrengthValue,
                    placeholder = stringResource(R.string.caregiver_medication_form_strength_value),
                    onValueChange = { onDraftChange(draft.copy(dosageStrengthValue = it)) },
                    modifier = Modifier.weight(1f).testTag("medication-strength-value"),
                    enabled = draft.dosageStrengthUnit != "不明",
                    keyboardType = KeyboardType.Decimal,
                    contentPadding = PaddingValues(vertical = 14.dp),
                )
                Box(Modifier.height(22.dp).width(1.dp).background(MaterialTheme.colorScheme.outline))
                Box(Modifier.width(108.dp)) {
                    MedicationInlineField(
                        value = draft.dosageStrengthUnit,
                        placeholder = stringResource(R.string.caregiver_medication_form_strength_unit),
                        onValueChange = { unit -> onDraftChange(draft.copy(dosageStrengthUnit = unit, dosageStrengthValue = if (unit == "不明") "" else draft.dosageStrengthValue)) },
                        modifier = Modifier.fillMaxWidth().testTag("medication-strength-unit"),
                        contentPadding = PaddingValues(start = 14.dp, top = 14.dp, bottom = 14.dp),
                        trailing = {
                            IconButton(
                                onClick = { unitMenuExpanded = true },
                                modifier = Modifier.size(28.dp).testTag("medication-strength-unit-menu"),
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "単位を選択", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                        },
                    )
                    DropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                        listOf("mg", "g", "μg", "mL", "IU", "mEq", "%", "滴", "包", "枚", "吸入", "不明").forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = {
                                    unitMenuExpanded = false
                                    onDraftChange(draft.copy(dosageStrengthUnit = unit, dosageStrengthValue = if (unit == "不明") "" else draft.dosageStrengthValue))
                                },
                                modifier = Modifier.testTag("medication-strength-unit-$unit"),
                            )
                        }
                    }
                }
            }
            MedicationFormDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                MedicationPillsGlyph(MedicationTheme.colors.primaryTealText, Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.caregiver_medication_form_dose_count_short), modifier = Modifier.weight(1f), fontSize = 16.sp)
                MedicationInlineField(
                    value = draft.doseCountPerIntake,
                    placeholder = "0",
                    onValueChange = { onDraftChange(draft.copy(doseCountPerIntake = it)) },
                    modifier = Modifier.width(48.dp).testTag("medication-dose-count"),
                    keyboardType = KeyboardType.Decimal,
                    textAlign = TextAlign.End,
                    contentPadding = PaddingValues(vertical = 12.dp),
                )
                IconButton(
                    onClick = { onDraftChange(draft.copy(doseCountPerIntake = adjustDose(draft.doseCountPerIntake, -0.5))) },
                    modifier = Modifier.size(34.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp)),
                ) { Icon(Icons.Rounded.Remove, contentDescription = "減らす", modifier = Modifier.size(18.dp)) }
                IconButton(
                    onClick = { onDraftChange(draft.copy(doseCountPerIntake = adjustDose(draft.doseCountPerIntake, 0.5))) },
                    modifier = Modifier.size(34.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp)),
                ) { Icon(Icons.Rounded.Add, contentDescription = "増やす", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun MedicationInlineField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textAlign: TextAlign = TextAlign.Start,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().padding(contentPadding), verticalAlignment = Alignment.CenterVertically) {
                leading?.let { it(); Spacer(Modifier.width(12.dp)) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    inner()
                }
                trailing?.let { Spacer(Modifier.width(4.dp)); it() }
            }
        },
    )
}

@Composable
private fun MedicationFormDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun MedicationTypeChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    icon: MedicationFormTypeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent.copy(alpha = 0.45f) else MedicationTheme.colors.cardStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) accent else accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(
                if (icon == MedicationFormTypeIcon.SCHEDULED) Icons.Rounded.Schedule else Icons.Rounded.LocalHospital,
                contentDescription = null,
                tint = if (selected) Color.White else accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        else Box(Modifier.size(21.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
    }
}

private fun adjustDose(value: String, delta: Double): String = formatMedicationNumber(((value.toDoubleOrNull() ?: 0.0) + delta).coerceIn(0.0, 999.0))

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

private fun CaregiverSlotTimes.valueFor(slot: CaregiverScheduleSlot): String = when (slot) {
    CaregiverScheduleSlot.MORNING -> morning
    CaregiverScheduleSlot.NOON -> noon
    CaregiverScheduleSlot.EVENING -> evening
    CaregiverScheduleSlot.BEDTIME -> bedtime
}

private val TOKYO = ZoneId.of("Asia/Tokyo")
private val MEDICATION_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.JAPAN)
private val DAY_LABELS = mapOf("MON" to "月", "TUE" to "火", "WED" to "水", "THU" to "木", "FRI" to "金", "SAT" to "土", "SUN" to "日")
