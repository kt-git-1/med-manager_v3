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

    @Test
    fun updateSlotTimesPatchesAllFourCanonicalValues() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(200, """{"data":{"slotTimes":{"morning":"07:30","noon":"12:15","evening":"18:45","bedtime":"22:00"}}}""")
            },
        )
        val times = CaregiverSlotTimes("07:30", "12:15", "18:45", "22:00")

        val saved = CaregiverPatientApi(client).updateSlotTimes("p1", times)

        assertEquals("PATCH", captured?.method)
        assertEquals("https://example.test/api/patients/p1", captured?.url)
        assertEquals("{\"slotTimes\":{\"morning\":\"07:30\",\"noon\":\"12:15\",\"evening\":\"18:45\",\"bedtime\":\"22:00\"}}", captured?.body)
        assertEquals(times, saved)
    }

    @Test
    fun issueLinkingCodePostsWithoutPayloadAndMapsExpiry() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(201, """{"data":{"code":"123456","expiresAt":"2026-07-14T12:15:00.000Z"}}""")
            },
        )

        val issued = CaregiverPatientApi(client).issueLinkingCode("p1")

        assertEquals("POST", captured?.method)
        assertNull(captured?.body)
        assertEquals("123456", issued.code)
        assertEquals("2026-07-14T12:15:00.000Z", issued.expiresAt)
    }
}
