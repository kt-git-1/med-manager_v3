package com.afterlifearchive.medmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.MedicationRecordingPolicy
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.PatientSlotTimes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TodayContent(
    doses: List<PatientDose>,
    loading: Boolean,
    updatingKey: String?,
    error: String?,
    message: String?,
    maintenanceWarning: String?,
    medications: Map<String, PatientMedication>,
    nextSlot: MedicationSlot?,
    updatingSlot: MedicationSlot?,
    prnMedications: List<PatientMedication>,
    updatingPrnMedicationId: String?,
    onRetry: () -> Unit,
    onRecord: (PatientDose) -> Unit,
    onDetail: (PatientDose) -> Unit,
    onRecordSlot: (MedicationSlot) -> Unit,
    onRecordPrn: (PatientMedication) -> Unit,
    onRemind: (PatientDose) -> Unit,
    prnError: String? = null,
    prnSuccessRevision: Long = 0,
    onClearPrnFeedback: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    scrollTargetSlot: MedicationSlot? = null,
    now: Instant = Instant.now(),
) {
    if (loading && doses.isEmpty()) {
        PatientTodayInitialLoading()
        return
    }
    if (!loading && error != null && doses.isEmpty() && prnMedications.isEmpty()) {
        PatientTodayInitialError(error)
        return
    }

    val today = now.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
    val date = today.format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_today_date_pattern), Locale.JAPANESE))
    val grouped = doses.groupBy { it.slot ?: PatientSlotTimes.DEFAULT.resolve(it.scheduledAt) }
    val nextDoses = nextSlot?.let { grouped[it] }.orEmpty()
    val takenCount = doses.count { it.status == DoseStatus.TAKEN }
    val completedSlots = MedicationSlot.entries.filter { slot ->
        grouped[slot].orEmpty().let { it.isNotEmpty() && it.all { dose -> dose.status == DoseStatus.TAKEN } }
    }
    val partialSlots = MedicationSlot.entries.filter { slot ->
        val slotDoses = grouped[slot].orEmpty()
        slot != nextSlot && slotDoses.any { it.status == DoseStatus.TAKEN } && slotDoses.any { it.status != DoseStatus.TAKEN }
    }
    val lateSlots = MedicationSlot.entries.filter { slot ->
        val slotDoses = grouped[slot].orEmpty()
        val scheduledAt = slotDoses.minOfOrNull(PatientDose::scheduledAt)
        slot != nextSlot && slot !in partialSlots && scheduledAt != null && slotDoses.any { it.status != DoseStatus.TAKEN } &&
            MedicationRecordingPolicy.isRecordable(scheduledAt, now) && MedicationRecordingPolicy.isLate(scheduledAt, now)
    }
    val compactSummarySlots = partialSlots + lateSlots + completedSlots
    val insufficientMedicationNames = buildList {
        val seenMedicationIds = mutableSetOf<String>()
        doses.forEach { dose ->
            if (
                dose.status != DoseStatus.TAKEN &&
                medications[dose.medicationId]?.isInsufficientForDose == true &&
                seenMedicationIds.add(dose.medicationId)
            ) {
                val dosage = dose.dosageText.trim()
                add(if (dosage.isEmpty() || dosage == "不明") dose.medicationName else "${dose.medicationName} $dosage")
            }
        }
    }
    val screenUpdating = refreshing || updatingKey != null || updatingSlot != null
    var showPrnSheet by rememberSaveable { mutableStateOf(false) }
    var observedPrnSuccessRevision by rememberSaveable { mutableStateOf(prnSuccessRevision) }
    var observedTakenCount by rememberSaveable { mutableStateOf(takenCount) }
    val listState = rememberLazyListState()
    val targetItemIndex = scrollTargetSlot?.let { target ->
        var index = 1
        if (insufficientMedicationNames.isNotEmpty()) index += 1
        if (error != null) index += 1
        if (message != null) index += 1
        if (maintenanceWarning != null) index += 1
        index += 1 // progress
        val nextHeroIndex = index
        index += 1
        if (prnMedications.isNotEmpty()) index += 1
        index += 1 // summary title
        if (target == nextSlot) nextHeroIndex
        else compactSummarySlots.indexOf(target).takeIf { it >= 0 }?.let(index::plus)
    }

    LaunchedEffect(prnSuccessRevision) {
        if (prnSuccessRevision > observedPrnSuccessRevision && showPrnSheet) {
            showPrnSheet = false
            onClearPrnFeedback()
        }
        observedPrnSuccessRevision = prnSuccessRevision
    }

    LaunchedEffect(scrollTargetSlot, targetItemIndex) {
        if (scrollTargetSlot != null && targetItemIndex != null) {
            listState.scrollToItem(targetItemIndex)
        }
    }

    LaunchedEffect(takenCount) {
        if (takenCount > observedTakenCount) {
            listState.animateScrollToItem(0)
        }
        observedTakenCount = takenCount
    }

    Box(Modifier.fillMaxSize()) {
        MedicationPullToRefresh(
            isRefreshing = refreshing,
            enabled = onRefresh != null && !screenUpdating && updatingPrnMedicationId == null && !showPrnSheet,
            onRefresh = { onRefresh?.invoke() },
            testTag = "patient-today-pull-refresh",
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("patient-today-list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item { PatientTodayHeader(date) }
            if (insufficientMedicationNames.isNotEmpty()) {
                item { PatientInventoryWarningCard(insufficientMedicationNames) }
            }
            error?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.errorContainer, onRetry) } }
            message?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.primaryContainer, null) } }
            maintenanceWarning?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.tertiaryContainer, null) } }

            item {
                PatientDayProgressStrip(
                    grouped = grouped,
                    nextSlot = nextSlot,
                )
            }

            item {
                NextDoseHeroCard(
                    slot = nextSlot,
                    doses = nextDoses,
                    medications = medications,
                    loading = screenUpdating,
                    updating = nextSlot != null && updatingSlot == nextSlot,
                    now = now,
                    hasLateUnrecordedSlot = grouped.values.any { slotDoses ->
                        val scheduledAt = slotDoses.minOfOrNull(PatientDose::scheduledAt) ?: return@any false
                        slotDoses.any { it.status != DoseStatus.TAKEN } &&
                            MedicationRecordingPolicy.isRecordable(scheduledAt, now) &&
                            MedicationRecordingPolicy.isLate(scheduledAt, now)
                    },
                    onRecordSlot = onRecordSlot,
                    onDetail = onDetail,
                )
            }

            if (prnMedications.isNotEmpty()) {
                item {
                    PrnEntryCard(prnMedications.size) {
                        onClearPrnFeedback()
                        showPrnSheet = true
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.patient_today_record_section_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().testTag("patient-today-planned"),
                )
            }

            if (!loading && error == null && doses.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✓", color = PatientTeal, style = MaterialTheme.typography.displaySmall)
                            Text(stringResource(R.string.patient_today_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.patient_today_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (!loading && error == null && doses.isNotEmpty() && compactSummarySlots.isEmpty()) {
                item { PatientTodayEmptyRecordCard() }
            }
            partialSlots.forEach { slot ->
                item(key = "patient-today-partial-${slot.name}") {
                    PatientTodayPartialSlotCard(
                        slot = slot,
                        doses = grouped[slot].orEmpty(),
                        medications = medications,
                        onDetail = onDetail,
                    )
                }
            }
            lateSlots.forEach { slot ->
                item(key = "patient-today-late-${slot.name}") {
                    PatientTodayLateRecordCard(
                        slot = slot,
                        doses = grouped[slot].orEmpty(),
                        medications = medications,
                        now = now,
                        updating = screenUpdating,
                        onRecordSlot = onRecordSlot,
                        onDetail = onDetail,
                    )
                }
            }
            completedSlots.forEach { slot ->
                item(key = "patient-today-completed-${slot.name}") {
                    PatientTodayCompletedSlotCard(
                        slot = slot,
                        doses = grouped[slot].orEmpty(),
                        medications = medications,
                        onDetail = onDetail,
                    )
                }
            }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }

        if ((updatingKey != null || updatingSlot != null) && !showPrnSheet) PatientTodayUpdatingOverlay()

        if (showPrnSheet) {
            val dismissPrn = {
                showPrnSheet = false
                onClearPrnFeedback()
            }
            BackHandler(onBack = dismissPrn)
            PatientPrnScreen(
                medications = prnMedications,
                disabled = screenUpdating || updatingPrnMedicationId != null,
                error = prnError,
                updating = updatingPrnMedicationId != null,
                onBack = dismissPrn,
                onRecordPrn = onRecordPrn,
            )
        }
    }
}

@Composable
private fun PatientPrnScreen(
    medications: List<PatientMedication>,
    disabled: Boolean,
    error: String?,
    updating: Boolean,
    onBack: () -> Unit,
    onRecordPrn: (PatientMedication) -> Unit,
) {
    val pane = stringResource(R.string.patient_prn_sheet_title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PatientBackground)
            .semantics { paneTitle = pane }
            .testTag("patient-prn-sheet"),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).size(48.dp).testTag("patient-prn-back"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    stringResource(R.string.patient_prn_sheet_title),
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.patient_prn_list_title),
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(medications, key = PatientMedication::id) { medication ->
                    PrnMedicationCard(
                        medication = medication,
                        disabled = disabled,
                        onRecordPrn = onRecordPrn,
                    )
                }
            }
        }
        error?.let {
            PatientPrnErrorToast(
                message = it,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
        }
        if (updating) PatientPrnUpdatingOverlay()
    }
}

@Composable
private fun PatientPrnErrorToast(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Text(message, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PatientTodayInitialLoading() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("patient-today-initial-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(38.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            strokeWidth = 4.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.patient_today_loading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PatientTodayInitialError(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("patient-today-initial-error"),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = shape,
                    ambientColor = MedicationTheme.colors.patientCardShadow,
                    spotColor = MedicationTheme.colors.patientCardShadow,
                )
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                message,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PatientTodayUpdatingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("patient-today-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier.width(172.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.app_image),
                    contentDescription = null,
                    modifier = Modifier.size(85.dp),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(51.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    strokeWidth = 4.dp,
                )
                Text(
                    stringResource(R.string.patient_today_updating),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatientInventoryWarningCard(medicationNames: List<String>) {
    val red = MedicationTheme.colors.patientRed
    val message = if (medicationNames.size == 1) {
        stringResource(R.string.patient_inventory_warning_single, medicationNames.first())
    } else {
        stringResource(R.string.patient_inventory_warning_multiple, medicationNames.size)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-inventory-warning"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, red.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(red, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.patient_inventory_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = red,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatientTodayHeader(date: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PatientHeaderIcon(Icons.Rounded.CalendarMonth)
        PatientHeaderText(
            title = stringResource(R.string.patient_today_title),
            subtitle = date,
        )
    }
}

private val PatientOrange = Color(0xFFF36A00)

@Composable
private fun PatientDayProgressStrip(
    grouped: Map<MedicationSlot, List<PatientDose>>,
    nextSlot: MedicationSlot?,
) {
    val completedConnectorCount = MedicationSlot.entries
        .takeWhile { slot -> grouped[slot].orEmpty().let { it.isNotEmpty() && it.all { dose -> dose.status == DoseStatus.TAKEN } } }
        .size
        .coerceAtMost(MedicationSlot.entries.lastIndex)
    val connectorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val completedColor = PatientTeal
    Box(
        modifier = Modifier.fillMaxWidth().height(154.dp).testTag("patient-today-progress-strip"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val start = 35.dp.toPx()
            val end = size.width - start
            val y = 77.dp.toPx()
            val stroke = 8.dp.toPx()
            drawLine(connectorColor, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
            if (completedConnectorCount > 0) {
                val completedEnd = start + (end - start) * completedConnectorCount / MedicationSlot.entries.lastIndex
                drawLine(completedColor, Offset(start, y), Offset(completedEnd, y), stroke, StrokeCap.Round)
            }
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MedicationSlot.entries.forEach { slot ->
                val slotDoses = grouped[slot].orEmpty()
                val completed = slotDoses.isNotEmpty() && slotDoses.all { it.status == DoseStatus.TAKEN }
                val takenCount = slotDoses.count { it.status == DoseStatus.TAKEN }
                val partial = takenCount > 0 && takenCount < slotDoses.size
                val takenAt = slotDoses.mapNotNull(PatientDose::takenAt).maxOrNull()
                val accent = when {
                    completed -> PatientTeal
                    partial -> PatientOrange
                    slot == nextSlot -> PatientOrange
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                }
                val emphasized = completed || partial || slot == nextSlot
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(154.dp)
                        .testTag("patient-today-progress-card-${slot.name.lowercase()}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, accent.copy(alpha = if (emphasized) 1f else 0.26f)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            patientSlotShortTitle(slot),
                            color = accent,
                            fontSize = 22.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Box(
                            Modifier.size(52.dp).background(
                                if (emphasized) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = when {
                                completed -> Icons.Rounded.Check
                                partial -> Icons.Rounded.Warning
                                slotDoses.isEmpty() -> Icons.Rounded.Remove
                                slot == MedicationSlot.BEDTIME -> Icons.Rounded.DarkMode
                                else -> Icons.Rounded.AccessTime
                            }
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        if (partial) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                Text(
                                    stringResource(R.string.patient_today_progress_partial, takenCount, slotDoses.size),
                                    color = accent,
                                    fontSize = 17.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("patient-today-progress-time-${slot.name.lowercase()}"),
                                )
                                Text(
                                    stringResource(R.string.patient_today_progress_partial_unit),
                                    color = accent,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        } else {
                            Text(
                                takenAt?.let(::instantTimeText)
                                    ?: slotDoses.minOfOrNull(PatientDose::scheduledAt)?.let(::instantTimeText)
                                    ?: "—",
                                color = accent,
                                fontSize = 19.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.testTag("patient-today-progress-time-${slot.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextDoseHeroCard(
    slot: MedicationSlot?,
    doses: List<PatientDose>,
    medications: Map<String, PatientMedication>,
    loading: Boolean,
    updating: Boolean,
    now: Instant,
    hasLateUnrecordedSlot: Boolean,
    onRecordSlot: (MedicationSlot) -> Unit,
    onDetail: (PatientDose) -> Unit,
) {
    val accent = if (slot == null) PatientTeal else patientTodaySlotColor(slot)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-next"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
    ) {
        if (slot == null || doses.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    if (hasLateUnrecordedSlot) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (hasLateUnrecordedSlot) PatientOrange else PatientTeal,
                    modifier = Modifier.size(46.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (hasLateUnrecordedSlot) R.string.patient_today_next_overdue_title else R.string.patient_today_next_done_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(if (hasLateUnrecordedSlot) R.string.patient_today_next_overdue_message else R.string.patient_today_next_done_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            return@Card
        }

        val remaining = doses.filter { it.status != DoseStatus.TAKEN }
        val insufficient = remaining.count { medications[it.medicationId]?.isInsufficientForDose == true }
        val scheduledAt = doses.minOf(PatientDose::scheduledAt)
        val withinWindow = MedicationRecordingPolicy.isRecordable(scheduledAt, now)
        val isLate = MedicationRecordingPolicy.isLate(scheduledAt, now)
        val recordableCount = remaining.size - insufficient
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.patient_today_next_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(58.dp).background(PatientTeal.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = MedicationTheme.colors.primaryTealText, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(patientTodaySlotTitle(slot), fontSize = 29.sp, fontWeight = FontWeight.Bold, color = MedicationTheme.colors.primaryTealText, maxLines = 1)
                    Text(
                        stringResource(R.string.patient_today_schedule_format, timeText(doses.first())),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                stringResource(R.string.patient_today_bulk_summary, formatPatientAmount(doses.sumOf(PatientDose::doseCount)), doses.map(PatientDose::medicationId).distinct().size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            if (isLate) {
                Text(
                    delayText(MedicationRecordingPolicy.delaySeconds(scheduledAt, now)),
                    modifier = Modifier.background(PatientOrange.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
                    color = PatientOrange,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                doses.forEach { dose ->
                    val inventoryInsufficient = medications[dose.medicationId]?.isInsufficientForDose == true
                    val dosage = dose.dosageText.trim()
                    val displayName = if (dosage.isEmpty() || dosage == "不明") dose.medicationName else "${dose.medicationName} $dosage"
                    Card(
                        onClick = { onDetail(dose) },
                        modifier = Modifier.testTag("patient-today-next-dose-${dose.key}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (inventoryInsufficient || dose.status == DoseStatus.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.patient_prn_dose_count, formatPatientAmount(dose.doseCount)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (inventoryInsufficient) {
                                    Text(
                                        stringResource(R.string.patient_inventory_insufficient),
                                        modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Icon(
                                when {
                                    inventoryInsufficient || dose.status == DoseStatus.MISSED -> Icons.Rounded.Error
                                    dose.status == DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                                    else -> Icons.Rounded.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = when {
                                    inventoryInsufficient || dose.status == DoseStatus.MISSED -> MaterialTheme.colorScheme.error
                                    dose.status == DoseStatus.TAKEN -> PatientTeal
                                    else -> MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                    }
                }
            }
            val actionEnabled = !loading && !updating && withinWindow && recordableCount > 0
            Button(
                onClick = { onRecordSlot(slot) },
                enabled = actionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    // SwiftUI applies its disabled treatment in addition to the explicit 55% label opacity.
                    .alpha(if (actionEnabled) 1f else 0.28f)
                    .testTag("patient-today-primary-bulk-record"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PatientTeal,
                    disabledContainerColor = PatientTeal,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.patient_today_bulk_action_actual, instantTimeText(now)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (insufficient > 0) Text(stringResource(R.string.patient_slot_insufficient_count, insufficient), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PrnEntryCard(count: Int, onClick: () -> Unit) {
    val orange = Color(0xFFF36A00)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("patient-today-prn-entry"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, orange.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(64.dp).background(orange.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocalHospital, contentDescription = null, tint = orange, modifier = Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.patient_prn_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.patient_prn_entry_message, count), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PatientTodayEmptyRecordCard() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-empty-record-state"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, MedicationTheme.colors.caregiverBlue.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).background(MedicationTheme.colors.caregiverBlue.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = MedicationTheme.colors.caregiverBlue, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.patient_today_summary_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.patient_today_summary_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PatientTodayPartialSlotCard(
    slot: MedicationSlot,
    doses: List<PatientDose>,
    medications: Map<String, PatientMedication>,
    onDetail: (PatientDose) -> Unit,
) {
    var expanded by rememberSaveable(slot.name) { mutableStateOf(true) }
    val takenCount = doses.count { it.status == DoseStatus.TAKEN }
    val insufficientCount = doses.count { dose ->
        dose.status != DoseStatus.TAKEN && medications[dose.medicationId]?.isInsufficientForDose == true
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-partial-slot-${slot.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, PatientOrange.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(52.dp).background(PatientOrange.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = PatientOrange, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.patient_today_summary_partial), color = PatientOrange, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.patient_today_summary_partial_detail, patientSlotShortTitle(slot), takenCount, doses.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (insufficientCount > 0) {
                        Text(
                            stringResource(R.string.patient_today_summary_partial_inventory, insufficientCount),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = PatientOrange)
            }
            if (expanded) {
                androidx.compose.material3.HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    doses.forEach { dose ->
                        PatientTodayCompactDoseRow(dose, medications[dose.medicationId]?.isInsufficientForDose == true) { onDetail(dose) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientTodayLateRecordCard(
    slot: MedicationSlot,
    doses: List<PatientDose>,
    medications: Map<String, PatientMedication>,
    now: Instant,
    updating: Boolean,
    onRecordSlot: (MedicationSlot) -> Unit,
    onDetail: (PatientDose) -> Unit,
) {
    val scheduledAt = doses.minOf(PatientDose::scheduledAt)
    val unrecorded = doses.filter { it.status != DoseStatus.TAKEN }
    val recordable = unrecorded.filter { medications[it.medicationId]?.isInsufficientForDose != true }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-slot-${slot.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, PatientOrange.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(54.dp).background(PatientOrange, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.patient_today_late_unrecorded), color = PatientOrange, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.patient_today_summary_late_detail,
                            patientSlotShortTitle(slot),
                            instantTimeText(scheduledAt),
                            delayText(MedicationRecordingPolicy.delaySeconds(scheduledAt, now)),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(stringResource(R.string.patient_today_summary_late_guide), fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                unrecorded.forEach { dose ->
                    PatientTodayCompactDoseRow(dose, medications[dose.medicationId]?.isInsufficientForDose == true) { onDetail(dose) }
                }
            }
            Button(
                onClick = { onRecordSlot(slot) },
                enabled = !updating && recordable.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("patient-today-late-record-${slot.name.lowercase()}"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PatientOrange),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.patient_today_bulk_action_actual, instantTimeText(now)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PatientTodayCompletedSlotCard(
    slot: MedicationSlot,
    doses: List<PatientDose>,
    medications: Map<String, PatientMedication>,
    onDetail: (PatientDose) -> Unit,
) {
    var expanded by rememberSaveable(slot.name) { mutableStateOf(false) }
    val scheduledAt = doses.minOf(PatientDose::scheduledAt)
    val takenAt = doses.mapNotNull(PatientDose::takenAt).maxOrNull()
    val isLate = takenAt?.let { MedicationRecordingPolicy.isLate(scheduledAt, it) } == true
    val color = if (isLate) PatientOrange else PatientTeal
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-slot-${slot.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("patient-today-summary-toggle-${slot.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(52.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if (isLate) R.string.patient_status_late else R.string.patient_today_summary_taken), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.patient_today_summary_completed_detail, patientSlotShortTitle(slot), instantTimeText(takenAt ?: scheduledAt)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(if (expanded) R.string.patient_today_summary_hide else R.string.patient_today_summary_show), color = color, fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = color)
            }
            if (expanded) {
                androidx.compose.material3.HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    doses.forEach { dose ->
                        PatientTodayCompactDoseRow(dose, medications[dose.medicationId]?.isInsufficientForDose == true) { onDetail(dose) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientTodayCompactDoseRow(dose: PatientDose, inventoryInsufficient: Boolean, onClick: () -> Unit) {
    val dosage = dose.dosageText.trim()
    val displayName = if (dosage.isEmpty() || dosage == "不明") dose.medicationName else "${dose.medicationName} $dosage"
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("patient-today-summary-dose-${dose.key}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.patient_prn_dose_count, formatPatientAmount(dose.doseCount)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                if (inventoryInsufficient && dose.status != DoseStatus.TAKEN) Text(stringResource(R.string.patient_inventory_insufficient), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Icon(
                when {
                    inventoryInsufficient && dose.status != DoseStatus.TAKEN -> Icons.Rounded.Error
                    dose.status == DoseStatus.TAKEN -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    inventoryInsufficient && dose.status != DoseStatus.TAKEN -> MaterialTheme.colorScheme.error
                    dose.status == DoseStatus.TAKEN -> PatientTeal
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

@Composable
private fun PrnMedicationCard(
    medication: PatientMedication,
    disabled: Boolean,
    onRecordPrn: (PatientMedication) -> Unit,
) {
    val orange = Color(0xFFF36A00)
    val unavailable = disabled || medication.isInsufficientForDose
    val dosage = medication.dosageText.trim()
    val displayName = if (dosage.isEmpty() || dosage == "不明") medication.name else "${medication.name} $dosage"
    val note = medication.prnInstructions?.trim().takeUnless { it.isNullOrEmpty() }
        ?: medication.notes?.trim().takeUnless { it.isNullOrEmpty() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, orange.copy(alpha = 0.32f)),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(50.dp).background(orange.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    MedicationPillsGlyph(orange, Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        displayName,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.patient_prn_dose_count, formatPatientAmount(medication.doseCountPerIntake)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    note?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (medication.isInsufficientForDose) {
                        Text(
                            stringResource(R.string.patient_inventory_insufficient),
                            modifier = Modifier.background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Button(
                onClick = { onRecordPrn(medication) },
                enabled = !unavailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .alpha(if (unavailable) 0.55f else 1f)
                    .testTag("prn-record-${medication.id}"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PatientTeal,
                    disabledContainerColor = PatientTeal,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.patient_prn_record_action), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PatientPrnUpdatingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("patient-prn-updating"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier.width(172.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.app_image),
                    contentDescription = null,
                    modifier = Modifier.size(85.dp),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(51.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    strokeWidth = 4.dp,
                )
                Text(
                    stringResource(R.string.patient_prn_updating),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DoseCard(
    dose: PatientDose,
    updating: Boolean,
    screenUpdating: Boolean,
    inventoryInsufficient: Boolean,
    onRecord: (PatientDose) -> Unit,
    onRemind: (PatientDose) -> Unit,
    onDetail: (PatientDose) -> Unit,
) {
    val taken = dose.status == DoseStatus.TAKEN
    val takenAt = dose.takenAt
    val late = takenAt?.let { MedicationRecordingPolicy.isLate(dose.scheduledAt, it) } == true
    Card(
        onClick = { onDetail(dose) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (taken) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        dose.medicationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(dose.dosageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.patient_today_schedule_format, timeText(dose)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    takenAt?.let {
                        Text(
                            stringResource(R.string.patient_today_actual_time_format, instantTimeText(it)),
                            color = if (late) PatientOrange else PatientTeal,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    takenAt?.takeIf { late }?.let { actual ->
                        Text(
                            delayText(MedicationRecordingPolicy.delaySeconds(dose.scheduledAt, actual)),
                            color = PatientOrange,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                val statusText = when {
                    late -> stringResource(R.string.patient_status_late)
                    taken -> stringResource(R.string.patient_status_taken)
                    inventoryInsufficient -> stringResource(R.string.patient_inventory_insufficient)
                    dose.status == DoseStatus.MISSED -> stringResource(R.string.patient_status_missed)
                    else -> stringResource(R.string.patient_status_pending)
                }
                Text(statusText, color = if (late) PatientOrange else if (taken) PatientTeal else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            if (!taken) {
                Spacer(Modifier.height(18.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !screenUpdating && !updating && !inventoryInsufficient,
                    onClick = { onRecord(dose) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PatientTeal),
                ) {
                    Text(
                        stringResource(
                            when {
                                inventoryInsufficient -> R.string.patient_inventory_check
                                updating -> R.string.patient_recording
                                else -> R.string.patient_taken_action
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !screenUpdating, onClick = { onRemind(dose) }) {
                    Text(stringResource(R.string.patient_remind_ten_minutes), color = PatientTeal)
                }
            }
        }
    }
}

@Composable
internal fun PatientDoseDetailContent(
    dose: PatientDose,
    medication: PatientMedication?,
    loading: Boolean = false,
    error: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 28.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    dose.medicationName,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dose.medicationName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            dose.dosageText,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                dateTimeText(dose),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            patientDetailStatusText(dose.status),
                            modifier = Modifier.background(patientDetailStatusColor(dose.status), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            item {
                PatientDoseDetailCard(
                    stringResource(R.string.patient_detail_notes),
                    medication?.notes?.trim().takeUnless { it.isNullOrEmpty() } ?: stringResource(R.string.patient_detail_no_notes),
                    insetValue = true,
                )
            }
            item {
                PatientDoseDetailCard(
                    stringResource(R.string.patient_detail_dose_amount),
                    stringResource(R.string.patient_detail_dose_value, formatPatientAmount(dose.doseCount)),
                    emphasizeValue = true,
                )
            }
            if (error) {
                item { PatientDoseDetailError(onRetry) }
            }
        }
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .testTag("patient-dose-detail-loading"),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.width(172.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            strokeWidth = 4.dp,
                        )
                        Text(
                            stringResource(R.string.patient_detail_loading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientDoseDetailError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    stringResource(R.string.patient_detail_error),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) { Text(stringResource(R.string.patient_detail_retry)) }
    }
}

@Composable
private fun PatientDoseDetailCard(
    title: String,
    value: String,
    emphasizeValue: Boolean = false,
    insetValue: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                value,
                modifier = if (insetValue) {
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
                } else {
                    Modifier
                },
                style = if (emphasizeValue) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                color = if (emphasizeValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (emphasizeValue) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun dateTimeText(dose: PatientDose): String = dose.scheduledAt.atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern(stringResource(R.string.patient_detail_date_pattern), Locale.JAPANESE))

@Composable
private fun patientDetailStatusText(status: DoseStatus) = stringResource(
    when (status) {
        DoseStatus.PENDING -> R.string.patient_detail_status_pending
        DoseStatus.TAKEN -> R.string.patient_detail_status_taken
        DoseStatus.MISSED -> R.string.patient_detail_status_missed
    },
)

@Composable
private fun patientDetailStatusColor(status: DoseStatus) = when (status) {
    DoseStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    DoseStatus.TAKEN -> Color(0xFF34C759).copy(alpha = 0.15f)
    DoseStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun SlotHeader(
    slot: MedicationSlot,
    isNext: Boolean,
    recordableCount: Int,
    insufficientCount: Int,
    isWithinRecordingWindow: Boolean,
    isLate: Boolean,
    updating: Boolean,
    onRecordSlot: (MedicationSlot) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("patient-today-slot-${slot.name.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = if (isLate) PatientOrange.copy(alpha = 0.08f) else if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(patientSlotShortTitle(slot), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = patientTodaySlotColor(slot))
                Spacer(Modifier.weight(1f))
                if (isNext) Text(stringResource(R.string.patient_next_slot), color = PatientTeal, fontWeight = FontWeight.Bold)
            }
            if (isLate && isWithinRecordingWindow) {
                Text(stringResource(R.string.patient_today_late_unrecorded), color = PatientOrange, fontWeight = FontWeight.Bold)
            }
            if (insufficientCount > 0) {
                Text(stringResource(R.string.patient_slot_insufficient_count, insufficientCount), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
            if (!isWithinRecordingWindow && recordableCount == 0) {
                Text(stringResource(R.string.patient_slot_wait_for_window), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recordableCount > 0) {
                Button(
                    onClick = { onRecordSlot(slot) },
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (updating) stringResource(R.string.patient_slot_recording) else stringResource(R.string.patient_slot_record_count, recordableCount)) }
            }
        }
    }
}

@Composable
private fun patientTodaySlotColor(slot: MedicationSlot): Color = when (slot) {
    MedicationSlot.MORNING -> MedicationTheme.colors.slotMorning
    MedicationSlot.NOON -> MedicationTheme.colors.slotNoon
    MedicationSlot.EVENING -> MedicationTheme.colors.slotEvening
    MedicationSlot.BEDTIME -> MedicationTheme.colors.slotBedtime
}

@Composable
private fun patientTodaySlotTitle(slot: MedicationSlot): String = stringResource(
    when (slot) {
        MedicationSlot.MORNING -> R.string.patient_today_slot_morning
        MedicationSlot.NOON -> R.string.patient_today_slot_noon
        MedicationSlot.EVENING -> R.string.patient_today_slot_evening
        MedicationSlot.BEDTIME -> R.string.patient_today_slot_bedtime
    },
)

private fun timeText(dose: PatientDose): String = dose.scheduledAt
    .atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun instantTimeText(instant: Instant): String = instant
    .atZone(ZoneId.of("Asia/Tokyo"))
    .format(DateTimeFormatter.ofPattern("H:mm"))

private fun delayText(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0L) / 60L
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}時間${remainingMinutes}分遅れ"
        hours > 0 -> "${hours}時間遅れ"
        else -> "${minutes}分遅れ"
    }
}
