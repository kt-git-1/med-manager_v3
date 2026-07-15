package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryMutationMessage
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import kotlinx.coroutines.launch

private enum class InventoryFilter { ALL, LOW, OUT }
private sealed interface InventoryDetailRetryAction {
    data object SaveSettings : InventoryDetailRetryAction
    data class Refill(val amount: Double) : InventoryDetailRetryAction
    data class Correction(val quantity: Double) : InventoryDetailRetryAction
}

@Composable
internal fun CaregiverInventoryScreen(
    repository: CaregiverInventoryRepository,
    patientState: CaregiverPatientState,
    enabled: Boolean,
    onOpenMedications: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val freshness by repository.freshness.collectAsStateWithLifecycle()
    val selected = patientState.selectedPatient
    val cursor = remember(repository) { repository.newFreshnessCursor() }
    val scope = rememberCoroutineScope()
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(enabled, selected?.id, freshness.dose, freshness.medication, freshness.inventory) {
        if (enabled && selected != null) cursor.refreshIfStale { repository.load(selected.id) }
        if (selected == null) repository.clear()
    }
    val selectedItem = state.items.firstOrNull { it.medicationId == selectedItemId }
    if (selected != null && selectedItem != null) {
        Box(Modifier.fillMaxSize()) {
            CaregiverInventoryDetail(
                patientId = selected.id,
                item = selectedItem,
                repository = repository,
                updating = state.updatingMedicationId == selectedItem.medicationId,
                failed = state.mutationFailed,
                message = state.mutationMessage,
                refreshFailed = state.refreshFailed,
                onRetry = { scope.launch { repository.load(selected.id) } },
                enabled = enabled && !state.refreshFailed,
                onClose = { selectedItemId = null },
            )
            if (state.refreshing || state.updatingMedicationId != null) CaregiverInventoryUpdatingOverlay()
        }
        return
    }

    when {
        patientState.loading && patientState.patients.isEmpty() -> InventoryCentered { CircularProgressIndicator() }
        patientState.loadFailed -> InventoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message))
        patientState.patients.isEmpty() -> InventoryMessage(stringResource(R.string.caregiver_no_patient_title), stringResource(R.string.caregiver_no_patient_message))
        selected == null -> InventoryMessage(stringResource(R.string.caregiver_no_selection_title), stringResource(R.string.caregiver_no_selection_message))
        state.loading -> CaregiverInventoryLoadingState()
        state.loadFailed -> InventoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message)) {
            Button(onClick = { scope.launch { repository.load(selected.id) } }) { Text(stringResource(R.string.common_retry)) }
        }
        else -> Box(Modifier.fillMaxSize()) {
            CaregiverInventoryList(
                patientName = selected.displayName,
                items = state.items,
                refreshFailed = state.refreshFailed,
                failed = state.mutationFailed,
                message = state.mutationMessage,
                onRetry = { scope.launch { repository.load(selected.id) } },
                onSelect = { selectedItemId = it.medicationId },
                onQuickRefill = { item -> scope.launch { repository.refill(selected.id, item, plannedRefill(item, 7)) } },
                onOpenMedications = onOpenMedications,
                actionsEnabled = enabled && !state.refreshFailed && !state.refreshing && state.updatingMedicationId == null,
            )
            if (state.refreshing || state.updatingMedicationId != null) CaregiverInventoryUpdatingOverlay()
        }
    }
}

@Composable
private fun CaregiverInventoryList(
    patientName: String,
    items: List<CaregiverInventoryItem>,
    refreshFailed: Boolean,
    failed: Boolean,
    message: CaregiverInventoryMutationMessage?,
    onRetry: () -> Unit,
    onSelect: (CaregiverInventoryItem) -> Unit,
    onQuickRefill: (CaregiverInventoryItem) -> Unit,
    onOpenMedications: () -> Unit,
    actionsEnabled: Boolean,
) {
    var filterName by rememberSaveable { mutableStateOf(InventoryFilter.ALL.name) }
    val filter = InventoryFilter.valueOf(filterName)
    val visible = items.filter {
        when (filter) {
            InventoryFilter.ALL -> true
            InventoryFilter.LOW -> it.inventoryEnabled && !it.periodEnded && it.low
            InventoryFilter.OUT -> it.inventoryEnabled && !it.periodEnded && it.out
        }
    }
    val colors = MedicationTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-inventory-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CaregiverPatientAvatar(patientName)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.caregiver_inventory_title), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_inventory_patient, patientName), fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (refreshFailed) item { CaregiverStaleDataCard("caregiver-inventory-stale", onRetry) }
        if (failed) item { Text(stringResource(R.string.caregiver_inventory_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        message?.let { item { Text(inventoryMutationText(it), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        if (items.isEmpty()) {
            item {
                InventoryCard(MaterialTheme.colorScheme.primary) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        InventoryIcon(Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primary, 58)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.caregiver_inventory_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.caregiver_inventory_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    InventoryOnboardingStep(1, stringResource(R.string.caregiver_inventory_empty_step_medication))
                    InventoryOnboardingStep(2, stringResource(R.string.caregiver_inventory_empty_step_enable))
                    InventoryOnboardingStep(3, stringResource(R.string.caregiver_inventory_empty_step_refill))
                    Button(onClick = onOpenMedications, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth().testTag("caregiver-inventory-open-medications")) {
                        Icon(Icons.Rounded.Medication, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.caregiver_inventory_empty_action))
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_action), items.count { it.needsAction }, if (items.any { it.needsAction }) Icons.Rounded.Warning else Icons.Rounded.CheckCircle, if (items.any { it.needsAction }) colors.caregiverRed else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_managed), items.count { it.inventoryEnabled && !it.periodEnded }, Icons.Rounded.Inventory2, colors.caregiverBlue, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_not_started), items.count { !it.inventoryEnabled }, Icons.Rounded.Error, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_ended), items.count { it.periodEnded }, Icons.Rounded.EventBusy, colors.orange, Modifier.weight(1f))
                    }
                }
            }
            item { InventoryGuide(items, onSelect, actionsEnabled) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InventoryFilter.entries.forEach { candidate ->
                        InventoryFilterChip(
                            filter = candidate,
                            selected = filter == candidate,
                            onClick = { filterName = candidate.name },
                        )
                    }
                }
            }
            val sections = if (filter == InventoryFilter.ALL) listOf(
                R.string.caregiver_inventory_section_active to visible.filter { it.inventoryEnabled && !it.periodEnded },
                R.string.caregiver_inventory_section_ended to visible.filter { it.periodEnded },
                R.string.caregiver_inventory_section_unconfigured to visible.filter { !it.inventoryEnabled && !it.periodEnded },
            ) else listOf(R.string.caregiver_inventory_section_active to visible)
            sections.forEach { (title, sectionItems) ->
                if (sectionItems.isNotEmpty()) {
                    item { Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                    items(sectionItems, key = CaregiverInventoryItem::medicationId) { item ->
                        InventoryRow(item, onSelect, onQuickRefill, actionsEnabled)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun InventoryRow(item: CaregiverInventoryItem, onSelect: (CaregiverInventoryItem) -> Unit, onQuickRefill: (CaregiverInventoryItem) -> Unit, enabled: Boolean) {
    val detailDescription = stringResource(R.string.caregiver_inventory_open_detail_accessibility, item.name)
    val tint = when {
        item.needsAction -> MedicationTheme.colors.orange
        !item.inventoryEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val statusTint = when {
        item.out -> MaterialTheme.colorScheme.error
        item.low -> MedicationTheme.colors.orange
        else -> tint
    }
    InventoryCard(
        accent = if (item.needsAction) tint else null,
        modifier = Modifier.clickable(enabled = enabled) { onSelect(item) }
            .semantics {
                contentDescription = detailDescription
            }
            .testTag("caregiver-inventory-item-${item.medicationId}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InventoryMedicationIllustration(tint = tint, isPrn = item.isPrn)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                InventoryStatusBadge(inventoryStatusText(item), statusTint)
            }
            if (item.inventoryEnabled) InventoryRemainingCount(item, tint)
        }
        if (item.inventoryEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(inventoryDaysText(item), fontSize = 16.sp, lineHeight = 21.sp, color = if (item.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(inventoryHelpText(item), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.needsAction) {
                Button(onClick = { onQuickRefill(item) }, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = MedicationTheme.colors.orange), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp).testTag("caregiver-inventory-refill-${item.medicationId}")) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.caregiver_inventory_weekly_refill))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MedicationTheme.colors.elevatedBackground).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.caregiver_inventory_unconfigured_title), fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_inventory_unconfigured_message), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.caregiver_inventory_open_detail), fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun InventoryGuide(items: List<CaregiverInventoryItem>, onSelect: (CaregiverInventoryItem) -> Unit, enabled: Boolean) {
    val primary = items.filter { it.needsAction }.sortedWith(compareByDescending<CaregiverInventoryItem> { it.out }.thenBy { it.daysRemaining ?: Int.MAX_VALUE }).firstOrNull()
    val tint = if (primary == null) MaterialTheme.colorScheme.primary else MedicationTheme.colors.orange
    InventoryCard(tint) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            InventoryIcon(if (primary == null) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, tint, 34)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (primary == null) R.string.caregiver_inventory_guide_ok_title else R.string.caregiver_inventory_guide_action_title), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        primary == null -> stringResource(R.string.caregiver_inventory_guide_ok_message)
                        primary.out -> stringResource(R.string.caregiver_inventory_guide_out_message, primary.name)
                        else -> stringResource(R.string.caregiver_inventory_guide_low_message, primary.name, inventoryDaysText(primary))
                    },
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (primary != null) Button(onClick = { onSelect(primary) }, enabled = enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MedicationTheme.colors.orange), modifier = Modifier.fillMaxWidth().height(46.dp).testTag("caregiver-inventory-guide-action")) {
            Text(stringResource(R.string.caregiver_inventory_guide_action))
        }
    }
}

@Composable
private fun InventoryOnboardingStep(number: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun inventoryDaysText(item: CaregiverInventoryItem): String = when {
    item.isPrn -> "頓服"
    item.periodEnded -> stringResource(R.string.caregiver_inventory_status_ended)
    item.daysRemaining != null -> stringResource(R.string.caregiver_inventory_days, item.daysRemaining)
    else -> "—"
}

@Composable
private fun inventoryHelpText(item: CaregiverInventoryItem): String = stringResource(when {
    item.periodEnded -> R.string.caregiver_inventory_help_ended
    item.out -> R.string.caregiver_inventory_help_out
    item.low -> if (item.daysRemaining != null && !item.refillDueDate.isNullOrBlank()) R.string.caregiver_inventory_refill_due else R.string.caregiver_inventory_help_low
    item.isPrn -> R.string.caregiver_inventory_help_prn
    else -> R.string.caregiver_inventory_help_available
}, *if (item.low && item.daysRemaining != null && !item.refillDueDate.isNullOrBlank()) arrayOf(item.refillDueDate!!) else emptyArray())

@Composable
private fun CaregiverInventoryLoadingState() {
    InventoryCentered {
        CircularProgressIndicator(modifier = Modifier.size(52.dp), color = MedicationTheme.colors.primaryTealText)
        Text(stringResource(R.string.patient_today_loading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("caregiver-inventory-loading"))
    }
}

@Composable
private fun CaregiverInventoryUpdatingOverlay() {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown().consume() }
        }.testTag("caregiver-inventory-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), color = MedicationTheme.colors.primaryTealText)
                Text(stringResource(R.string.patient_today_updating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CaregiverInventoryDetail(
    patientId: String,
    item: CaregiverInventoryItem,
    repository: CaregiverInventoryRepository,
    updating: Boolean,
    failed: Boolean,
    message: CaregiverInventoryMutationMessage?,
    refreshFailed: Boolean,
    onRetry: () -> Unit,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    var inventoryEnabled by rememberSaveable(item.medicationId) { mutableStateOf(item.inventoryEnabled) }
    var refillText by rememberSaveable(item.medicationId) { mutableStateOf("") }
    var correctionText by rememberSaveable(item.medicationId) { mutableStateOf(formatInventoryNumber(item.inventoryQuantity)) }
    var confirmRefill by remember { mutableStateOf<Double?>(null) }
    var confirmCorrection by remember { mutableStateOf<Double?>(null) }
    var retryAction by remember { mutableStateOf<InventoryDetailRetryAction?>(null) }
    val scope = rememberCoroutineScope()
    val refill = refillText.toDoubleOrNull()
    val correction = correctionText.toDoubleOrNull()
    val tint = if (item.needsAction) MedicationTheme.colors.orange else MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("caregiver-inventory-detail")) {
        Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.caregiver_tab_inventory), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = {
                    scope.launch {
                        val succeeded = repository.updateSettings(patientId, item, inventoryEnabled)
                        if (succeeded) onClose() else retryAction = InventoryDetailRetryAction.SaveSettings
                    }
                },
                enabled = enabled && !updating && inventoryEnabled != item.inventoryEnabled,
                modifier = Modifier.align(Alignment.CenterEnd).testTag("inventory-save-settings"),
            ) { Text(stringResource(R.string.caregiver_inventory_save_settings), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp).testTag("caregiver-inventory-detail-scroll"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            InventoryCard(if (item.needsAction) MedicationTheme.colors.caregiverRed else MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InventoryMedicationIllustration(tint, item.isPrn, size = 56)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                        Text(inventoryDailySummary(item), fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                    InventoryDetailStatusBadge(item, tint)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.caregiver_inventory_remaining_label), fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(if (inventoryEnabled) formatInventoryNumber(item.inventoryQuantity) else "—", fontSize = 52.sp, lineHeight = 56.sp, color = if (inventoryEnabled) tint else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(5.dp))
                    Text(stringResource(R.string.caregiver_inventory_unit), fontSize = 20.sp, lineHeight = 25.sp, color = tint, fontWeight = FontWeight.Bold)
                }
                if (item.isPrn) {
                    Text("頓服", fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_inventory_prn_per_use, formatInventoryNumber(item.doseCountPerIntake)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(inventoryDaysText(item), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.caregiver_inventory_refill_due_label), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Text(item.refillDueDate ?: "—", fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (refreshFailed) item { CaregiverStaleDataCard("caregiver-inventory-detail-stale", onRetry) }
        if (failed) item {
            InventoryCard(MaterialTheme.colorScheme.error) {
                Text(stringResource(R.string.caregiver_inventory_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (retryAction != null) Button(
                    onClick = {
                        val action = retryAction ?: return@Button
                        scope.launch {
                            val succeeded = when (action) {
                                InventoryDetailRetryAction.SaveSettings -> repository.updateSettings(patientId, item, inventoryEnabled)
                                is InventoryDetailRetryAction.Refill -> repository.refill(patientId, item, action.amount)
                                is InventoryDetailRetryAction.Correction -> repository.correct(patientId, item, action.quantity)
                            }
                            if (succeeded) onClose()
                        }
                    },
                    modifier = Modifier.testTag("inventory-retry"),
                ) { Text(stringResource(R.string.common_retry)) }
            }
        }
        message?.let { item { Text(inventoryMutationText(it), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        item { InventoryDetailSectionTitle(stringResource(R.string.caregiver_inventory_settings_section)) }
        item {
            InventoryCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.caregiver_inventory_enabled), fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = inventoryEnabled, onCheckedChange = { inventoryEnabled = it }, enabled = enabled && !updating, modifier = Modifier.testTag("inventory-enabled"))
                }
            }
        }
        item { InventoryDetailSectionTitle(stringResource(R.string.caregiver_inventory_adjust_section)) }
        item {
            InventoryCard {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to R.string.caregiver_inventory_refill_week, 14 to R.string.caregiver_inventory_refill_two_weeks, 21 to R.string.caregiver_inventory_refill_three_weeks).forEach { (days, label) ->
                        InventoryPresetButton(
                            title = stringResource(label),
                            onClick = { refillText = formatInventoryNumber(plannedRefill(item, days)) },
                            enabled = enabled && !updating,
                        )
                    }
                    InventoryPresetButton(title = stringResource(R.string.caregiver_inventory_refill_custom), onClick = { refillText = "" }, enabled = enabled && !updating)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                InventoryDetailQuantityInput(
                    title = stringResource(R.string.caregiver_inventory_refill_amount),
                    value = refillText,
                    onValueChange = { refillText = it },
                    enabled = enabled && !updating,
                    showUnit = false,
                    modifier = Modifier.testTag("inventory-refill-amount"),
                )
                Button(
                    onClick = { if (refill != null && refill > 0) confirmRefill = refill },
                    enabled = enabled && !updating && item.inventoryEnabled && refill != null && refill > 0,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(58.dp).testTag("inventory-refill"),
                ) {
                    Icon(Icons.Rounded.AddCircle, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.caregiver_inventory_refill_action), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { InventoryDetailSectionTitle(stringResource(R.string.caregiver_inventory_correction_section)) }
        item {
            InventoryCard {
                InventoryDetailQuantityInput(
                    title = stringResource(R.string.caregiver_inventory_correction_quantity),
                    value = correctionText,
                    onValueChange = { correctionText = it },
                    enabled = enabled && !updating,
                    showUnit = true,
                    modifier = Modifier.testTag("inventory-correction-quantity"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Button(
                    onClick = { if (correction != null && correction >= 0) confirmCorrection = correction },
                    enabled = enabled && !updating && item.inventoryEnabled && correction != null && correction >= 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicationTheme.colors.orange),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(58.dp).testTag("inventory-correction"),
                ) { Text(stringResource(R.string.caregiver_inventory_correction_action), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        }
    }

    confirmRefill?.let { amount ->
        AlertDialog(
            onDismissRequest = { confirmRefill = null },
            title = { Text(stringResource(R.string.caregiver_inventory_confirm_refill_title)) },
            text = { Text(stringResource(R.string.caregiver_inventory_confirm_refill_message, item.name, formatInventoryNumber(amount), formatInventoryNumber(item.inventoryQuantity), formatInventoryNumber(item.inventoryQuantity + amount))) },
            dismissButton = { TextButton(onClick = { confirmRefill = null }) { Text(stringResource(R.string.caregiver_medication_form_cancel)) } },
            confirmButton = {
                TextButton(
                    onClick = { confirmRefill = null; scope.launch {
                        val succeeded = repository.refill(patientId, item, amount)
                        if (succeeded) onClose() else retryAction = InventoryDetailRetryAction.Refill(amount)
                    } },
                    enabled = enabled && !updating,
                    modifier = Modifier.testTag("inventory-refill-confirm"),
                ) {
                    Text(stringResource(R.string.caregiver_inventory_refill_action))
                }
            },
        )
    }
    confirmCorrection?.let { quantity ->
        AlertDialog(
            onDismissRequest = { confirmCorrection = null },
            title = { Text(stringResource(R.string.caregiver_inventory_confirm_correction_title)) },
            text = { Text(stringResource(R.string.caregiver_inventory_confirm_correction_message, formatInventoryNumber(quantity))) },
            dismissButton = { TextButton(onClick = { confirmCorrection = null }) { Text(stringResource(R.string.caregiver_medication_form_cancel)) } },
            confirmButton = {
                TextButton(
                    onClick = { confirmCorrection = null; scope.launch {
                        val succeeded = repository.correct(patientId, item, quantity)
                        if (succeeded) onClose() else retryAction = InventoryDetailRetryAction.Correction(quantity)
                    } },
                    enabled = enabled && !updating,
                    modifier = Modifier.testTag("inventory-correction-confirm"),
                ) {
                    Text(stringResource(R.string.caregiver_inventory_correction_action))
                }
            },
        )
    }
}

@Composable
private fun InventoryDetailStatusBadge(item: CaregiverInventoryItem, tint: Color) {
    val icon = when {
        item.out -> Icons.Rounded.Cancel
        item.low -> Icons.Rounded.Warning
        item.periodEnded -> Icons.Rounded.EventBusy
        !item.inventoryEnabled -> Icons.Rounded.Error
        else -> Icons.Rounded.CheckCircle
    }
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(inventoryStatusText(item), color = tint, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun InventoryDetailSectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(horizontal = 2.dp), fontSize = 20.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
}

@Composable
private fun InventoryPresetButton(title: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(38.dp).clip(CircleShape).background(MedicationTheme.colors.elevatedBackground),
    ) {
        Text(title, color = MedicationTheme.colors.caregiverBlue, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InventoryDetailQuantityInput(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    showUnit: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, fontWeight = FontWeight.Bold),
            modifier = modifier.width(92.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text("0", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    inner()
                }
            },
        )
        if (showUnit) Text(stringResource(R.string.caregiver_inventory_unit), fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun inventoryDailySummary(item: CaregiverInventoryItem): String {
    if (item.isPrn) return "頓服"
    val daily = item.dailyPlannedUnits
    if (daily == null || daily <= 0 || item.doseCountPerIntake <= 0) return stringResource(R.string.caregiver_inventory_daily_unknown)
    return stringResource(
        R.string.caregiver_inventory_daily_format,
        formatInventoryNumber(daily / item.doseCountPerIntake),
        formatInventoryNumber(item.doseCountPerIntake),
    )
}

@Composable
private fun InventoryMetric(label: String, value: Int, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 124.dp).shadow(3.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(value.toString(), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InventoryCard(accent: Color? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (accent == null) 1.dp else 1.5.dp, accent?.copy(alpha = 0.75f) ?: MedicationTheme.colors.cardStroke),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun InventoryFilterChip(filter: InventoryFilter, selected: Boolean, onClick: () -> Unit) {
    val tint = when (filter) {
        InventoryFilter.ALL -> MaterialTheme.colorScheme.primary
        InventoryFilter.LOW -> MedicationTheme.colors.orange
        InventoryFilter.OUT -> MedicationTheme.colors.caregiverRed
    }
    val label = when (filter) {
        InventoryFilter.ALL -> R.string.caregiver_inventory_filter_all
        InventoryFilter.LOW -> R.string.caregiver_inventory_filter_low
        InventoryFilter.OUT -> R.string.caregiver_inventory_filter_out
    }
    val icon = when (filter) {
        InventoryFilter.ALL -> Icons.AutoMirrored.Rounded.FormatListBulleted
        InventoryFilter.LOW -> Icons.Rounded.Warning
        InventoryFilter.OUT -> Icons.Rounded.Cancel
    }
    Row(
        Modifier.height(38.dp).clip(CircleShape)
            .background(if (selected) tint else MaterialTheme.colorScheme.surface)
            .border(1.dp, tint.copy(alpha = if (selected) 1f else 0.25f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .semantics { this.selected = selected }
            .testTag("caregiver-inventory-filter-${filter.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else tint, modifier = Modifier.size(18.dp))
        Text(stringResource(label), color = if (selected) Color.White else tint, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InventoryMedicationIllustration(tint: Color, isPrn: Boolean, size: Int = 62) {
    Box(
        Modifier.size(size.dp).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.07f))), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.size(width = (size * 0.68f).dp, height = (size * 0.55f).dp).shadow(4.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                .background(MedicationTheme.colors.elevatedBackground).padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isPrn) Icon(Icons.Rounded.LocalHospital, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            else MedicationPillsGlyph(tint, Modifier.size(20.dp))
            Box(Modifier.size(6.dp).background(tint.copy(alpha = 0.24f), CircleShape))
        }
    }
}

@Composable
private fun InventoryStatusBadge(text: String, tint: Color) {
    Text(
        text,
        color = tint,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun InventoryRemainingCount(item: CaregiverInventoryItem, tint: Color) {
    val label = stringResource(R.string.caregiver_inventory_remaining_label)
    val unit = stringResource(R.string.caregiver_inventory_unit)
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(SpanStyle(color = tint, fontSize = 34.sp, fontWeight = FontWeight.Bold)) {
                append(formatInventoryNumber(item.inventoryQuantity))
            }
            withStyle(SpanStyle(color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)) { append(" $unit") }
        },
        fontSize = 16.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun InventoryIcon(icon: ImageVector, tint: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun inventoryStatusText(item: CaregiverInventoryItem) = stringResource(when {
    item.periodEnded -> R.string.caregiver_inventory_status_ended
    !item.inventoryEnabled -> R.string.caregiver_inventory_status_unconfigured
    item.out -> R.string.caregiver_inventory_status_out
    item.low -> R.string.caregiver_inventory_status_low
    else -> R.string.caregiver_inventory_status_available
})

@Composable
private fun inventoryMutationText(message: CaregiverInventoryMutationMessage) = stringResource(when (message) {
    CaregiverInventoryMutationMessage.SAVED -> R.string.caregiver_inventory_saved
    CaregiverInventoryMutationMessage.REFILLED -> R.string.caregiver_inventory_refilled
    CaregiverInventoryMutationMessage.CORRECTED -> R.string.caregiver_inventory_corrected
})

private fun plannedRefill(item: CaregiverInventoryItem, days: Int): Double = when (days) {
    7 -> item.nextSevenDaysPlannedUnits
    14 -> item.nextFourteenDaysPlannedUnits
    21 -> item.nextTwentyOneDaysPlannedUnits
    else -> null
}?.takeIf { it > 0 } ?: item.dailyPlannedUnits?.takeIf { it > 0 }?.times(days.toDouble()) ?: maxOf(item.doseCountPerIntake, 1.0) * days

private fun formatInventoryNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

@Composable
private fun InventoryMessage(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    InventoryCentered { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center); action?.invoke() }
}

@Composable
private fun InventoryCentered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
}
