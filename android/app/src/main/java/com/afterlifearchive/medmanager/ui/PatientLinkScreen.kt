package com.afterlifearchive.medmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.data.session.SessionRepository
import com.afterlifearchive.medmanager.data.session.SessionState
import com.afterlifearchive.medmanager.data.session.PatientLinkFailure
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import kotlinx.coroutines.launch

const val LINK_CODE_INPUT_TAG = "link-code-input"
const val LINK_CODE_SUBMIT_TAG = "link-code-submit"

@Composable
fun PatientLinkScreen(state: SessionState, repository: SessionRepository) {
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val errorMessage = state.patientLinkFailure?.let { stringResource(it.messageResource()) }
        ?: state.errorMessage?.let { sessionUserMessageText(it) }
    PatientLinkContent(
        code = code,
        loading = state.loading,
        errorMessage = errorMessage,
        onCodeChange = {
            code = it.filter(Char::isDigit).take(6)
            repository.clearError()
        },
        onSubmit = { scope.launch { repository.linkPatient(code) } },
        onBack = repository::resetMode,
    )
}

@Composable
fun PatientLinkContent(
    code: String,
    loading: Boolean,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val ready = code.length == 6
    val colors = MaterialTheme.colorScheme
    val extended = MedicationTheme.colors
    val placeholder = stringResource(R.string.patient_link_placeholder)
    val codeA11yLabel = stringResource(R.string.patient_link_a11y_code)
    val submitA11yLabel = stringResource(R.string.patient_link_a11y_submit)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 48.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        item { LinkHeader() }
        item {
            LinkCard {
                Text(placeholder, color = colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth()
                        .height(64.dp)
                        .semantics { contentDescription = codeA11yLabel }
                        .testTag(LINK_CODE_INPUT_TAG),
                    placeholder = { Text(placeholder, color = extended.readableSecondaryText.copy(alpha = 0.48f)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Numbers,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = colors.onSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.primary.copy(alpha = 0.08f),
                        unfocusedContainerColor = colors.primary.copy(alpha = 0.08f),
                        focusedBorderColor = colors.primary.copy(alpha = 0.30f),
                        unfocusedBorderColor = colors.primary.copy(alpha = 0.18f),
                    ),
                )
                errorMessage?.let { InlineLinkError(it) }
                Button(
                    onClick = onSubmit,
                    enabled = ready && !loading,
                    modifier = Modifier.fillMaxWidth()
                        .height(64.dp)
                        .alpha(if (ready) 1f else 0.55f)
                        .semantics { contentDescription = submitA11yLabel }
                        .testTag(LINK_CODE_SUBMIT_TAG),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        disabledContainerColor = colors.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.patient_link_submit), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Surface(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, colors.primary.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = colors.surface.copy(alpha = 0.75f),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = colors.primary)
                    Text(
                        stringResource(R.string.patient_link_back),
                        color = colors.primary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkHeader() {
    val colors = MaterialTheme.colorScheme
    val extended = MedicationTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(62.dp).shadow(8.dp, CircleShape).background(colors.primary, CircleShape).border(5.dp, colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp).rotate(-45f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.patient_link_title),
                color = colors.onBackground,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.patient_link_subtitle),
                color = extended.readableSecondaryText,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LinkCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, shape).background(colors.surface, shape)
            .border(1.5.dp, colors.primary.copy(alpha = 0.55f), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        content = content,
    )
}

@Composable
private fun InlineLinkError(message: String) {
    val error = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().background(error.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = error)
        Text(message, color = error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@androidx.annotation.StringRes
internal fun PatientLinkFailure.messageResource(): Int = when (this) {
    PatientLinkFailure.INVALID -> R.string.patient_link_error_invalid
    PatientLinkFailure.NOT_FOUND -> R.string.patient_link_error_not_found
    PatientLinkFailure.AUTHORIZATION -> R.string.patient_link_error_authorization
    PatientLinkFailure.NETWORK -> R.string.patient_link_error_network
    PatientLinkFailure.GENERIC -> R.string.patient_link_error_generic
}
