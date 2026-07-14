package com.afterlifearchive.medmanager.data.session

import com.afterlifearchive.medmanager.data.auth.AuthService
import com.afterlifearchive.medmanager.data.auth.AuthException
import com.afterlifearchive.medmanager.data.auth.AuthFailure
import com.afterlifearchive.medmanager.data.auth.AuthSession
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.ui.AppMode
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun caregiverLoginNormalizesAndRestoresSession() = runTest {
        val storage = FakeStorage()
        val repository = repository(storage, now = 1_000)

        repository.loginCaregiver("care@example.com", "password")

        assertEquals(AppMode.CAREGIVER, repository.state.value.mode)
        assertTrue(repository.state.value.caregiverAuthenticated)
        assertEquals("caregiver-access-token", storage.getSecret(SessionRepository.CAREGIVER_ACCESS))
        assertEquals("refresh-token", storage.getSecret(SessionRepository.CAREGIVER_REFRESH))
        assertEquals("4600", storage.getSecret(SessionRepository.CAREGIVER_EXPIRES))
    }

    @Test
    fun expiredPatientSessionIsRemovedOnRestore() {
        val storage = FakeStorage().apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "patient-token")
            putSecret(SessionRepository.PATIENT_EXPIRES, "999")
        }
        val repository = repository(storage, now = 1_000)

        repository.restore()

        assertFalse(repository.state.value.patientAuthenticated)
        assertNull(storage.getSecret(SessionRepository.PATIENT_ACCESS))
    }

    @Test
    fun selectingModePersistsAcrossRepositoryCreation() {
        val storage = FakeStorage()
        repository(storage).selectMode(AppMode.PATIENT)

        val restored = repository(storage)
        restored.restore()

        assertEquals(AppMode.PATIENT, restored.state.value.mode)
    }

    @Test
    fun expiringPatientSessionIsRefreshedAndPersisted() = runTest {
        val storage = FakeStorage().apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "patient-old")
            putSecret(SessionRepository.PATIENT_EXPIRES, "1001")
        }
        val transport = HttpTransport {
            HttpResponse(
                200,
                """{"data":{"patientSessionToken":"patient-new","expiresAt":"2026-08-12T00:00:00Z"}}""",
            )
        }
        val auth = FakeAuthService()
        val apiClient = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { storage.getSecret(SessionRepository.PATIENT_ACCESS) },
            transport = transport,
        )
        val repository = SessionRepository(storage, auth, apiClient, { 1_000 })

        assertTrue(repository.refreshPatientIfNeeded())
        assertEquals("patient-new", storage.getSecret(SessionRepository.PATIENT_ACCESS))
        assertEquals("1786492800", storage.getSecret(SessionRepository.PATIENT_EXPIRES))
    }

    @Test
    fun invalidatingPatientSessionKeepsModeAndReturnsToLinkState() {
        val storage = FakeStorage().apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "patient-token")
            putSecret(SessionRepository.PATIENT_EXPIRES, "2000")
        }
        val repository = repository(storage)

        repository.invalidatePatientSession()

        assertEquals(AppMode.PATIENT, repository.state.value.mode)
        assertFalse(repository.state.value.patientAuthenticated)
        assertNull(storage.getSecret(SessionRepository.PATIENT_ACCESS))
    }

    @Test
    fun concurrentCaregiverRefreshIsCoalescedAndKeepsPatientSelection() = runTest {
        val storage = FakeStorage().apply {
            mode = AppMode.CAREGIVER
            currentPatientId = "patient-1"
            putSecret(SessionRepository.CAREGIVER_ACCESS, "caregiver-old")
            putSecret(SessionRepository.CAREGIVER_REFRESH, "refresh-old")
            putSecret(SessionRepository.CAREGIVER_EXPIRES, "1001")
        }
        val auth = CountingAuthService()
        val repository = SessionRepository(
            storage,
            auth,
            ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }),
            { 1_000 },
        )

        val first = async { repository.refreshCaregiverIfNeeded() }
        val second = async { repository.refreshCaregiverIfNeeded() }
        first.await()
        second.await()

        assertEquals(1, auth.refreshCount)
        assertEquals("caregiver-new-access", storage.getSecret(SessionRepository.CAREGIVER_ACCESS))
        assertEquals("patient-1", storage.currentPatientId)
    }

    @Test
    fun caregiverRefreshFailureKeepsModeAndClearsCredentials() = runTest {
        val storage = FakeStorage().apply {
            mode = AppMode.CAREGIVER
            putSecret(SessionRepository.CAREGIVER_ACCESS, "caregiver-old")
            putSecret(SessionRepository.CAREGIVER_REFRESH, "refresh-old")
            putSecret(SessionRepository.CAREGIVER_EXPIRES, "1001")
        }
        val auth = object : AuthService {
            override suspend fun login(email: String, password: String) = error("unused")
            override suspend fun refresh(refreshToken: String): AuthSession = error("refresh failed")
        }
        val repository = SessionRepository(
            storage,
            auth,
            ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }),
            { 1_000 },
        )

        repository.refreshCaregiverIfNeeded()

        assertEquals(AppMode.CAREGIVER, repository.state.value.mode)
        assertFalse(repository.state.value.caregiverAuthenticated)
        assertNull(storage.getSecret(SessionRepository.CAREGIVER_REFRESH))
    }

    @Test
    fun invalidLinkCodeUsesCanonicalJapaneseMessageWithoutNetwork() = runTest {
        val storage = FakeStorage().apply { mode = AppMode.PATIENT }
        var requestCount = 0
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { null },
            transport = HttpTransport { requestCount += 1; HttpResponse(200, "{}") },
        )
        val repository = SessionRepository(storage, FakeAuthService(), client, { 1_000 })

        repository.linkPatient("12-3")

        assertEquals(0, requestCount)
        assertEquals(PatientLinkFailure.INVALID, repository.state.value.patientLinkFailure)
        assertNull(repository.state.value.errorMessage)
    }

    @Test
    fun missingLinkCodeUsesCanonicalExpiredMessage() = runTest {
        val storage = FakeStorage().apply { mode = AppMode.PATIENT }
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { null },
            transport = HttpTransport { HttpResponse(404, "{\"error\":\"not_found\"}") },
        )
        val repository = SessionRepository(storage, FakeAuthService(), client, { 1_000 })

        repository.linkPatient("123456")

        assertEquals(PatientLinkFailure.NOT_FOUND, repository.state.value.patientLinkFailure)
        assertNull(repository.state.value.errorMessage)
        assertFalse(repository.state.value.patientAuthenticated)
    }

    @Test
    fun failedPublicLinkExchangeDoesNotSendOrDeleteExistingSessions() = runTest {
        val storage = FakeStorage().apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "existing-patient-token")
            putSecret(SessionRepository.PATIENT_EXPIRES, "9999")
            putSecret(SessionRepository.CAREGIVER_ACCESS, "caregiver-access-token")
            putSecret(SessionRepository.CAREGIVER_REFRESH, "caregiver-refresh-token")
            putSecret(SessionRepository.CAREGIVER_EXPIRES, "9999")
        }
        var request: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { storage.getSecret(SessionRepository.PATIENT_ACCESS) },
            caregiverTokenProvider = { storage.getSecret(SessionRepository.CAREGIVER_ACCESS) },
            transport = HttpTransport {
                request = it
                HttpResponse(401, "{\"error\":\"Unauthorized\"}")
            },
        )
        val repository = SessionRepository(storage, FakeAuthService(), client, { 1_000 })
        repository.restore()

        repository.linkPatient("123456")

        assertNull(request?.headers?.get("Authorization"))
        assertEquals("existing-patient-token", storage.getSecret(SessionRepository.PATIENT_ACCESS))
        assertEquals("caregiver-access-token", storage.getSecret(SessionRepository.CAREGIVER_ACCESS))
        assertTrue(repository.state.value.patientAuthenticated)
        assertEquals(PatientLinkFailure.AUTHORIZATION, repository.state.value.patientLinkFailure)
    }

    @Test
    fun linkFailuresMapToPinnedIosCategoriesWithoutRawServerMessages() {
        val cases = mapOf(
            ApiException.Validation("raw validation") to PatientLinkFailure.INVALID,
            ApiException.NotFound() to PatientLinkFailure.NOT_FOUND,
            ApiException.Conflict("raw conflict") to PatientLinkFailure.NOT_FOUND,
            ApiException.Unauthorized() to PatientLinkFailure.AUTHORIZATION,
            ApiException.Forbidden() to PatientLinkFailure.AUTHORIZATION,
            ApiException.Network("raw server response") to PatientLinkFailure.NETWORK,
            ApiException.RateLimited() to PatientLinkFailure.GENERIC,
            ApiException.Server() to PatientLinkFailure.GENERIC,
            IllegalStateException("raw unexpected error") to PatientLinkFailure.GENERIC,
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.toPatientLinkFailure())
        }
    }

    @Test
    fun successfulLinkPersistsSessionAndClearsLoadingState() = runTest {
        val storage = FakeStorage().apply { mode = AppMode.PATIENT }
        var sentBody: String? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { null },
            transport = HttpTransport { request: HttpRequest ->
                sentBody = request.body
                HttpResponse(200, "{\"data\":{\"patientSessionToken\":\"patient-linked\",\"expiresAt\":\"2026-08-12T00:00:00Z\"}}")
            },
        )
        val repository = SessionRepository(storage, FakeAuthService(), client, { 1_000 })

        repository.linkPatient("12-34-56")

        assertTrue(sentBody.orEmpty().contains("123456"))
        assertTrue(repository.state.value.patientAuthenticated)
        assertFalse(repository.state.value.loading)
        assertNull(repository.state.value.errorMessage)
    }

    @Test
    fun signupValidatesEmailPasswordAndConfirmationBeforeNetwork() = runTest {
        val auth = SignupAuthService(AuthSession(null, null, null))
        val repository = SessionRepository(
            FakeStorage().apply { mode = AppMode.CAREGIVER }, auth,
            ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }), { 1_000 },
        )

        repository.signupCaregiver("invalid", "123", "456")
        assertEquals(SessionUserMessage.InvalidEmail, repository.state.value.errorMessage)
        assertEquals(0, auth.signupCount)

        repository.signupCaregiver("care@example.com", "123", "123")
        assertEquals(SessionUserMessage.PasswordTooShort, repository.state.value.errorMessage)

        repository.signupCaregiver("care@example.com", "123456", "654321")
        assertEquals(SessionUserMessage.PasswordMismatch, repository.state.value.errorMessage)
        assertEquals(0, auth.signupCount)
    }

    @Test
    fun signupWithoutAccessTokenShowsConfirmationAndAllowsResend() = runTest {
        val auth = SignupAuthService(AuthSession(null, null, null))
        val repository = SessionRepository(
            FakeStorage().apply { mode = AppMode.CAREGIVER }, auth,
            ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }), { 1_000 },
        )

        repository.signupCaregiver(" care@example.com ", "123456", "123456")

        assertTrue(repository.state.value.canResendConfirmation)
        assertEquals(SessionUserMessage.ConfirmationSent, repository.state.value.infoMessage)
        assertEquals("care@example.com", auth.signupEmail)

        repository.resendSignupConfirmation("care@example.com")
        assertEquals(1, auth.resendCount)
        assertEquals(SessionUserMessage.ConfirmationResent, repository.state.value.infoMessage)
    }

    @Test
    fun signupPreservesIosSpecificAlreadyRegisteredAndConfirmationEmailFailures() = runTest {
        val cases = mapOf(
            AuthFailure.EMAIL_ALREADY_REGISTERED to SessionUserMessage.EmailAlreadyRegistered,
            AuthFailure.CONFIRMATION_EMAIL_FAILED to SessionUserMessage.ConfirmationEmailFailed,
        )

        cases.forEach { (failure, expected) ->
            val auth = SignupAuthService(
                signupSession = AuthSession(null, null, null),
                signupError = AuthException(failure),
            )
            val repository = SessionRepository(
                FakeStorage().apply { mode = AppMode.CAREGIVER }, auth,
                ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }), { 1_000 },
            )

            repository.signupCaregiver("care@example.com", "123456", "123456")

            assertEquals(expected, repository.state.value.errorMessage)
            assertFalse(repository.state.value.loading)
        }
    }

    @Test
    fun resendUsesConfirmationSpecificRateLimitAndFailureStates() = runTest {
        val cases = mapOf(
            AuthFailure.RATE_LIMITED to SessionUserMessage.ConfirmationResendRateLimited,
            AuthFailure.LOGIN_FAILED to SessionUserMessage.ConfirmationResendFailed,
        )

        cases.forEach { (failure, expected) ->
            val auth = SignupAuthService(
                signupSession = AuthSession(null, null, null),
                resendError = AuthException(failure),
            )
            val repository = SessionRepository(
                FakeStorage().apply { mode = AppMode.CAREGIVER }, auth,
                ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }), { 1_000 },
            )

            repository.resendSignupConfirmation("care@example.com")

            assertEquals(expected, repository.state.value.errorMessage)
        }
    }

    @Test
    fun leavingAuthFlowClearsTransientConfirmationState() = runTest {
        val auth = SignupAuthService(AuthSession(null, null, null))
        val repository = SessionRepository(
            FakeStorage().apply { mode = AppMode.CAREGIVER }, auth,
            ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }), { 1_000 },
        )
        repository.signupCaregiver("care@example.com", "123456", "123456")
        assertTrue(repository.state.value.canResendConfirmation)

        repository.clearAuthFlowState()

        assertNull(repository.state.value.infoMessage)
        assertNull(repository.state.value.errorMessage)
        assertFalse(repository.state.value.canResendConfirmation)
    }

    @Test
    fun authCallbacksClearStaleCredentialsAndRequestCaregiverLogin() {
        val acceptedUrls = listOf(
            "okusurimimamori://auth/login",
            "https://okusuri-mimamori.com/auth/confirmed",
            "https://www.okusuri-mimamori.com/auth/login",
        )

        acceptedUrls.forEach { url ->
            val storage = FakeStorage().apply {
                mode = AppMode.CAREGIVER
                currentPatientId = "patient-1"
                putSecret(SessionRepository.CAREGIVER_ACCESS, "stale-access")
                putSecret(SessionRepository.CAREGIVER_REFRESH, "stale-refresh")
                putSecret(SessionRepository.CAREGIVER_EXPIRES, "9999")
            }
            val repository = repository(storage)

            assertTrue(repository.handleAuthCallback(url))
            assertEquals(AppMode.CAREGIVER, repository.state.value.mode)
            assertFalse(repository.state.value.caregiverAuthenticated)
            assertTrue(repository.state.value.caregiverLoginRequested)
            assertNull(storage.getSecret(SessionRepository.CAREGIVER_ACCESS))
            assertNull(storage.currentPatientId)

            repository.consumeCaregiverLoginRequest()
            assertFalse(repository.state.value.caregiverLoginRequested)
        }
    }

    @Test
    fun unrelatedAuthCallbackIsRejectedWithoutChangingSession() {
        val storage = FakeStorage().apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "patient-token")
        }
        val repository = repository(storage)

        assertFalse(repository.handleAuthCallback("https://example.com/auth/confirmed"))
        assertEquals(AppMode.PATIENT, storage.mode)
        assertEquals("patient-token", storage.getSecret(SessionRepository.PATIENT_ACCESS))
        assertFalse(repository.state.value.caregiverLoginRequested)
    }

    private fun repository(storage: FakeStorage, now: Long = 1_000): SessionRepository {
        val auth = FakeAuthService()
        return SessionRepository(
            storage,
            auth,
            ApiClient(baseUrl = "https://example.invalid/", patientTokenProvider = { null }),
            { now },
        )
    }
}

private class FakeAuthService : AuthService {
    override suspend fun login(email: String, password: String) =
        AuthSession("access-token", "refresh-token", 3_600)

    override suspend fun refresh(refreshToken: String) =
        AuthSession("new-access-token", "new-refresh-token", 3_600)
}

private class CountingAuthService : AuthService {
    var refreshCount = 0
    override suspend fun login(email: String, password: String) = error("unused")
    override suspend fun refresh(refreshToken: String): AuthSession {
        refreshCount += 1
        delay(10)
        return AuthSession("new-access", "new-refresh", 3_600)
    }
}

private class SignupAuthService(
    private val signupSession: AuthSession,
    private val signupError: Exception? = null,
    private val resendError: Exception? = null,
) : AuthService {
    var signupCount = 0
    var signupEmail: String? = null
    var resendCount = 0
    override suspend fun login(email: String, password: String) = error("unused")
    override suspend fun refresh(refreshToken: String) = error("unused")
    override suspend fun signup(email: String, password: String): AuthSession {
        signupCount += 1
        signupEmail = email
        signupError?.let { throw it }
        return signupSession
    }
    override suspend fun resendSignupConfirmation(email: String) {
        resendCount += 1
        resendError?.let { throw it }
    }
}

private class FakeStorage : SessionStorage {
    private val secrets = mutableMapOf<String, String>()
    override var mode: AppMode? = null
    override var currentPatientId: String? = null
    override fun getSecret(key: String) = secrets[key]
    override fun putSecret(key: String, value: String?) {
        if (value == null) secrets.remove(key) else secrets[key] = value
    }
}
