package com.afterlifearchive.medmanager.ui

import android.content.Intent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.CaregiverPdfReportGenerator
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverEntitlement
import com.afterlifearchive.medmanager.data.caregiver.CaregiverReportRepository
import com.afterlifearchive.medmanager.data.caregiver.ReportPeriod
import com.afterlifearchive.medmanager.data.caregiver.ReportPeriodPreset
import com.afterlifearchive.medmanager.data.caregiver.ReportPeriodValidation
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CaregiverReportAction(
    repository: CaregiverReportRepository,
    patientId: String,
    billingEnabled: Boolean,
    generatePdf: suspend (android.content.Context, com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryReport) -> com.afterlifearchive.medmanager.GeneratedPdfReport = { context, report ->
        withContext(Dispatchers.IO) { CaregiverPdfReportGenerator.generate(context, report) }
    },
    sharePdf: (android.content.Context, com.afterlifearchive.medmanager.GeneratedPdfReport, String) -> Unit = { context, generated, title ->
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, generated.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, title))
    },
) {
    if (!billingEnabled) return
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.caregiver_pdf_share_title)
    var showLock by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var localGenerationFailed by rememberSaveable { mutableStateOf(false) }
    var localGenerating by rememberSaveable { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf(ReportPeriodPreset.THIS_MONTH.name) }
    var presetExpanded by rememberSaveable { mutableStateOf(false) }
    var customFrom by rememberSaveable { mutableStateOf(LocalDate.now(TOKYO).toString()) }
    var customTo by rememberSaveable { mutableStateOf(LocalDate.now(TOKYO).toString()) }
    val today = LocalDate.now(TOKYO)
    val preset = ReportPeriodPreset.valueOf(presetName)
    val customFromDate = runCatching { LocalDate.parse(customFrom) }.getOrNull()
    val customToDate = runCatching { LocalDate.parse(customTo) }.getOrNull()
    val period = if (preset == ReportPeriodPreset.CUSTOM && customFromDate != null && customToDate != null) ReportPeriod(customFromDate, customToDate)
    else if (preset == ReportPeriodPreset.CUSTOM) null else ReportPeriod.preset(preset, today)
    val validation = period?.let { ReportPeriod.validation(it.from, it.to, today) }

    Button(
        onClick = {
            when (state.entitlement) {
                CaregiverEntitlement.PREMIUM -> showPicker = true
                CaregiverEntitlement.FREE -> showLock = true
                CaregiverEntitlement.UNKNOWN -> scope.launch {
                    if (repository.refreshEntitlement() == CaregiverEntitlement.PREMIUM) showPicker = true else if (!repository.state.value.entitlementFailed) showLock = true
                }
            }
        },
        enabled = !state.checkingEntitlement,
        modifier = Modifier.fillMaxWidth().testTag("caregiver-pdf-action"),
    ) {
        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
        Text(stringResource(R.string.caregiver_pdf_action), modifier = Modifier.padding(start = 8.dp))
    }
    if (state.checkingEntitlement) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.entitlementFailed) Text(stringResource(R.string.caregiver_pdf_entitlement_failed), color = androidx.compose.material3.MaterialTheme.colorScheme.error)

    if (showLock) {
        AlertDialog(
            onDismissRequest = { showLock = false },
            title = { Text(stringResource(R.string.caregiver_pdf_lock_title)) },
            text = { Text(stringResource(R.string.caregiver_pdf_lock_body)) },
            confirmButton = { TextButton(onClick = { showLock = false }, modifier = Modifier.testTag("caregiver-pdf-lock-close")) { Text(stringResource(R.string.caregiver_pdf_close)) } },
        )
    }
    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, modifier = Modifier.testTag("caregiver-pdf-picker")) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.caregiver_pdf_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showPicker = false }, modifier = Modifier.testTag("caregiver-pdf-picker-close")) {
                        Text(stringResource(R.string.caregiver_pdf_close))
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = !presetExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = periodLabel(preset),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.caregiver_pdf_period)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).testTag("caregiver-pdf-preset-picker"),
                    )
                    ExposedDropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                        ReportPeriodPreset.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(periodLabel(candidate)) },
                                onClick = {
                                    presetName = candidate.name
                                    presetExpanded = false
                                },
                                modifier = Modifier.testTag("caregiver-pdf-preset-${candidate.name.lowercase()}"),
                            )
                        }
                    }
                }
                if (preset == ReportPeriodPreset.CUSTOM) {
                    OutlinedTextField(customFrom, { customFrom = it }, label = { Text(stringResource(R.string.caregiver_pdf_from)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("caregiver-pdf-from"))
                    OutlinedTextField(customTo, { customTo = it }, label = { Text(stringResource(R.string.caregiver_pdf_to)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("caregiver-pdf-to"))
                }
                period?.let { Text(stringResource(R.string.caregiver_pdf_range, it.from.toString(), it.to.toString(), it.days)) }
                val validationText = when {
                    preset == ReportPeriodPreset.CUSTOM && period == null -> R.string.caregiver_pdf_validation_date
                    validation == ReportPeriodValidation.FUTURE -> R.string.caregiver_pdf_validation_future
                    validation == ReportPeriodValidation.REVERSED -> R.string.caregiver_pdf_validation_reversed
                    validation == ReportPeriodValidation.TOO_LONG -> R.string.caregiver_pdf_validation_too_long
                    else -> null
                }
                validationText?.let { Text(stringResource(it), color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.testTag("caregiver-pdf-validation")) }
                if (state.retentionCutoffDate != null) Text(stringResource(R.string.patient_history_locked_message, state.retentionDays ?: 30, state.retentionCutoffDate!!), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                if (state.generationFailed || localGenerationFailed) Text(
                    stringResource(R.string.caregiver_pdf_generation_failed),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("caregiver-pdf-generation-failed"),
                )
                if (state.generating || localGenerating) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("caregiver-pdf-generating"))
                Button(
                    onClick = {
                        val chosen = period ?: return@Button
                        scope.launch {
                            localGenerationFailed = false
                            localGenerating = true
                            try {
                                val report = repository.loadReport(patientId, chosen.from, chosen.to) ?: return@launch
                                sharePdf(context, generatePdf(context, report), shareTitle)
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                localGenerationFailed = true
                            } finally {
                                localGenerating = false
                            }
                        }
                    },
                    enabled = period != null && validation == null && !state.generating && !localGenerating,
                    modifier = Modifier.fillMaxWidth().testTag("caregiver-pdf-generate"),
                ) { Text(stringResource(R.string.caregiver_pdf_generate)) }
            }
        }
    }
}

@Composable
private fun periodLabel(value: ReportPeriodPreset) = stringResource(when (value) {
    ReportPeriodPreset.THIS_MONTH -> R.string.caregiver_pdf_this_month
    ReportPeriodPreset.LAST_MONTH -> R.string.caregiver_pdf_last_month
    ReportPeriodPreset.LAST_30_DAYS -> R.string.caregiver_pdf_last_30
    ReportPeriodPreset.LAST_90_DAYS -> R.string.caregiver_pdf_last_90
    ReportPeriodPreset.CUSTOM -> R.string.caregiver_pdf_custom
})

private val TOKYO = ZoneId.of("Asia/Tokyo")
