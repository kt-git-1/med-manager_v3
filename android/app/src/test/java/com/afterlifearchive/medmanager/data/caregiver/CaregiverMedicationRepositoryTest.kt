package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaregiverMedicationRepositoryTest {
    @Test
    fun apiUsesSelectedPatientQueryAndCaregiverAuth() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(200, MEDICATION_RESPONSE)
            },
        )

        val items = CaregiverMedicationApi(client).listMedications("patient-1")

        assertEquals("https://example.test/api/medications?patientId=patient-1", captured?.url)
        assertEquals("Bearer caregiver-token", captured?.headers?.get("Authorization"))
        assertEquals("med-1", items.single().id)
        assertEquals(listOf("08:00", "18:00"), items.single().regimenTimes)
    }

    @Test
    fun patientSwitchNeverRendersPreviousPatientItemsWhileLoading() = runTest {
        val repository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { patientId -> listOf(medication("med-$patientId", patientId)) },
            MutationFreshnessStore(),
        )

        repository.load("one")
        repository.load("two")

        assertEquals("two", repository.state.value.patientId)
        assertEquals(listOf("med-two"), repository.state.value.items.map { it.id })
        assertFalse(repository.state.value.loadFailed)
    }

    @Test
    fun failureIsExplicitAndClearsStaleItemsForThatPatient() = runTest {
        val repository = CaregiverMedicationRepository(
            CaregiverMedicationDataSource { error("offline") },
            MutationFreshnessStore(),
        )

        repository.load("one")

        assertTrue(repository.state.value.loadFailed)
        assertTrue(repository.state.value.items.isEmpty())
    }

    private fun medication(id: String, patientId: String) = PatientMedication(
        id, patientId, "薬", "10mg", 1.0, 10.0, "mg", null, false, null,
        Instant.parse("2026-07-01T00:00:00Z"), null, null, null, false, 0.0, false,
        true, false, null, listOf("08:00"), emptyList(),
    )

    private companion object {
        const val MEDICATION_RESPONSE = """{"data":[{"id":"med-1","patientId":"patient-1","name":"アムロジピン","dosageText":"5mg","doseCountPerIntake":1,"dosageStrengthValue":5,"dosageStrengthUnit":"mg","startDate":"2026-07-01T00:00:00Z","isActive":true,"isArchived":false,"regimenTimes":["08:00","18:00"],"regimenDaysOfWeek":[]}]}"""
    }
}
