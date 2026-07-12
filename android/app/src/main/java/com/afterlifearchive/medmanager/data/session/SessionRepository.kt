package com.afterlifearchive.medmanager.data.session

import com.afterlifearchive.medmanager.data.auth.AuthService
import com.afterlifearchive.medmanager.data.auth.AuthSession
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
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
    val currentPatientId: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

class SessionRepository(
    private val storage: SessionStorage,
    private val authService: AuthService,
    private val apiClient: ApiClient,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) {
    private val patientRefreshMutex = Mutex()
    private val caregiverRefreshMutex = Mutex()
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun restore() {
        expireInvalidSessions()
        mutableState.value = SessionState(
            mode = storage.mode,
            caregiverAuthenticated = storage.getSecret(CAREGIVER_ACCESS) != null,
            patientAuthenticated = storage.getSecret(PATIENT_ACCESS) != null,
            currentPatientId = storage.currentPatientId,
        )
    }

    fun selectMode(mode: AppMode) {
        storage.mode = mode
        mutableState.value = mutableState.value.copy(mode = mode, errorMessage = null)
    }

    fun resetMode() {
        storage.mode = null
        mutableState.value = mutableState.value.copy(mode = null, errorMessage = null)
    }

    suspend fun loginCaregiver(email: String, password: String) = runOperation {
        saveCaregiver(authService.login(email, password))
    }

    suspend fun linkPatient(code: String) = runOperation {
        val normalized = code.filter(Char::isDigit)
        if (normalized.length != 6) throw ApiException.Validation("6桁の連携コードを入力してください。")
        val response = apiClient.post("api/patient/link", JSONObject().put("code", normalized))
        savePatientSession(PatientSessionToken.fromJson(response))
        storage.mode = AppMode.PATIENT
    }

    suspend fun refreshPatientIfNeeded(): Boolean = refreshPatientSession(force = false)

    suspend fun forceRefreshPatientSession(): Boolean = refreshPatientSession(force = true)

    fun isPatientSession(): Boolean = storage.mode == AppMode.PATIENT && storage.getSecret(PATIENT_ACCESS) != null

    fun invalidatePatientSession() {
        storage.clearSecrets(listOf(PATIENT_ACCESS, PATIENT_EXPIRES))
        restore()
    }

    suspend fun refreshCaregiverIfNeeded() = caregiverRefreshMutex.withLock {
        if (storage.mode != AppMode.CAREGIVER || storage.getSecret(CAREGIVER_ACCESS) == null) return@withLock
        val expiry = storage.getSecret(CAREGIVER_EXPIRES)?.toLongOrNull() ?: return@withLock
        if (expiry - nowEpochSeconds() > CAREGIVER_REFRESH_BUFFER_SECONDS) return@withLock
        val refreshToken = storage.getSecret(CAREGIVER_REFRESH)
        if (refreshToken == null) {
            invalidateCaregiverSession()
            return@withLock
        }
        runCatching { saveCaregiver(authService.refresh(refreshToken), preserveCurrentPatientId = true) }
            .onFailure { invalidateCaregiverSession() }
        restore()
    }

    fun logoutCaregiver() {
        storage.clearSecrets(listOf(CAREGIVER_ACCESS, CAREGIVER_REFRESH, CAREGIVER_EXPIRES))
        storage.currentPatientId = null
        if (storage.mode == AppMode.CAREGIVER) storage.mode = null
        restore()
    }

    private fun invalidateCaregiverSession() {
        storage.clearSecrets(listOf(CAREGIVER_ACCESS, CAREGIVER_REFRESH, CAREGIVER_EXPIRES))
        storage.currentPatientId = null
        restore()
    }

    fun unlinkPatient() {
        storage.clearSecrets(listOf(PATIENT_ACCESS, PATIENT_EXPIRES))
        if (storage.mode == AppMode.PATIENT) storage.mode = null
        restore()
    }

    fun authorizationToken(): String? = when (storage.mode) {
        AppMode.CAREGIVER -> storage.getSecret(CAREGIVER_ACCESS)
        AppMode.PATIENT -> storage.getSecret(PATIENT_ACCESS)
        null -> null
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(errorMessage = null)
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(loading = true, errorMessage = null)
        try {
            block()
            restore()
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                errorMessage = error.message ?: "予期しない問題が発生しました。",
            )
        }
    }

    private fun saveCaregiver(session: AuthSession, preserveCurrentPatientId: Boolean = false) {
        storage.putSecret(CAREGIVER_ACCESS, normalizeCaregiverToken(session.accessToken))
        session.refreshToken?.let { storage.putSecret(CAREGIVER_REFRESH, it) }
        val duration = session.expiresInSeconds?.takeIf { it > 0 } ?: DEFAULT_SESSION_SECONDS
        storage.putSecret(CAREGIVER_EXPIRES, (nowEpochSeconds() + duration).toString())
        storage.mode = AppMode.CAREGIVER
        if (!preserveCurrentPatientId) storage.currentPatientId = null
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
                allowsPatientRefreshRetry = false,
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
    }
}

data class PatientSessionToken(val token: String, val expiresAtEpochSeconds: Long?) {
    companion object {
        fun fromJson(response: JSONObject): PatientSessionToken {
            val data = response.getJSONObject("data")
            val token = data.getString("patientSessionToken")
            require(token.isNotBlank()) { "患者セッショントークンがありません。" }
            val expiresAt = data.optString("expiresAt")
                .takeIf(String::isNotBlank)
                ?.let { Instant.parse(it).epochSecond }
            return PatientSessionToken(token, expiresAt)
        }
    }
}
