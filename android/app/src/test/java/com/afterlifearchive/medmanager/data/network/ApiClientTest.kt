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
            patientTokenProvider = { token },
            forceRefreshPatient = {
                refreshCount += 1
                token = "new-token"
                true
            },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        client.get("api/patient/today", RequestAuthPolicy.PATIENT)

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
            patientTokenProvider = { "expired-token" },
            forceRefreshPatient = { false },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        val error = runCatching {
            client.get("api/patient/today", RequestAuthPolicy.PATIENT)
        }.exceptionOrNull()

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
            patientTokenProvider = { "token" },
            forceRefreshPatient = { true },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        val error = runCatching {
            client.get("api/patient/today", RequestAuthPolicy.PATIENT)
        }.exceptionOrNull()

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
            patientTokenProvider = { "token" },
            refreshPatientIfNeeded = { false },
            onPatientAuthFailure = { invalidated = true },
            transport = transport,
        )

        val error = runCatching {
            client.get("api/patient/today", RequestAuthPolicy.PATIENT)
        }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertTrue(transport.requests.isEmpty())
        assertTrue(invalidated)
    }

    @Test
    fun publicRequestNeverSendsStoredAuthorizationOrRefreshesSessions() = runTest {
        var patientRefreshCount = 0
        var caregiverRefreshCount = 0
        val transport = QueueTransport(HttpResponse(200, "{\"data\":{}}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { "stale-patient-token" },
            caregiverTokenProvider = { "caregiver-access-token" },
            refreshPatientIfNeeded = { patientRefreshCount += 1; true },
            refreshCaregiverIfNeeded = { caregiverRefreshCount += 1; true },
            transport = transport,
        )

        client.post(
            "api/patient/link",
            org.json.JSONObject().put("code", "123456"),
            RequestAuthPolicy.PUBLIC,
        )

        assertEquals(1, transport.requests.size)
        assertEquals(null, transport.requests.single().headers["Authorization"])
        assertEquals(0, patientRefreshCount)
        assertEquals(0, caregiverRefreshCount)
    }

    @Test
    fun public401DoesNotRetryOrInvalidateAnyStoredSession() = runTest {
        var patientInvalidated = false
        var caregiverInvalidated = false
        val transport = QueueTransport(HttpResponse(401, "{\"error\":\"Unauthorized\"}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { "patient-token" },
            caregiverTokenProvider = { "caregiver-token" },
            onPatientAuthFailure = { patientInvalidated = true },
            onCaregiverAuthFailure = { caregiverInvalidated = true },
            transport = transport,
        )

        val error = runCatching {
            client.post(
                "api/patient/link",
                org.json.JSONObject().put("code", "123456"),
                RequestAuthPolicy.PUBLIC,
            )
        }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertEquals(1, transport.requests.size)
        assertEquals(null, transport.requests.single().headers["Authorization"])
        assertTrue(!patientInvalidated)
        assertTrue(!caregiverInvalidated)
    }

    @Test
    fun everyPublicLinkFailureFamilyPreservesSessionsAndNeverRetries() = runTest {
        listOf(401, 403, 404, 422, 429).forEach { status ->
            var patientInvalidationCount = 0
            var caregiverInvalidationCount = 0
            val transport = QueueTransport(HttpResponse(status, "{}"))
            val client = ApiClient(
                baseUrl = "https://example.test/",
                patientTokenProvider = { "patient-token" },
                caregiverTokenProvider = { "caregiver-token" },
                onPatientAuthFailure = { patientInvalidationCount += 1 },
                onCaregiverAuthFailure = { caregiverInvalidationCount += 1 },
                transport = transport,
            )

            runCatching {
                client.post(
                    "api/patient/link",
                    org.json.JSONObject().put("code", "123456"),
                    RequestAuthPolicy.PUBLIC,
                )
            }

            assertEquals("status=$status", 1, transport.requests.size)
            assertEquals("status=$status", null, transport.requests.single().headers["Authorization"])
            assertEquals("status=$status", 0, patientInvalidationCount)
            assertEquals("status=$status", 0, caregiverInvalidationCount)
        }
    }

    @Test
    fun caregiverRequestUsesOnlyCaregiverTokenAnd401InvalidatesWithoutRetry() = runTest {
        var caregiverRefreshCount = 0
        var caregiverInvalidated = false
        val transport = QueueTransport(HttpResponse(401, "{}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            patientTokenProvider = { "patient-token" },
            caregiverTokenProvider = { "caregiver-token" },
            refreshCaregiverIfNeeded = { caregiverRefreshCount += 1; true },
            onCaregiverAuthFailure = { caregiverInvalidated = true },
            transport = transport,
        )

        val error = runCatching {
            client.get("api/patients", RequestAuthPolicy.CAREGIVER)
        }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertEquals(1, transport.requests.size)
        assertEquals("Bearer caregiver-token", transport.requests.single().headers["Authorization"])
        assertEquals(1, caregiverRefreshCount)
        assertTrue(caregiverInvalidated)
    }

    @Test
    fun caregiver403DoesNotInvalidateSession() = runTest {
        var caregiverInvalidated = false
        val transport = QueueTransport(HttpResponse(403, "{}"))
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            onCaregiverAuthFailure = { caregiverInvalidated = true },
            transport = transport,
        )

        val error = runCatching {
            client.get("api/patients", RequestAuthPolicy.CAREGIVER)
        }.exceptionOrNull()

        assertTrue(error is ApiException.Forbidden)
        assertTrue(!caregiverInvalidated)
        assertEquals(1, transport.requests.size)
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
