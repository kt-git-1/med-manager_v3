package com.afterlifearchive.medmanager.data.session

import com.afterlifearchive.medmanager.data.auth.AuthService
import com.afterlifearchive.medmanager.data.auth.AuthSession
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
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
            tokenProvider = { storage.getSecret(SessionRepository.PATIENT_ACCESS) },
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
            ApiClient(baseUrl = "https://example.invalid/", tokenProvider = { null }),
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
            ApiClient(baseUrl = "https://example.invalid/", tokenProvider = { null }),
            { 1_000 },
        )

        repository.refreshCaregiverIfNeeded()

        assertEquals(AppMode.CAREGIVER, repository.state.value.mode)
        assertFalse(repository.state.value.caregiverAuthenticated)
        assertNull(storage.getSecret(SessionRepository.CAREGIVER_REFRESH))
    }

    private fun repository(storage: FakeStorage, now: Long = 1_000): SessionRepository {
        val auth = FakeAuthService()
        return SessionRepository(
            storage,
            auth,
            ApiClient(baseUrl = "https://example.invalid/", tokenProvider = { null }),
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

private class FakeStorage : SessionStorage {
    private val secrets = mutableMapOf<String, String>()
    override var mode: AppMode? = null
    override var currentPatientId: String? = null
    override fun getSecret(key: String) = secrets[key]
    override fun putSecret(key: String, value: String?) {
        if (value == null) secrets.remove(key) else secrets[key] = value
    }
}
