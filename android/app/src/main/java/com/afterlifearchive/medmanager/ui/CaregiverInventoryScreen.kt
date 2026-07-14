package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryMutationMessage
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientState
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import kotlinx.coroutines.launch

private enum class InventoryFilter { ALL, LOW, OUT }

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
        CaregiverInventoryDetail(
            patientId = selected.id,
            item = selectedItem,
            repository = repository,
            updating = state.updatingMedicationId == selectedItem.medicationId,
            failed = state.mutationFailed,
            message = state.mutationMessage,
            enabled = enabled,
            onClose = { selectedItemId = null },
        )
        return
    }

    when {
        patientState.loading && patientState.patients.isEmpty() -> InventoryCentered { CircularProgressIndicator() }
        patientState.loadFailed -> InventoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message))
        patientState.patients.isEmpty() -> InventoryMessage(stringResource(R.string.caregiver_no_patient_title), stringResource(R.string.caregiver_no_patient_message))
        selected == null -> InventoryMessage(stringResource(R.string.caregiver_no_selection_title), stringResource(R.string.caregiver_no_selection_message))
        state.loading -> InventoryCentered { CircularProgressIndicator() }
        state.loadFailed -> InventoryMessage(stringResource(R.string.caregiver_data_unavailable_title), stringResource(R.string.caregiver_data_unavailable_message)) {
            Button(onClick = { scope.launch { repository.load(selected.id) } }) { Text(stringResource(R.string.common_retry)) }
        }
        else -> CaregiverInventoryList(
            patientName = selected.displayName,
            items = state.items,
            refreshing = state.refreshing,
            failed = state.mutationFailed,
            message = state.mutationMessage,
            onSelect = { selectedItemId = it.medicationId },
            onOpenMedications = onOpenMedications,
        )
    }
}

@Composable
private fun CaregiverInventoryList(
    patientName: String,
    items: List<CaregiverInventoryItem>,
    refreshing: Boolean,
    failed: Boolean,
    message: CaregiverInventoryMutationMessage?,
    onSelect: (CaregiverInventoryItem) -> Unit,
    onOpenMedications: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InventoryIcon(Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primary, 54)
                Column {
                    Text(stringResource(R.string.caregiver_inventory_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_inventory_patient, patientName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (refreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failed) item { Text(stringResource(R.string.caregiver_inventory_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        message?.let { item { Text(inventoryMutationText(it), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        if (items.isEmpty()) {
            item {
                InventoryCard(MaterialTheme.colorScheme.primary) {
                    Text(stringResource(R.string.caregiver_inventory_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.caregiver_inventory_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenMedications, modifier = Modifier.fillMaxWidth().testTag("caregiver-inventory-open-medications")) {
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
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_action), items.count { it.needsAction }, Icons.Rounded.Warning, colors.orange, Modifier.weight(1f))
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_managed), items.count { it.inventoryEnabled && !it.periodEnded }, Icons.Rounded.Inventory2, colors.caregiverBlue, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_not_started), items.count { !it.inventoryEnabled }, Icons.Rounded.Error, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                        InventoryMetric(stringResource(R.string.caregiver_inventory_summary_ended), items.count { it.periodEnded }, Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InventoryFilter.entries.forEach { candidate ->
                        val label = when (candidate) {
                            InventoryFilter.ALL -> R.string.caregiver_inventory_filter_all
                            InventoryFilter.LOW -> R.string.caregiver_inventory_filter_low
                            InventoryFilter.OUT -> R.string.caregiver_inventory_filter_out
                        }
                        FilterChip(
                            selected = filter == candidate,
                            onClick = { filterName = candidate.name },
                            label = { Text(stringResource(label)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.testTag("caregiver-inventory-filter-${candidate.name.lowercase()}"),
                        )
                    }
                }
            }
            items(visible, key = CaregiverInventoryItem::medicationId) { item ->
                InventoryRow(item, onSelect)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun InventoryRow(item: CaregiverInventoryItem, onSelect: (CaregiverInventoryItem) -> Unit) {
    val tint = when {
        item.out -> MaterialTheme.colorScheme.error
        item.low -> MedicationTheme.colors.orange
        !item.inventoryEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    InventoryCard(if (item.needsAction) tint else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InventoryIcon(if (item.isPrn) Icons.Rounded.AddBox else Icons.Rounded.Inventory2, tint, 48)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(inventoryStatusText(item), color = tint, fontWeight = FontWeight.Bold)
            }
            Text(if (item.inventoryEnabled) stringResource(R.string.caregiver_inventory_remaining, formatInventoryNumber(item.inventoryQuantity)) else "—", fontWeight = FontWeight.Bold)
        }
        if (item.daysRemaining != null && !item.isPrn) Text(stringResource(R.string.caregiver_inventory_days, item.daysRemaining), color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.refillDueDate?.let { Text(stringResource(R.string.caregiver_inventory_refill_due, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedButton(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth().testTag("caregiver-inventory-item-${item.medicationId}")) {
            Text(stringResource(R.string.caregiver_inventory_open_detail))
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
    enabled: Boolean,
    onClose: () -> Unit,
) {
    var inventoryEnabled by rememberSaveable(item.medicationId) { mutableStateOf(item.inventoryEnabled) }
    var refillText by rememberSaveable(item.medicationId) { mutableStateOf("") }
    var correctionText by rememberSaveable(item.medicationId) { mutableStateOf(formatInventoryNumber(item.inventoryQuantity)) }
    var confirmRefill by remember { mutableStateOf<Double?>(null) }
    var confirmCorrection by remember { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    val refill = refillText.toDoubleOrNull()
    val correction = correctionText.toDoubleOrNull()
    val tint = if (item.needsAction) MedicationTheme.colors.orange else MaterialTheme.colorScheme.primary

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).testTag("caregiver-inventory-detail"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                Text(stringResource(R.string.caregiver_inventory_detail_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            InventoryCard(tint) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InventoryIcon(Icons.Rounded.Inventory2, tint, 56)
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(inventoryStatusText(item), color = tint, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (item.inventoryEnabled) stringResource(R.string.caregiver_inventory_remaining, formatInventoryNumber(item.inventoryQuantity)) else "—", style = MaterialTheme.typography.headlineMedium, color = tint, fontWeight = FontWeight.Bold)
            }
        }
        if (updating) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failed) item { Text(stringResource(R.string.caregiver_inventory_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        message?.let { item { Text(inventoryMutationText(it), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        item {
            InventoryCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.caregiver_inventory_enabled), fontWeight = FontWeight.Bold)
                    Switch(checked = inventoryEnabled, onCheckedChange = { inventoryEnabled = it }, enabled = enabled && !updating, modifier = Modifier.testTag("inventory-enabled"))
                }
                Button(
                    onClick = { scope.launch { repository.updateSettings(patientId, item, inventoryEnabled) } },
                    enabled = enabled && !updating && inventoryEnabled != item.inventoryEnabled,
                    modifier = Modifier.fillMaxWidth().testTag("inventory-save-settings"),
                ) { Text(stringResource(R.string.caregiver_inventory_save_settings)) }
            }
        }
        item {
            InventoryCard {
                Text(stringResource(R.string.caregiver_inventory_refill_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to R.string.caregiver_inventory_refill_week, 14 to R.string.caregiver_inventory_refill_two_weeks, 21 to R.string.caregiver_inventory_refill_three_weeks).forEach { (days, label) ->
                        OutlinedButton(onClick = { refillText = formatInventoryNumber(plannedRefill(item, days)) }) { Text(stringResource(label)) }
                    }
                }
                OutlinedTextField(
                    value = refillText,
                    onValueChange = { refillText = it },
                    label = { Text(stringResource(R.string.caregiver_inventory_refill_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inventory-refill-amount"),
                )
                Button(
                    onClick = { if (refill != null && refill > 0) confirmRefill = refill },
                    enabled = enabled && !updating && item.inventoryEnabled && refill != null && refill > 0,
                    modifier = Modifier.fillMaxWidth().testTag("inventory-refill"),
                ) { Text(stringResource(R.string.caregiver_inventory_refill_action)) }
            }
        }
        item {
            InventoryCard {
                Text(stringResource(R.string.caregiver_inventory_correction_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = correctionText,
                    onValueChange = { correctionText = it },
                    label = { Text(stringResource(R.string.caregiver_inventory_correction_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inventory-correction-quantity"),
                )
                Button(
                    onClick = { if (correction != null && correction >= 0) confirmCorrection = correction },
                    enabled = enabled && !updating && item.inventoryEnabled && correction != null && correction >= 0,
                    modifier = Modifier.fillMaxWidth().testTag("inventory-correction"),
                ) { Text(stringResource(R.string.caregiver_inventory_correction_action)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    confirmRefill?.let { amount ->
        AlertDialog(
            onDismissRequest = { confirmRefill = null },
            title = { Text(stringResource(R.string.caregiver_inventory_confirm_refill_title)) },
            text = { Text(stringResource(R.string.caregiver_inventory_confirm_refill_message, item.name, formatInventoryNumber(amount), formatInventoryNumber(item.inventoryQuantity + amount))) },
            dismissButton = { TextButton(onClick = { confirmRefill = null }) { Text(stringResource(R.string.caregiver_medication_form_cancel)) } },
            confirmButton = {
                TextButton(onClick = { confirmRefill = null; scope.launch { repository.refill(patientId, item, amount) } }, modifier = Modifier.testTag("inventory-refill-confirm")) {
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
                TextButton(onClick = { confirmCorrection = null; scope.launch { repository.correct(patientId, item, quantity) } }, modifier = Modifier.testTag("inventory-correction-confirm")) {
                    Text(stringResource(R.string.caregiver_inventory_correction_action))
                }
            },
        )
    }
}

@Composable
private fun InventoryMetric(label: String, value: Int, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, tint.copy(alpha = 0.2f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            InventoryIcon(icon, tint, 34)
            Column { Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = tint, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun InventoryCard(accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, accent?.copy(alpha = 0.28f) ?: MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
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
