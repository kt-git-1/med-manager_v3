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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.session.SessionRepository
import com.afterlifearchive.medmanager.data.session.SessionState
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AuthTeal: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AuthOrange: Color @Composable get() = MedicationTheme.colors.orange
private val AuthBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val AuthFormIsDark: Boolean @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f
private val AuthFormAccent: Color @Composable get() = if (AuthFormIsDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
private val AuthFormBackground: Color @Composable get() = if (AuthFormIsDark) Color.Black else Color.White
private val AuthFormCard: Color @Composable get() = if (AuthFormIsDark) Color(0xFF111111) else Color(0xFFFAFAFA)
private val AuthFormField: Color @Composable get() = if (AuthFormIsDark) Color(0xFF232323) else Color(0xFFE7E7E7)

private enum class AuthPage { CHOICE, LOGIN, SIGNUP }

const val AUTH_EMAIL_TAG = "caregiver-auth-email"
const val AUTH_PASSWORD_TAG = "caregiver-auth-password"
const val AUTH_CONFIRMATION_TAG = "caregiver-auth-confirmation"
const val AUTH_SUBMIT_TAG = "caregiver-auth-submit"
const val AUTH_NAVIGATION_BACK_TAG = "caregiver-auth-navigation-back"
const val AUTH_FORM_LIST_TAG = "caregiver-auth-form-list"
const val AUTH_RESEND_TAG = "caregiver-auth-resend"
const val AUTH_ERROR_TAG = "caregiver-auth-error"
const val AUTH_INFO_TAG = "caregiver-auth-info"

@Composable
fun CaregiverAuthFlow(
    state: SessionState,
    repository: SessionRepository,
    resendCooldownSeconds: Int = 60,
) {
    var page by remember { mutableStateOf(AuthPage.CHOICE) }
    LaunchedEffect(state.caregiverLoginRequested) {
        if (state.caregiverLoginRequested) {
            page = AuthPage.LOGIN
            repository.consumeCaregiverLoginRequest()
        }
    }
    when (page) {
        AuthPage.CHOICE -> CaregiverAuthChoiceScreen(
            onLogin = { page = AuthPage.LOGIN },
            onSignup = { page = AuthPage.SIGNUP },
            onBack = repository::resetMode,
        )
        AuthPage.LOGIN -> CaregiverLoginScreen(state, repository) {
            repository.clearAuthFlowState()
            page = AuthPage.CHOICE
        }
        AuthPage.SIGNUP -> CaregiverSignupScreen(state, repository, resendCooldownSeconds) {
            repository.clearAuthFlowState()
            page = AuthPage.CHOICE
        }
    }
}

@Composable
fun CaregiverAuthChoiceScreen(onLogin: () -> Unit, onSignup: () -> Unit, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AuthBackground).safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 48.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { CaregiverHeader(stringResource(R.string.caregiver_auth_title), stringResource(R.string.caregiver_auth_subtitle)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AuthChoiceCard(Icons.Rounded.ArrowCircleRight, stringResource(R.string.caregiver_auth_login), stringResource(R.string.caregiver_auth_login_subtitle), AuthTeal, "caregiver-auth-choice-login", onLogin)
                AuthChoiceCard(Icons.Rounded.PersonAdd, stringResource(R.string.caregiver_auth_signup), stringResource(R.string.caregiver_auth_signup_subtitle), AuthOrange, "caregiver-auth-choice-signup", onSignup)
            }
        }
        item { AuthBackButton(stringResource(R.string.caregiver_auth_reselect_mode), onBack) }
    }
}

@Composable
private fun AuthChoiceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color, testTag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().border(1.5.dp, tint.copy(alpha = 0.55f), shape).testTag(testTag), shape = shape, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(54.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MedicationTheme.colors.readableSecondaryText)
            }
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronRight, null, tint = tint)
            }
        }
    }
}

@Composable
private fun CaregiverLoginScreen(state: SessionState, repository: SessionRepository, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val ready = email.isNotBlank() && password.isNotBlank()
    val submit = {
        if (ready && !state.loading) {
            focusManager.clearFocus()
            scope.launch { repository.loginCaregiver(email, password) }
        }
    }
    AuthFormShell(onBack) {
        FormTitle(Icons.Rounded.AccountCircle, stringResource(R.string.caregiver_login_title))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthField(
                email,
                { email = it; repository.clearMessages() },
                stringResource(R.string.caregiver_auth_email),
                false,
                AUTH_EMAIL_TAG,
                imeAction = ImeAction.Next,
                onImeAction = passwordFocus::requestFocus,
            )
            AuthField(
                password,
                { password = it; repository.clearMessages() },
                stringResource(R.string.caregiver_auth_password),
                true,
                AUTH_PASSWORD_TAG,
                focusRequester = passwordFocus,
                imeAction = ImeAction.Done,
                onImeAction = submit,
            )
        }
        state.errorMessage?.let { AuthError(sessionUserMessageText(it)) }
        AuthPrimaryButton(stringResource(R.string.caregiver_auth_login), state.loading, ready, submit)
    }
}

@Composable
private fun CaregiverSignupScreen(
    state: SessionState,
    repository: SessionRepository,
    resendCooldownSeconds: Int,
    onBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var cooldown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val ready = email.isNotBlank() && password.isNotBlank() && confirmation.isNotBlank()
    val submit = {
        if (ready && !state.loading) {
            focusManager.clearFocus()
            scope.launch { repository.signupCaregiver(email, password, confirmation) }
        }
    }
    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1_000); cooldown -= 1 }
    }
    LaunchedEffect(state.resendCooldownRevision) {
        if (state.resendCooldownRevision > 0) cooldown = resendCooldownSeconds
    }
    AuthFormShell(onBack, cardSpacing = 45.dp) {
        FormTitle(
            Icons.Rounded.PersonAdd,
            stringResource(R.string.caregiver_signup_title),
            stringResource(R.string.caregiver_signup_subtitle),
            iconSize = 68.dp,
            titleSize = 30.sp,
            subtitleSize = 18.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthField(
                email,
                { email = it; repository.clearMessages() },
                stringResource(R.string.caregiver_signup_email),
                false,
                AUTH_EMAIL_TAG,
                imeAction = ImeAction.Next,
                onImeAction = passwordFocus::requestFocus,
            )
            AuthField(
                password,
                { password = it; repository.clearMessages() },
                stringResource(R.string.caregiver_signup_password),
                true,
                AUTH_PASSWORD_TAG,
                focusRequester = passwordFocus,
                imeAction = ImeAction.Next,
                onImeAction = confirmationFocus::requestFocus,
            )
            AuthField(
                confirmation,
                { confirmation = it; repository.clearMessages() },
                stringResource(R.string.caregiver_signup_password_confirmation),
                true,
                AUTH_CONFIRMATION_TAG,
                leadingIcon = Icons.Rounded.LockReset,
                focusRequester = confirmationFocus,
                imeAction = ImeAction.Done,
                onImeAction = submit,
            )
        }
        state.errorMessage?.let { AuthError(sessionUserMessageText(it)) }
        state.infoMessage?.let { AuthInfo(sessionUserMessageText(it)) }
        if (state.canResendConfirmation) {
            TextButton(
                enabled = cooldown == 0 && !state.loading && !state.resendingConfirmation,
                onClick = { scope.launch { repository.resendSignupConfirmation(email) } },
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                    .testTag(AUTH_RESEND_TAG),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AuthFormAccent,
                    disabledContentColor = MedicationTheme.colors.readableSecondaryText,
                ),
            ) {
                if (state.resendingConfirmation) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                } else {
                    Icon(
                        if (cooldown > 0) Icons.Rounded.Schedule else Icons.Rounded.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (cooldown > 0) stringResource(R.string.caregiver_signup_resend_countdown, cooldown)
                        else stringResource(R.string.caregiver_signup_resend),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        AuthPrimaryButton(stringResource(R.string.caregiver_auth_signup), state.loading, ready, submit)
    }
}

@Composable
private fun AuthFormShell(
    onBack: () -> Unit,
    cardSpacing: androidx.compose.ui.unit.Dp = 52.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AuthFormBackground).safeDrawingPadding().testTag(AUTH_FORM_LIST_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp, 16.dp, 24.dp, 52.dp),
        verticalArrangement = Arrangement.spacedBy(cardSpacing),
    ) {
        item { AuthNavigationBackButton(onBack) }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .shadow(14.dp, cardShape)
                    .clip(cardShape)
                    .background(AuthFormCard)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), cardShape)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AuthNavigationBackButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp).testTag(AUTH_NAVIGATION_BACK_TAG),
        color = AuthFormCard,
        shape = CircleShape,
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun FormTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconSize: androidx.compose.ui.unit.Dp = 52.dp,
    titleSize: androidx.compose.ui.unit.TextUnit = 28.sp,
    subtitleSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = AuthFormAccent, modifier = Modifier.size(iconSize))
        Text(title, fontSize = titleSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(
                it,
                color = MedicationTheme.colors.readableSecondaryText,
                fontSize = subtitleSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    secure: Boolean,
    testTag: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .testTag(testTag),
        leadingIcon = { Icon(leadingIcon ?: if (secure) Icons.Rounded.Lock else Icons.Rounded.Email, null) },
        visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secure) KeyboardType.Password else KeyboardType.Email,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent,
            focusedContainerColor = AuthFormField,
            unfocusedContainerColor = AuthFormField,
            disabledContainerColor = AuthFormField,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun AuthPrimaryButton(text: String, loading: Boolean, ready: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = ready && !loading, modifier = Modifier.fillMaxWidth().height(50.dp).alpha(if (ready) 1f else 0.5f).testTag(AUTH_SUBMIT_TAG), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AuthFormAccent, contentColor = Color.White, disabledContainerColor = AuthFormAccent, disabledContentColor = Color.White)) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthError(message: String) {
    val error = MaterialTheme.colorScheme.error
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(error.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .border(1.dp, error.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(22.dp).semantics(mergeDescendants = true) {}.testTag(AUTH_ERROR_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = error,
            modifier = Modifier.size(36.dp),
        )
        Text(
            message,
            color = error,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthInfo(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(MedicationTheme.colors.authInfoContainer, RoundedCornerShape(18.dp))
            .padding(22.dp).semantics(mergeDescendants = true) {}.testTag(AUTH_INFO_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.MarkEmailRead, null, tint = MedicationTheme.colors.authInfoIcon, modifier = Modifier.size(34.dp))
        Text(message, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CaregiverHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(62.dp).shadow(8.dp, CircleShape).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface).testTag("caregiver-auth-header-icon"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AdminPanelSettings, null, tint = AuthTeal, modifier = Modifier.size(42.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MedicationTheme.colors.readableSecondaryText, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AuthBackButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp).border(1.dp, AuthTeal.copy(alpha = 0.18f), RoundedCornerShape(16.dp)), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), shape = RoundedCornerShape(16.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ChevronLeft, null, tint = AuthTeal)
            Text(text, color = AuthTeal, fontWeight = FontWeight.SemiBold)
        }
    }
}
