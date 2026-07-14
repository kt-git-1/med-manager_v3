package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afterlifearchive.medmanager.PatientNotificationSettings
import com.afterlifearchive.medmanager.R

@Composable
internal fun SettingsContent(
    loading: Boolean,
    error: String?,
    notificationSettings: PatientNotificationSettings,
    onNotificationSettingsChange: (PatientNotificationSettings) -> Unit,
    notificationPermissionDenied: Boolean,
    analyticsEnabled: Boolean,
    onAnalyticsEnabledChange: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onUnlink: () -> Unit,
) {
    var confirmUnlink by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("patient-settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 18.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PatientSettingsHeader() }
        item {
            SettingsCard(stringResource(R.string.patient_settings_notifications), Icons.Rounded.Notifications, PatientTeal) {
                SettingsSwitchRow(
                    stringResource(R.string.patient_settings_notification_master),
                    stringResource(R.string.patient_settings_notification_master_detail),
                    notificationSettings.masterEnabled,
                    enabled = !notificationPermissionDenied,
                    testTag = "patient-notification-toggle",
                ) {
                    onNotificationSettingsChange(notificationSettings.copy(masterEnabled = it))
                }
            }
        }
        item {
            SettingsCard {
                SettingsInfoRow(
                    stringResource(R.string.patient_settings_link_status),
                    stringResource(R.string.patient_settings_link_status_detail),
                    Icons.Rounded.Group,
                    PatientTeal,
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.patient_settings_analytics_title), Icons.Rounded.BarChart, PatientTeal) {
                SettingsSwitchRow(
                    stringResource(R.string.patient_settings_analytics_toggle),
                    stringResource(R.string.patient_settings_analytics_detail),
                    analyticsEnabled,
                    testTag = "patient-analytics-toggle",
                    onChecked = onAnalyticsEnabledChange,
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.patient_settings_legal_support), Icons.Rounded.Description, PatientTeal) {
                SettingsLink(stringResource(R.string.patient_settings_privacy), stringResource(R.string.patient_settings_privacy_detail), Icons.Rounded.Lock, PatientTeal) { onOpenUrl("https://www.okusuri-mimamori.com/privacy") }
                SettingsLink(stringResource(R.string.patient_settings_terms), stringResource(R.string.patient_settings_terms_detail), Icons.Rounded.Description, Color(0xFF3478F6)) { onOpenUrl("https://www.okusuri-mimamori.com/terms") }
                SettingsLink(stringResource(R.string.patient_settings_support), stringResource(R.string.patient_settings_support_detail), Icons.AutoMirrored.Rounded.Help, Color(0xFFF36A00)) { onOpenUrl("https://www.okusuri-mimamori.com/support") }
            }
        }
        if (notificationPermissionDenied) item {
            SettingsCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.patient_settings_permission_denied), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
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
            confirmButton = { Button(modifier = Modifier.testTag("patient-logout-confirm"), onClick = { confirmUnlink = false; onUnlink() }) { Text(stringResource(R.string.patient_settings_unlink_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun PatientSettingsHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = PatientTeal, modifier = Modifier.size(32.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.patient_settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.patient_settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsCard(
    title: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = PatientTeal,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = iconColor)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, testTag: String? = null, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled, modifier = if (testTag == null) Modifier else Modifier.testTag(testTag))
    }
}

@Composable
private fun SettingsInfoRow(title: String, subtitle: String, icon: ImageVector, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PatientTeal)
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(42.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.Start) {
            Text(title, textAlign = TextAlign.Start, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, textAlign = TextAlign.Start, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}
