package com.afterlifearchive.medmanager.data.push

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverPushRepositoryTest {
    @Test
    fun apiUsesExactAndroidRegisterAndUnregisterContracts() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val api = CaregiverPushApi(
            ApiClient(
                baseUrl = "https://example.test/",
                caregiverTokenProvider = { "caregiver-token" },
                transport = HttpTransport { request ->
                    requests += request
                    HttpResponse(200, """{"data":{"ok":true}}""")
                },
            ),
            environment = "DEV",
        )

        api.register("fcm-token")
        api.unregister("fcm-token")

        assertEquals(listOf("https://example.test/api/push/register", "https://example.test/api/push/unregister"), requests.map { it.url })
        assertEquals(listOf("POST", "POST"), requests.map { it.method })
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer caregiver-token" })
        assertEquals("android", JSONObject(requests[0].body!!).getString("platform"))
        assertEquals("DEV", JSONObject(requests[0].body!!).getString("environment"))
        assertEquals("fcm-token", JSONObject(requests[1].body!!).getString("token"))
    }

    @Test
    fun enableRegistersTokenAndTokenRefreshReplacesRegistration() = runTest {
        val source = FakeDataSource()
        val tokens = FakeTokenSource(tokenValue = "token-1")
        val storage = FakeStorage()
        val repository = CaregiverPushRepository(source, tokens, storage)

        assertTrue(repository.enable())
        assertTrue(storage.enabled)
        assertTrue(tokens.autoInit)
        assertEquals(listOf("token-1"), source.registered)
        assertTrue(repository.state.value.registered)

        assertTrue(repository.onNewToken("token-2"))
        assertEquals(listOf("token-1", "token-2"), source.registered)
        assertEquals("token-2", storage.registeredToken)
    }

    @Test
    fun disableStopsLocallyAndPreservesFailedUnregisterForRetry() = runTest {
        val source = FakeDataSource()
        val tokens = FakeTokenSource(tokenValue = "token-1")
        val storage = FakeStorage()
        val repository = CaregiverPushRepository(source, tokens, storage)
        repository.enable()
        source.failUnregister = true

        assertFalse(repository.disable())
        assertFalse(repository.acceptsMessages())
        assertFalse(tokens.autoInit)
        assertEquals("token-1", storage.pendingUnregisterToken)
        assertTrue(repository.state.value.syncFailed)

        source.failUnregister = false
        assertTrue(repository.retry())
        assertNull(storage.pendingUnregisterToken)
        assertNull(storage.registeredToken)
        assertFalse(repository.state.value.syncFailed)
    }

    @Test
    fun missingConfigurationAndDisabledTokenRefreshNeverContactServer() = runTest {
        val source = FakeDataSource()
        val tokens = FakeTokenSource(configured = false)
        val storage = FakeStorage()
        val repository = CaregiverPushRepository(source, tokens, storage)

        assertFalse(repository.enable())
        assertTrue(repository.state.value.configurationMissing)
        repository.disable()
        assertFalse(repository.onNewToken("late-token"))
        assertTrue(source.registered.isEmpty())
    }

    private class FakeDataSource : CaregiverPushDataSource {
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()
        var failUnregister = false
        override suspend fun register(token: String) { registered += token }
        override suspend fun unregister(token: String) {
            if (failUnregister) error("offline")
            unregistered += token
        }
    }

    private class FakeTokenSource(
        override val configured: Boolean = true,
        var tokenValue: String = "token",
    ) : CaregiverPushTokenSource {
        var autoInit = false
        override fun setAutoInitEnabled(enabled: Boolean) { autoInit = enabled }
        override suspend fun token() = tokenValue
    }

    private class FakeStorage : CaregiverPushStorage {
        override var enabled = false
        override var token: String? = null
        override var registeredToken: String? = null
        override var pendingUnregisterToken: String? = null
    }
}
