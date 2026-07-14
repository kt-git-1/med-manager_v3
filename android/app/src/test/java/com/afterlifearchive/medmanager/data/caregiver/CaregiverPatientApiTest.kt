package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaregiverPatientApiTest {
    @Test
    fun listPatientsUsesCaregiverAuthAndMapsTypedResponse() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(
                    200,
                    """{"data":[{"id":"p1","displayName":"さくら","slotTimes":{"morning":"08:00","noon":"12:00","evening":"18:00","bedtime":"21:00"}}]}""",
                )
            },
        )

        val patients = CaregiverPatientApi(client).listPatients()

        assertEquals("Bearer caregiver-token", captured?.headers?.get("Authorization"))
        assertEquals("https://example.test/api/patients", captured?.url)
        assertEquals("p1", patients.single().id)
        assertEquals("08:00", patients.single().slotTimes?.morning)
    }

    @Test
    fun missingOptionalSlotTimesIsAccepted() = runTest {
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport {
                HttpResponse(200, """{"data":[{"id":"p1","displayName":"さくら"}]}""")
            },
        )

        assertNull(CaregiverPatientApi(client).listPatients().single().slotTimes)
    }

    @Test
    fun createPatientPostsNormalizedContractBody() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(201, """{"data":{"id":"p2","displayName":"さくら"}}""")
            },
        )

        val created = CaregiverPatientApi(client).createPatient("さくら")

        assertEquals("POST", captured?.method)
        assertEquals("{\"displayName\":\"さくら\"}", captured?.body)
        assertEquals("p2", created.id)
    }
}
