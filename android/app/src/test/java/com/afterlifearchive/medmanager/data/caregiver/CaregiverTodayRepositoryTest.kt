package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverTodayRepositoryTest {
    @Test
    fun apiLoadsAllThreeCurrentIosContractsWithCaregiverAuth() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                requests += request
                when {
                    request.url.endsWith("/today") -> HttpResponse(200, todayJson())
                    request.url.contains("/medications?") -> HttpResponse(200, medicationsJson())
                    request.url.endsWith("/inventory") -> HttpResponse(200, inventoryJson())
                    else -> error(request.url)
                }
            },
        )
        val api = CaregiverTodayApi(client)

        assertEquals("dose-1", api.today("patient-1").single().key)
        assertEquals("頓服", api.medications("patient-1").single().name)
        assertTrue(api.inventory("patient-1").single().insufficientForDose)
        assertEquals(
            setOf(
                "https://example.test/api/patients/patient-1/today",
                "https://example.test/api/medications?patientId=patient-1",
                "https://example.test/api/patients/patient-1/inventory",
            ),
            requests.map { it.url }.toSet(),
        )
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer caregiver-token" })
    }

    @Test
    fun loadAggregatesSortedDosesActivePrnAndInventoryFlags() = runTest {
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(
                dose("taken", DoseStatus.TAKEN, "2026-07-15T08:00:00Z"),
                dose("missed", DoseStatus.MISSED, "2026-07-15T02:00:00Z"),
                dose("pending", DoseStatus.PENDING, "2026-07-15T04:00:00Z"),
            )
            override suspend fun medications(patientId: String) = listOf(
                medication("z", "Z頓服", isPrn = true, active = true),
                medication("a", "A頓服", isPrn = true, active = true),
                medication("regular", "定時", isPrn = false, active = true),
                medication("archived", "終了", isPrn = true, active = false),
            )
            override suspend fun inventory(patientId: String) = listOf(
                CaregiverInventorySummary("pending", true, 0.5, 1.0, low = true, out = false),
            )
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())

        repository.load("patient-1")

        assertEquals(listOf("pending", "missed", "taken"), repository.state.value.doses.map { it.key })
        assertEquals(listOf("A頓服", "Z頓服"), repository.state.value.prnMedications.map { it.name })
        assertEquals(setOf("pending"), repository.state.value.outOfStockMedicationIds)
        assertTrue(repository.state.value.hasLowStock)
        assertFalse(repository.state.value.loadFailed)
    }

    @Test
    fun failedPatientSwitchClearsPreviousPatientContentAndExposesRetryState() = runTest {
        var fail = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> = if (fail) error("offline") else listOf(dose("old", DoseStatus.PENDING, "2026-07-15T00:00:00Z"))
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        repository.load("one")

        fail = true
        repository.load("two")

        assertEquals("two", repository.state.value.patientId)
        assertTrue(repository.state.value.doses.isEmpty())
        assertTrue(repository.state.value.loadFailed)
    }

    private fun dose(key: String, status: DoseStatus, at: String) = PatientDose(
        key = key,
        medicationId = key,
        scheduledAt = Instant.parse(at),
        status = status,
        medicationName = key,
        dosageText = "5 mg",
        doseCount = 1.0,
        patientId = "patient-1",
    )

    private fun medication(id: String, name: String, isPrn: Boolean, active: Boolean) = PatientMedication(
        id, "patient-1", name, "5 mg", 1.0, 5.0, "mg", null, isPrn, null,
        Instant.parse("2026-01-01T00:00:00Z"), null, null, null, false, 0.0, false,
        active, !active, null, null, null,
    )

    private fun todayJson() = """{"data":[{"key":"dose-1","patientId":"patient-1","medicationId":"med-1","scheduledAt":"2026-07-15T08:00:00Z","effectiveStatus":"pending","recordedByType":null,"medicationSnapshot":{"name":"血圧の薬","dosageText":"5 mg","doseCountPerIntake":1.0,"dosageStrengthValue":5.0,"dosageStrengthUnit":"mg","notes":null}}]}"""

    private fun medicationsJson() = """{"data":[{"id":"prn-1","patientId":"patient-1","name":"頓服","dosageText":"200 mg","doseCountPerIntake":1.0,"dosageStrengthValue":200.0,"dosageStrengthUnit":"mg","isPrn":true,"startDate":"2026-01-01T00:00:00Z","inventoryEnabled":false,"inventoryQuantity":0.0,"inventoryOut":false,"isActive":true,"isArchived":false}]}"""

    private fun inventoryJson() = """{"data":{"patientId":"patient-1","medications":[{"medicationId":"med-1","doseCountPerIntake":1.0,"inventoryEnabled":true,"inventoryQuantity":0.5,"low":true,"out":false}]}}"""
}
