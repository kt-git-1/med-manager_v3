package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.patient.MedicationSlot

@Composable
internal fun SettingsContent(
    loading: Boolean,
    error: String?,
    notificationSettings: PatientNotificationSettings,
    onNotificationSettingsChange: (PatientNotificationSettings) -> Unit,
    onOpenUrl: (String) -> Unit,
    onUnlink: () -> Unit,
) {
    var confirmUnlink by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("patient-settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 18.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.patient_settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.patient_settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard(stringResource(R.string.patient_settings_notifications)) {
                SettingsSwitchRow(stringResource(R.string.patient_settings_notification_master), stringResource(R.string.patient_settings_notification_master_detail), notificationSettings.masterEnabled) {
                    onNotificationSettingsChange(notificationSettings.copy(masterEnabled = it))
                }
                MedicationSlot.entries.forEach { slot ->
                    SettingsSwitchRow(patientSlotTitle(slot), stringResource(R.string.patient_settings_slot_notification, slot.name.lowercase()), slot in notificationSettings.enabledSlots, notificationSettings.masterEnabled) { enabled ->
                        val slots = notificationSettings.enabledSlots.toMutableSet().apply { if (enabled) add(slot) else remove(slot) }
                        onNotificationSettingsChange(notificationSettings.copy(enabledSlots = slots))
                    }
                }
                SettingsSwitchRow(stringResource(R.string.patient_settings_rereminder), stringResource(R.string.patient_settings_rereminder_detail), notificationSettings.rereminderEnabled, notificationSettings.masterEnabled) {
                    onNotificationSettingsChange(notificationSettings.copy(rereminderEnabled = it))
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.patient_settings_link_status)) {
                Text(stringResource(R.string.patient_settings_link_status_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard(stringResource(R.string.patient_settings_legal_support)) {
                SettingsLink(stringResource(R.string.patient_settings_privacy)) { onOpenUrl("https://www.okusuri-mimamori.com/privacy") }
                SettingsLink(stringResource(R.string.patient_settings_terms)) { onOpenUrl("https://www.okusuri-mimamori.com/terms") }
                SettingsLink(stringResource(R.string.patient_settings_support)) { onOpenUrl("https://www.okusuri-mimamori.com/support") }
            }
        }
        error?.let { item { PatientNoticeCard(it, MaterialTheme.colorScheme.errorContainer, null) } }
        item {
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("patient-unlink-button"),
                enabled = !loading,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = { confirmUnlink = true },
            ) { Text(stringResource(if (loading) R.string.patient_settings_unlinking else R.string.patient_settings_unlink)) }
        }
    }
    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text(stringResource(R.string.patient_settings_unlink_confirm_title)) },
            text = { Text(stringResource(R.string.patient_settings_unlink_confirm_message)) },
            confirmButton = { Button(onClick = { confirmUnlink = false; onUnlink() }) { Text(stringResource(R.string.patient_settings_unlink_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun SettingsLink(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Start)
        Text("›")
    }
}
