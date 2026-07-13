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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterlifearchive.medmanager.data.session.SessionRepository
import com.afterlifearchive.medmanager.data.session.SessionState
import com.afterlifearchive.medmanager.ui.theme.MedicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AuthTeal: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AuthOrange: Color @Composable get() = MedicationTheme.colors.orange
private val AuthBackground: Color @Composable get() = MaterialTheme.colorScheme.background

private enum class AuthPage { CHOICE, LOGIN, SIGNUP }

@Composable
fun CaregiverAuthFlow(state: SessionState, repository: SessionRepository) {
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
        AuthPage.LOGIN -> CaregiverLoginScreen(state, repository) { page = AuthPage.CHOICE }
        AuthPage.SIGNUP -> CaregiverSignupScreen(state, repository) { page = AuthPage.CHOICE }
    }
}

@Composable
fun CaregiverAuthChoiceScreen(onLogin: () -> Unit, onSignup: () -> Unit, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AuthBackground).safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 48.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { CaregiverHeader("家族アカウント", "アカウントをお持ちの方はログイン、\nはじめての方は新規登録してください") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AuthChoiceCard(Icons.Rounded.AccountCircle, "ログイン", "既にアカウントをお持ちの方", AuthTeal, onLogin)
                AuthChoiceCard(Icons.Rounded.AddCircle, "新規登録", "家族アカウントを作成する", AuthOrange, onSignup)
            }
        }
        item { AuthBackButton("モードを選び直す", onBack) }
    }
}

@Composable
private fun AuthChoiceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().border(1.5.dp, tint.copy(alpha = 0.55f), shape), shape = shape, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(54.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MedicationTheme.colors.readableSecondaryText)
            }
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = tint)
            }
        }
    }
}

@Composable
private fun CaregiverLoginScreen(state: SessionState, repository: SessionRepository, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AuthFormShell(onBack) {
        FormTitle(Icons.Rounded.AccountCircle, "家族ログイン")
        AuthField(email, { email = it; repository.clearMessages() }, "Email", false)
        AuthField(password, { password = it; repository.clearMessages() }, "Password", true)
        state.errorMessage?.let { AuthError(it) }
        AuthPrimaryButton("ログイン", state.loading, email.isNotBlank() && password.isNotBlank()) {
            scope.launch { repository.loginCaregiver(email, password) }
        }
    }
}

@Composable
private fun CaregiverSignupScreen(state: SessionState, repository: SessionRepository, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var cooldown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1_000); cooldown -= 1 }
    }
    LaunchedEffect(state.canResendConfirmation) { if (state.canResendConfirmation && cooldown == 0) cooldown = 60 }
    AuthFormShell(onBack) {
        FormTitle(Icons.Rounded.AddCircle, "家族アカウント作成", "服薬を見守る家族用のアカウントを作成します")
        AuthField(email, { email = it; repository.clearMessages() }, "メールアドレス", false)
        AuthField(password, { password = it; repository.clearMessages() }, "パスワード（6文字以上）", true)
        AuthField(confirmation, { confirmation = it; repository.clearMessages() }, "パスワードをもう一度入力", true)
        state.errorMessage?.let { AuthError(it) }
        state.infoMessage?.let { AuthInfo(it) }
        if (state.canResendConfirmation) {
            TextButton(
                enabled = cooldown == 0 && !state.loading,
                onClick = { cooldown = 60; scope.launch { repository.resendSignupConfirmation(email) } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (cooldown > 0) "あと${cooldown}秒で再送" else "確認メールを再送", fontWeight = FontWeight.SemiBold) }
        }
        AuthPrimaryButton("新規登録", state.loading, email.isNotBlank() && password.isNotBlank() && confirmation.isNotBlank()) {
            scope.launch { repository.signupCaregiver(email, password, confirmation) }
        }
    }
}

@Composable
private fun AuthFormShell(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AuthBackground).safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp, 52.dp, 24.dp, 52.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { AuthBackButton("戻る", onBack) }
        item {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(24.dp)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
    }
}

@Composable
private fun FormTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = AuthTeal, modifier = Modifier.size(52.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, color = MedicationTheme.colors.readableSecondaryText, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun AuthField(value: String, onChange: (String) -> Unit, placeholder: String, secure: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onChange, placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(if (secure) Icons.Rounded.Lock else Icons.Rounded.Email, null) },
        visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secure) KeyboardType.Password else KeyboardType.Email),
        singleLine = true, shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun AuthPrimaryButton(text: String, loading: Boolean, ready: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = ready && !loading, modifier = Modifier.fillMaxWidth().height(50.dp).alpha(if (ready) 1f else 0.5f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AuthTeal)) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun AuthError(message: String) = Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)

@Composable
private fun AuthInfo(message: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFEAF4FF), RoundedCornerShape(18.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.MarkEmailRead, null, tint = Color(0xFF1976D2), modifier = Modifier.size(34.dp))
        Text(message, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CaregiverHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(62.dp).background(AuthOrange, CircleShape).border(5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(32.dp))
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = AuthTeal)
            Text(text, color = AuthTeal, fontWeight = FontWeight.SemiBold)
        }
    }
}
