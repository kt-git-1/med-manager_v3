package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.session.SessionRepository
import com.afterlifearchive.medmanager.data.session.SessionState
import com.afterlifearchive.medmanager.data.patient.PatientRepository
import kotlinx.coroutines.launch

@Composable
fun MedicationApp(repository: SessionRepository, patientRepository: PatientRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.mode, state.caregiverAuthenticated) {
        if (state.mode == AppMode.CAREGIVER && state.caregiverAuthenticated) {
            repository.refreshCaregiverIfNeeded()
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state.mode) {
            null -> ModeSelectScreen(repository::selectMode)
            AppMode.CAREGIVER -> if (state.caregiverAuthenticated) {
                SessionReadyScreen(
                    stringResource(R.string.mode_select_caregiver_badge),
                    stringResource(R.string.caregiver_session_ready_message),
                    repository::logoutCaregiver,
                )
            } else {
                CaregiverAuthFlow(state, repository)
            }
            AppMode.PATIENT -> if (state.patientAuthenticated) {
                PatientHomeScreen(patientRepository, repository::unlinkPatient)
            } else {
                PatientLinkScreen(state, repository)
            }
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun AuthScreen(
    title: String,
    state: SessionState,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = ScreenColumn {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(28.dp))
    content()
    if (state.loading) {
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator()
    }
    state.errorMessage?.let {
        Spacer(Modifier.height(16.dp))
        Text(sessionUserMessageText(it), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onBack, enabled = !state.loading) { Text(stringResource(R.string.caregiver_auth_reselect_mode)) }
}

@Composable
private fun SessionReadyScreen(title: String, message: String, onLogout: () -> Unit) = ScreenColumn {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    Text(message, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
    OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.caregiver_session_logout_device)) }
}
