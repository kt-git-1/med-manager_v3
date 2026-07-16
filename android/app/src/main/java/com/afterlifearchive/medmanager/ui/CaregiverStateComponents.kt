package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.R

@Composable
internal fun CaregiverDataUnavailableState(
    enabled: Boolean,
    onRetry: () -> Unit,
    onReturnToLogin: () -> Unit,
    testTagPrefix: String,
) {
    CaregiverBlockingStateCard(
        icon = Icons.Rounded.WifiOff,
        iconIsError = true,
        title = stringResource(R.string.caregiver_data_unavailable_title),
        message = stringResource(R.string.caregiver_data_unavailable_message),
        testTag = "$testTagPrefix-unavailable",
    ) {
        Button(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("$testTagPrefix-retry"),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.common_retry), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onReturnToLogin,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .testTag("$testTagPrefix-return-login"),
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.caregiver_return_login), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun CaregiverNoPatientState(enabled: Boolean, onCreatePatient: () -> Unit, testTagPrefix: String) {
    CaregiverBlockingStateCard(
        icon = Icons.Rounded.PersonAdd,
        title = stringResource(R.string.caregiver_no_patient_title),
        message = stringResource(R.string.caregiver_no_patient_message),
        testTag = "$testTagPrefix-no-patient",
    ) {
        Button(
            onClick = onCreatePatient,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("$testTagPrefix-create-patient"),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.caregiver_create_patient_action), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun CaregiverPatientSelectionRequiredState(enabled: Boolean, onOpenPatients: () -> Unit, testTagPrefix: String) {
    CaregiverBlockingStateCard(
        icon = Icons.Rounded.Medication,
        usePillsGlyph = true,
        title = stringResource(R.string.caregiver_selection_required_title),
        message = stringResource(R.string.caregiver_selection_required_message),
        testTag = "$testTagPrefix-selection-required",
    ) {
        Button(
            onClick = onOpenPatients,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("$testTagPrefix-open-patients"),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.Group, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.caregiver_open_patients), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CaregiverBlockingStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    testTag: String,
    iconIsError: Boolean = false,
    usePillsGlyph: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp).testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier.fillMaxWidth().shadow(12.dp, shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shape).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val iconTint = if (iconIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            if (usePillsGlyph) MedicationPillsGlyph(iconTint, Modifier.size(44.dp))
            else Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(44.dp))
            Text(title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center)
            actions()
        }
    }
}
