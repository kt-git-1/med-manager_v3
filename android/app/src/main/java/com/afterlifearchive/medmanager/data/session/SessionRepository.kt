package com.afterlifearchive.medmanager.data.session

import com.afterlifearchive.medmanager.data.auth.AuthService
import com.afterlifearchive.medmanager.data.auth.AuthException
import com.afterlifearchive.medmanager.data.auth.AuthFailure
import com.afterlifearchive.medmanager.data.auth.AuthSession
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.ui.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant

data class SessionState(
    val mode: AppMode? = null,
    val caregiverAuthenticated: Boolean = false,
    val patientAuthenticated: Boolean = false,
    val loading: Boolean = false,
    val errorMessage: SessionUserMessage? = null,
    val patientLinkFailure: PatientLinkFailure? = null,
    val infoMessage: SessionUserMessage? = null,
    val canResendConfirmation: Boolean = false,
    val caregiverLoginRequested: Boolean = false,
)

sealed interface SessionUserMessage {
    data class Raw(val value: String) : SessionUserMessage
    data object InvalidEmail : SessionUserMessage
    data object PasswordTooShort : SessionUserMessage
    data object PasswordMismatch : SessionUserMessage
    data object ConfirmationSent : SessionUserMessage
    data object ConfirmationResent : SessionUserMessage
    data object Unexpected : SessionUserMessage
    data object MissingCredentials : SessionUserMessage
    data object InvalidInput : SessionUserMessage
    data object MissingAuthConfiguration : SessionUserMessage
    data object MissingAuthToken : SessionUserMessage
    data object InvalidCredentials : SessionUserMessage
    data object EmailNotConfirmed : SessionUserMessage
    data object RateLimited : SessionUserMessage
    data object LoginFailed : SessionUserMessage
    data object Network : SessionUserMessage
}

enum class PatientLinkFailure {
    INVALID,
    NOT_FOUND,
    AUTHORIZATION,
    NETWORK,
    GENERIC,
}

class SessionRepository(
    private val storage: SessionStorage,
    private val authService: AuthService,
    private val apiClient: ApiClient,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
    private val caregiverSelection: CaregiverSelectionRepository = CaregiverSelectionRepository(storage),
) {
    private val patientRefreshMutex = Mutex()
    private val caregiverRefreshMutex = Mutex()
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun restore() {
        expireInvalidSessions()
        caregiverSelection.restore()
        mutableState.value = SessionState(
            mode = storage.mode,
            caregiverAuthenticated = storage.getSecret(CAREGIVER_ACCESS) != null,
            patientAuthenticated = storage.getSecret(PATIENT_ACCESS) != null,
        )
    }

    fun selectMode(mode: AppMode) {
        storage.mode = mode
        mutableState.value = mutableState.value.copy(
            mode = mode,
            errorMessage = null,
            patientLinkFailure = null,
        )
    }

    fun resetMode() {
        storage.mode = null
        mutableState.value = mutableState.value.copy(
            mode = null,
            errorMessage = null,
            patientLinkFailure = null,
        )
    }

    suspend fun loginCaregiver(email: String, password: String) = runOperation {
        saveCaregiver(authService.login(email, password))
    }

    suspend fun signupCaregiver(email: String, password: String, confirmation: String) {
        mutableState.value = mutableState.value.copy(loading = false, errorMessage = null, infoMessage = null, canResendConfirmation = false)
        val trimmed = email.trim()
        val error = when {
            !EMAIL_REGEX.matches(trimmed) -> SessionUserMessage.InvalidEmail
            password.length < 6 -> SessionUserMessage.PasswordTooShort
            password != confirmation -> SessionUserMessage.PasswordMismatch
            else -> null
        }
        if (error != null) {
            mutableState.value = mutableState.value.copy(errorMessage = error)
            return
        }
        mutableState.value = mutableState.value.copy(loading = true)
        try {
            val session = authService.signup(trimmed, password)
            if (session.accessToken.isNullOrBlank()) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    infoMessage = SessionUserMessage.ConfirmationSent,
                    canResendConfirmation = true,
                )
            } else {
                saveCaregiver(session)
                restore()
            }
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(loading = false, errorMessage = error.toSessionUserMessage())
        }
    }

    suspend fun resendSignupConfirmation(email: String) {
        mutableState.value = mutableState.value.copy(errorMessage = null, infoMessage = null)
        try {
            authService.resendSignupConfirmation(email.trim())
            mutableState.value = mutableState.value.copy(infoMessage = SessionUserMessage.ConfirmationResent)
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(errorMessage = error.toSessionUserMessage())
        }
    }

    suspend fun linkPatient(code: String) {
        mutableState.value = mutableState.value.copy(
            loading = true,
            errorMessage = null,
            patientLinkFailure = null,
        )
        try {
            val normalized = code.filter(Char::isDigit)
            if (normalized.length != 6) throw ApiException.Validation("invalid link code")
            val response = apiClient.post(
                "api/patient/link",
                JSONObject().put("code", normalized),
                RequestAuthPolicy.PUBLIC,
                allowsAuthRefresh = false,
            )
            savePatientSession(PatientSessionToken.fromJson(response))
            storage.mode = AppMode.PATIENT
            restore()
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                errorMessage = null,
                patientLinkFailure = error.toPatientLinkFailure(),
            )
        }
    }

    suspend fun refreshPatientIfNeeded(): Boolean = refreshPatientSession(force = false)

    suspend fun forceRefreshPatientSession(): Boolean = refreshPatientSession(force = true)

    fun invalidatePatientSession() {
        storage.clearSecrets(listOf(PATIENT_ACCESS, PATIENT_EXPIRES))
        restore()
    }

    suspend fun refreshCaregiverIfNeeded(): Boolean = caregiverRefreshMutex.withLock {
        if (storage.mode != AppMode.CAREGIVER || storage.getSecret(CAREGIVER_ACCESS) == null) {
            return@withLock false
        }
        val expiry = storage.getSecret(CAREGIVER_EXPIRES)?.toLongOrNull() ?: return@withLock true
        if (expiry - nowEpochSeconds() > CAREGIVER_REFRESH_BUFFER_SECONDS) return@withLock true
        val refreshToken = storage.getSecret(CAREGIVER_REFRESH)
        if (refreshToken == null) {
            if (expiry <= nowEpochSeconds()) invalidateCaregiverSession()
            return@withLock storage.getSecret(CAREGIVER_ACCESS) != null
        }
        runCatching { saveCaregiver(authService.refresh(refreshToken), preserveCurrentPatientId = true) }
            .onFailure { invalidateCaregiverSession() }
        restore()
        storage.getSecret(CAREGIVER_ACCESS) != null
    }

    fun logoutCaregiver() {
        storage.clearSecrets(listOf(CAREGIVER_ACCESS, CAREGIVER_REFRESH, CAREGIVER_EXPIRES))
        caregiverSelection.clear()
        if (storage.mode == AppMode.CAREGIVER) storage.mode = null
        restore()
    }

    fun invalidateCaregiverSession() {
        storage.clearSecrets(listOf(CAREGIVER_ACCESS, CAREGIVER_REFRESH, CAREGIVER_EXPIRES))
        caregiverSelection.clear()
        restore()
    }

    fun unlinkPatient() {
        storage.clearSecrets(listOf(PATIENT_ACCESS, PATIENT_EXPIRES))
        if (storage.mode == AppMode.PATIENT) storage.mode = null
        restore()
    }

    fun patientAuthorizationToken(): String? = storage.getSecret(PATIENT_ACCESS)

    fun caregiverAuthorizationToken(): String? = storage.getSecret(CAREGIVER_ACCESS)

    fun isCaregiverSession(): Boolean = storage.mode == AppMode.CAREGIVER && caregiverAuthorizationToken() != null

    fun authorizationToken(): String? = when (storage.mode) {
        AppMode.CAREGIVER -> storage.getSecret(CAREGIVER_ACCESS)
        AppMode.PATIENT -> storage.getSecret(PATIENT_ACCESS)
        null -> null
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(errorMessage = null, patientLinkFailure = null)
    }

    fun clearMessages() {
        mutableState.value = mutableState.value.copy(
            errorMessage = null,
            patientLinkFailure = null,
            infoMessage = null,
        )
    }

    fun handleAuthCallback(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val accepted = when {
            uri.scheme.equals("okusurimimamori", true) && uri.host.equals("auth", true) && uri.path == "/login" -> true
            uri.scheme.equals("https", true) && uri.host?.lowercase() in setOf("okusuri-mimamori.com", "www.okusuri-mimamori.com") && uri.path in setOf("/auth/confirmed", "/auth/login") -> true
            else -> false
        }
        if (!accepted) return false
        logoutCaregiver()
        storage.mode = AppMode.CAREGIVER
        restore()
        mutableState.value = mutableState.value.copy(caregiverLoginRequested = true)
        return true
    }

    fun consumeCaregiverLoginRequest() {
        mutableState.value = mutableState.value.copy(caregiverLoginRequested = false)
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(loading = true, errorMessage = null)
        try {
            block()
            restore()
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                errorMessage = error.toSessionUserMessage(),
            )
        }
    }

    private fun saveCaregiver(session: AuthSession, preserveCurrentPatientId: Boolean = false) {
        val accessToken = session.accessToken?.takeIf(String::isNotBlank) ?: return
        storage.putSecret(CAREGIVER_ACCESS, normalizeCaregiverToken(accessToken))
        session.refreshToken?.let { storage.putSecret(CAREGIVER_REFRESH, it) }
        val duration = session.expiresInSeconds?.takeIf { it > 0 } ?: DEFAULT_SESSION_SECONDS
        storage.putSecret(CAREGIVER_EXPIRES, (nowEpochSeconds() + duration).toString())
        storage.mode = AppMode.CAREGIVER
        if (!preserveCurrentPatientId) caregiverSelection.clear()
    }

    private suspend fun refreshPatientSession(force: Boolean): Boolean = patientRefreshMutex.withLock {
        if (storage.mode != AppMode.PATIENT || storage.getSecret(PATIENT_ACCESS) == null) return@withLock false
        val expiresAt = storage.getSecret(PATIENT_EXPIRES)?.toLongOrNull()
        if (!force && expiresAt != null && expiresAt - nowEpochSeconds() > PATIENT_REFRESH_BUFFER_SECONDS) {
            return@withLock true
        }
        return@withLock runCatching {
            val response = apiClient.post(
                "api/patient/session/refresh",
                JSONObject(),
                RequestAuthPolicy.PATIENT,
                allowsAuthRefresh = false,
            )
            savePatientSession(PatientSessionToken.fromJson(response))
        }.isSuccess
    }

    private fun savePatientSession(session: PatientSessionToken) {
        storage.putSecret(PATIENT_ACCESS, session.token)
        session.expiresAtEpochSeconds?.let { storage.putSecret(PATIENT_EXPIRES, it.toString()) }
            ?: storage.putSecret(PATIENT_EXPIRES, (nowEpochSeconds() + DEFAULT_SESSION_SECONDS).toString())
    }

    private fun expireInvalidSessions() {
        val now = nowEpochSeconds()
        val caregiverExpired = storage.getSecret(CAREGIVER_EXPIRES)?.toLongOrNull()?.let { it <= now } == true
        if (caregiverExpired && storage.getSecret(CAREGIVER_REFRESH) == null) {
            storage.clearSecrets(listOf(CAREGIVER_ACCESS, CAREGIVER_EXPIRES))
        }
        val patientExpired = storage.getSecret(PATIENT_EXPIRES)?.toLongOrNull()?.let { it <= now } == true
        if (patientExpired) storage.clearSecrets(listOf(PATIENT_ACCESS, PATIENT_EXPIRES))
    }

    private fun normalizeCaregiverToken(token: String) = if (token.startsWith(CAREGIVER_PREFIX)) token else "$CAREGIVER_PREFIX$token"

    companion object {
        const val CAREGIVER_ACCESS = "caregiverToken"
        const val CAREGIVER_REFRESH = "caregiverRefreshToken"
        const val CAREGIVER_EXPIRES = "caregiverSessionExpiresAt"
        const val PATIENT_ACCESS = "patientToken"
        const val PATIENT_EXPIRES = "patientSessionExpiresAt"
        private const val CAREGIVER_PREFIX = "caregiver-"
        private const val DEFAULT_SESSION_SECONDS = 30L * 24 * 60 * 60
        private const val CAREGIVER_REFRESH_BUFFER_SECONDS = 2L * 60
        private const val PATIENT_REFRESH_BUFFER_SECONDS = 30L * 24 * 60 * 60
        private val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }
}

private fun Throwable.toSessionUserMessage(): SessionUserMessage = when (this) {
    is AuthException -> when (failure) {
        AuthFailure.MISSING_CREDENTIALS -> SessionUserMessage.MissingCredentials
        AuthFailure.MISSING_REFRESH_TOKEN, AuthFailure.MISSING_ACCESS_TOKEN -> SessionUserMessage.MissingAuthToken
        AuthFailure.INVALID_INPUT -> SessionUserMessage.InvalidInput
        AuthFailure.INVALID_EMAIL -> SessionUserMessage.InvalidEmail
        AuthFailure.MISSING_CONFIGURATION -> SessionUserMessage.MissingAuthConfiguration
        AuthFailure.INVALID_CREDENTIALS -> SessionUserMessage.InvalidCredentials
        AuthFailure.EMAIL_NOT_CONFIRMED -> SessionUserMessage.EmailNotConfirmed
        AuthFailure.RATE_LIMITED -> SessionUserMessage.RateLimited
        AuthFailure.LOGIN_FAILED -> SessionUserMessage.LoginFailed
    }
    is ApiException.Network -> SessionUserMessage.Network
    else -> message?.takeIf(String::isNotBlank)?.let(SessionUserMessage::Raw) ?: SessionUserMessage.Unexpected
}

internal fun Throwable.toPatientLinkFailure(): PatientLinkFailure = when (this) {
    is ApiException.Validation -> PatientLinkFailure.INVALID
    is ApiException.NotFound, is ApiException.Conflict -> PatientLinkFailure.NOT_FOUND
    is ApiException.Unauthorized, is ApiException.Forbidden -> PatientLinkFailure.AUTHORIZATION
    is ApiException.Network -> PatientLinkFailure.NETWORK
    else -> PatientLinkFailure.GENERIC
}

data class PatientSessionToken(val token: String, val expiresAtEpochSeconds: Long?) {
    companion object {
        fun fromJson(response: JSONObject): PatientSessionToken {
            val data = response.getJSONObject("data")
            val token = data.getString("patientSessionToken")
            require(token.isNotBlank()) { "missing_patient_session_token" }
            val expiresAt = data.optString("expiresAt")
                .takeIf(String::isNotBlank)
                ?.let { Instant.parse(it).epochSecond }
            return PatientSessionToken(token, expiresAt)
        }
    }
}
