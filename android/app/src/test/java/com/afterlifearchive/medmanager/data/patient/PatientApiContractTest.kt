package com.afterlifearchive.medmanager.data.patient

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PatientApiContractTest {
    @Test
    fun todayParsesCompleteIosEquivalentContract() = runTest {
        val transport = ContractTransport(
            HttpResponse(200, TODAY_FIXTURE),
        )
        val api = PatientApi(ApiClient("https://example.test/", { "patient-token" }, transport = transport))

        val dose = api.today().single()

        assertEquals("dose-1", dose.key)
        assertEquals("patient-1", dose.patientId)
        assertEquals("medication-1", dose.medicationId)
        assertEquals(Instant.parse("2026-07-13T03:15:00Z"), dose.scheduledAt)
        assertEquals(DoseStatus.MISSED, dose.status)
        assertEquals(RecordedByType.CAREGIVER, dose.recordedByType)
        assertEquals("血圧のお薬", dose.medicationName)
        assertEquals("1回1.5錠", dose.dosageText)
        assertEquals(1.5, dose.doseCount, 0.0)
        assertEquals(5.0, dose.dosageStrengthValue, 0.0)
        assertEquals("mg", dose.dosageStrengthUnit)
        assertEquals("食後", dose.notes)
        assertEquals("Bearer patient-token", transport.requests.single().headers["Authorization"])
    }

    @Test
    fun todayTreatsMissingOptionalStatusRecorderAndNotesSafely() = runTest {
        val fixture = TODAY_FIXTURE
            .replace("\"effectiveStatus\":\"missed\",", "")
            .replace("\"recordedByType\":\"caregiver\",", "")
            .replace("\"notes\":\"食後\"", "\"notes\":null")
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = ContractTransport(HttpResponse(200, fixture))))

        val dose = api.today().single()

        assertEquals(DoseStatus.PENDING, dose.status)
        assertNull(dose.recordedByType)
        assertNull(dose.notes)
    }

    @Test
    fun slotTimesParseAllFourServerValues() = runTest {
        val transport = ContractTransport(HttpResponse(200, SLOT_TIMES_FIXTURE))
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = transport))

        val result = api.slotTimes()

        assertEquals(PatientSlotTimes("07:30", "12:15", "18:45", "21:30"), result)
        assertTrue(transport.requests.single().url.endsWith("/api/patient/slot-times"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun slotTimesRejectMalformedServerTime() = runTest {
        val malformed = SLOT_TIMES_FIXTURE.replace("07:30", "25:99")
        PatientApi(ApiClient("https://example.test/", { null }, transport = ContractTransport(HttpResponse(200, malformed)))).slotTimes()
    }

    @Test
    fun medicationsParseInventoryPrnAndRegimenContract() = runTest {
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = ContractTransport(HttpResponse(200, MEDICATIONS_FIXTURE))))

        val medication = api.medications().single()

        assertEquals("medication-1", medication.id)
        assertTrue(medication.isPrn)
        assertEquals("痛い時", medication.prnInstructions)
        assertEquals(0.5, medication.inventoryQuantity, 0.0)
        assertTrue(medication.isInsufficientForDose)
        assertTrue(medication.isOutOfStock)
        assertEquals(listOf("07:30", "18:45"), medication.regimenTimes)
        assertEquals(listOf("MON", "WED"), medication.regimenDaysOfWeek)
    }

    @Test
    fun bulkRecordParsesPartialSuccessContractAndRequest() = runTest {
        val transport = ContractTransport(HttpResponse(200, BULK_FIXTURE))
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = transport))

        val result = api.recordSlot("2026-07-13", MedicationSlot.NOON)

        assertEquals(2, result.updatedCount)
        assertEquals(1, result.remainingCount)
        assertEquals(1, result.insufficientCount)
        assertEquals(3.5, result.totalPills, 0.0)
        assertEquals(HistoryStatus.PENDING, result.slotSummary[MedicationSlot.NOON])
        assertEquals("group-1", result.recordingGroupId)
        assertTrue(transport.requests.single().body.orEmpty().contains("\"slot\":\"noon\""))
        assertTrue(transport.requests.single().body.orEmpty().contains("\"date\":\"2026-07-13\""))
    }

    @Test
    fun prnRecordUsesPatientScopedRouteAndNullableDefaults() = runTest {
        val transport = ContractTransport(HttpResponse(200, """{"record":{"id":"prn-1"}}"""))
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = transport))
        val medication = api.medicationsFromFixtureForTest()

        api.recordPrn(medication)

        val request = transport.requests.single()
        assertTrue(request.url.endsWith("/api/patients/patient-1/prn-dose-records"))
        assertTrue(request.body.orEmpty().contains("\"medicationId\":\"medication-1\""))
        assertTrue(request.body.orEmpty().contains("\"takenAt\":null"))
    }

    @Test
    fun monthHistoryParsesFourSlotSummaryAndPrnCounts() = runTest {
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = ContractTransport(HttpResponse(200, HISTORY_MONTH_FIXTURE))))

        val day = api.history(2026, 7).single()

        assertEquals("2026-07-13", day.date)
        assertEquals(HistoryStatus.TAKEN, day.morning)
        assertEquals(HistoryStatus.MISSED, day.noon)
        assertEquals(HistoryStatus.PENDING, day.evening)
        assertEquals(HistoryStatus.NONE, day.bedtime)
        assertEquals(2, day.prnCount)
    }

    @Test
    fun dayHistoryParsesScheduledRecorderAndPrnActor() = runTest {
        val transport = ContractTransport(HttpResponse(200, HISTORY_DAY_FIXTURE))
        val api = PatientApi(ApiClient("https://example.test/", { null }, transport = transport))

        val detail = api.historyDay("2026-07-13")

        assertEquals("2026-07-13", detail.date)
        assertEquals(RecordedByType.CAREGIVER, detail.doses.single().recordedByType)
        assertEquals(MedicationSlot.NOON, detail.doses.single().slot)
        assertEquals(DoseStatus.TAKEN, detail.doses.single().status)
        assertEquals(PrnActorType.PATIENT, detail.prnItems.single().actorType)
        assertEquals(1.5, detail.prnItems.single().quantityTaken, 0.0)
        assertTrue(transport.requests.single().url.endsWith("/api/patient/history/day?date=2026-07-13"))
    }

    private companion object {
        const val TODAY_FIXTURE = """{"data":[{"key":"dose-1","patientId":"patient-1","medicationId":"medication-1","scheduledAt":"2026-07-13T03:15:00Z","effectiveStatus":"missed","recordedByType":"caregiver","medicationSnapshot":{"name":"血圧のお薬","dosageText":"1回1.5錠","doseCountPerIntake":1.5,"dosageStrengthValue":5.0,"dosageStrengthUnit":"mg","notes":"食後"}}]}"""
        const val SLOT_TIMES_FIXTURE = """{"data":{"slotTimes":{"morning":"07:30","noon":"12:15","evening":"18:45","bedtime":"21:30"}}}"""
        const val MEDICATIONS_FIXTURE = """{"data":[{"id":"medication-1","patientId":"patient-1","name":"頓服薬","dosageText":"1回1錠","doseCountPerIntake":1.0,"dosageStrengthValue":10.0,"dosageStrengthUnit":"mg","notes":null,"isPrn":true,"prnInstructions":"痛い時","startDate":"2026-07-01T00:00:00Z","endDate":null,"inventoryCount":0.5,"inventoryUnit":"錠","inventoryEnabled":true,"inventoryQuantity":0.5,"inventoryOut":true,"isActive":true,"isArchived":false,"nextScheduledAt":null,"regimenTimes":["07:30","18:45"],"regimenDaysOfWeek":["MON","WED"]}]}"""
        const val BULK_FIXTURE = """{"updatedCount":2,"remainingCount":1,"insufficientCount":1,"totalPills":3.5,"medCount":3,"slotTime":"12:15","slotSummary":{"morning":"taken","noon":"pending","evening":"none","bedtime":"none"},"recordingGroupId":"group-1"}"""
        const val HISTORY_MONTH_FIXTURE = """{"year":2026,"month":7,"days":[{"date":"2026-07-13","slotSummary":{"morning":"taken","noon":"missed","evening":"pending","bedtime":"none"}}],"prnCountByDay":{"2026-07-13":2}}"""
        const val HISTORY_DAY_FIXTURE = """{"date":"2026-07-13","doses":[{"medicationId":"med-1","medicationName":"血圧薬","dosageText":"1錠","doseCountPerIntake":1.0,"scheduledAt":"2026-07-13T03:15:00Z","slot":"noon","effectiveStatus":"taken","recordedByType":"caregiver"}],"prnItems":[{"medicationId":"prn-1","medicationName":"頭痛薬","takenAt":"2026-07-13T05:00:00Z","quantityTaken":1.5,"actorType":"patient"}]}"""
    }
}

private fun PatientApi.medicationsFromFixtureForTest() = PatientMedication(
    id = "medication-1", patientId = "patient-1", name = "頓服薬", dosageText = "1錠",
    doseCountPerIntake = 1.0, dosageStrengthValue = 1.0, dosageStrengthUnit = "mg", notes = null,
    isPrn = true, prnInstructions = "痛い時", startDate = Instant.EPOCH, endDate = null,
    inventoryCount = 10.0, inventoryUnit = "錠", inventoryEnabled = true, inventoryQuantity = 10.0,
    inventoryOut = false, isActive = true, isArchived = false, nextScheduledAt = null,
    regimenTimes = null, regimenDaysOfWeek = null,
)

private class ContractTransport(vararg responses: HttpResponse) : HttpTransport {
    private val queued = ArrayDeque(responses.toList())
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return queued.removeFirst()
    }
}
