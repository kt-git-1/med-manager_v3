package com.afterlifearchive.medmanager.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {
    @Test
    fun patient401RefreshesAndRetriesExactlyOnceWithNewToken() = runTest {
        var token = "old-token"
        var refreshCount = 0
        var invalidated = false
        val transport = QueueTransport(
            HttpResponse(401, "{}"),
            HttpResponse(200, "{\"data\":[]}"),
        )
        val client = ApiClient(
            baseUrl = "https://example.test/",
            tokenProvider = { token },
            isPatientSession = { true },
            forceRefreshPatient = {
                refreshCount += 1
                token = "new-token"
                true
            },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        client.get("api/patient/today")

        assertEquals(2, transport.requests.size)
        assertEquals("Bearer old-token", transport.requests[0].headers["Authorization"])
        assertEquals("Bearer new-token", transport.requests[1].headers["Authorization"])
        assertEquals(1, refreshCount)
        assertTrue(!invalidated)
    }

    @Test
    fun failedPatientRefreshInvalidatesWithoutRetryingRequest() = runTest {
        var invalidated = false
        val transport = QueueTransport(HttpResponse(401, "{}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            tokenProvider = { "expired-token" },
            isPatientSession = { true },
            forceRefreshPatient = { false },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        val error = runCatching { client.get("api/patient/today") }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertEquals(1, transport.requests.size)
        assertTrue(invalidated)
    }

    @Test
    fun second401InvalidatesAndIsNotRetriedAgain() = runTest {
        var invalidated = false
        val transport = QueueTransport(HttpResponse(401, "{}"), HttpResponse(401, "{}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            tokenProvider = { "token" },
            isPatientSession = { true },
            forceRefreshPatient = { true },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        val error = runCatching { client.get("api/patient/today") }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertEquals(2, transport.requests.size)
        assertTrue(invalidated)
    }

    @Test
    fun proactiveRefreshFailureInvalidatesBeforeNetworkRequest() = runTest {
        var invalidated = false
        val transport = QueueTransport(HttpResponse(200, "{}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            tokenProvider = { "token" },
            isPatientSession = { true },
            refreshPatientIfNeeded = { false },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        val error = runCatching { client.get("api/patient/today") }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertTrue(transport.requests.isEmpty())
        assertTrue(invalidated)
    }
}

private class QueueTransport(vararg responses: HttpResponse) : HttpTransport {
    private val responses = ArrayDeque(responses.toList())
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return responses.removeFirst()
    }
}
