package com.afterlifearchive.medmanager.data.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterlifearchive.medmanager.data.auth.AuthService
import com.afterlifearchive.medmanager.data.auth.AuthSession
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.ui.AppMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSessionRestorationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearBefore() = clearPreferences()

    @After
    fun clearAfter() = clearPreferences()

    @Test
    fun newStorageInstanceRestoresModeSelectionAndEncryptedTokens() {
        AndroidSessionStorage(context).apply {
            mode = AppMode.CAREGIVER
            currentPatientId = "patient-42"
            putSecret(SessionRepository.CAREGIVER_ACCESS, "caregiver-sensitive-token")
            putSecret(SessionRepository.CAREGIVER_REFRESH, "refresh-sensitive-token")
            putSecret(SessionRepository.CAREGIVER_EXPIRES, "9999999999")
        }

        val rawStoredValue = context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
            .getString(SessionRepository.CAREGIVER_ACCESS, null)
        val restored = AndroidSessionStorage(context)

        assertNotEquals("caregiver-sensitive-token", rawStoredValue)
        assertFalse(rawStoredValue.orEmpty().contains("sensitive-token"))
        assertEquals(AppMode.CAREGIVER, restored.mode)
        assertEquals("patient-42", restored.currentPatientId)
        assertEquals("caregiver-sensitive-token", restored.getSecret(SessionRepository.CAREGIVER_ACCESS))
        assertEquals("refresh-sensitive-token", restored.getSecret(SessionRepository.CAREGIVER_REFRESH))
    }

    @Test
    fun newRepositoryRestoresValidPatientSessionAfterObjectRecreation() {
        AndroidSessionStorage(context).apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "patient-token")
            putSecret(SessionRepository.PATIENT_EXPIRES, "2000")
        }

        val repository = newRepository(now = 1000)
        repository.restore()

        assertEquals(AppMode.PATIENT, repository.state.value.mode)
        assertTrue(repository.state.value.patientAuthenticated)
        assertEquals("patient-token", repository.authorizationToken())
    }

    @Test
    fun newRepositoryDropsExpiredPatientSessionButKeepsPatientMode() {
        AndroidSessionStorage(context).apply {
            mode = AppMode.PATIENT
            putSecret(SessionRepository.PATIENT_ACCESS, "expired-patient-token")
            putSecret(SessionRepository.PATIENT_EXPIRES, "999")
        }

        val repository = newRepository(now = 1000)
        repository.restore()

        assertEquals(AppMode.PATIENT, repository.state.value.mode)
        assertFalse(repository.state.value.patientAuthenticated)
        assertNull(repository.authorizationToken())
    }

    private fun newRepository(now: Long): SessionRepository {
        val storage = AndroidSessionStorage(context)
        val auth = object : AuthService {
            override suspend fun login(email: String, password: String) = error("unused")
            override suspend fun refresh(refreshToken: String): AuthSession = error("unused")
        }
        return SessionRepository(
            storage = storage,
            authService = auth,
            apiClient = ApiClient(baseUrl = "https://example.invalid/", tokenProvider = { null }),
            nowEpochSeconds = { now },
        )
    }

    private fun clearPreferences() {
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private companion object {
        const val SESSION_PREFS = "session_preferences"
        const val SECURE_PREFS = "secure_session"
    }
}
