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
import java.time.LocalDate
import org.json.JSONObject

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
    fun apiCreateAndUpdateUseCaregiverContract() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                requests += request
                HttpResponse(if (request.method == "POST") 201 else 200, MEDICATION_ITEM_RESPONSE)
            },
        )
        val api = CaregiverMedicationApi(client)
        val draft = validDraft()

        api.createMedication("patient-1", draft)
        api.updateMedication("patient-1", "med-1", draft.copy(name = "更新薬"))

        val create = requests[0]
        assertEquals("POST", create.method)
        assertEquals("https://example.test/api/medications", create.url)
        assertEquals("patient-1", JSONObject(create.body!!).getString("patientId"))
        assertEquals("Bearer caregiver-token", create.headers["Authorization"])
        val update = requests[1]
        assertEquals("PATCH", update.method)
        assertEquals("https://example.test/api/medications/med-1?patientId=patient-1", update.url)
        assertFalse(JSONObject(update.body!!).has("patientId"))
        assertEquals("更新薬", JSONObject(update.body!!).getString("name"))
    }

    @Test
    fun saveUpdatesStateAndFreshnessOnlyAfterServerSuccess() = runTest {
        val freshness = MutationFreshnessStore()
        val saved = medication("med-new", "patient-1")
        val dataSource = object : CaregiverMedicationDataSource {
            override suspend fun listMedications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun createMedication(patientId: String, draft: CaregiverMedicationDraft) = saved
        }
        val repository = CaregiverMedicationRepository(dataSource, freshness)
        repository.load("patient-1")

        val result = repository.save("patient-1", null, validDraft())

        assertTrue(result.isSuccess)
        assertEquals(listOf("med-new"), repository.state.value.items.map { it.id })
        assertEquals(1L, freshness.revisions.value.medication)
        assertEquals(1L, freshness.revisions.value.inventory)
        assertEquals(1L, freshness.revisions.value.notificationPlan)
    }

    @Test
    fun failedSavePreservesStateAndFreshness() = runTest {
        val freshness = MutationFreshnessStore()
        val dataSource = object : CaregiverMedicationDataSource {
            override suspend fun listMedications(patientId: String) = listOf(medication("old", patientId))
            override suspend fun updateMedication(patientId: String, medicationId: String, draft: CaregiverMedicationDraft): PatientMedication = error("offline")
        }
        val repository = CaregiverMedicationRepository(dataSource, freshness)
        repository.load("patient-1")

        val result = repository.save("patient-1", "old", validDraft())

        assertTrue(result.isFailure)
        assertEquals(listOf("old"), repository.state.value.items.map { it.id })
        assertEquals(0L, freshness.revisions.value.medication)
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

    private fun validDraft() = CaregiverMedicationDraft(
        name = "アムロジピン",
        dosageStrengthValue = "5",
        dosageStrengthUnit = "mg",
        doseCountPerIntake = "1",
        startDate = LocalDate.parse("2026-07-15"),
        inventoryCount = "30",
    )

    private companion object {
        const val MEDICATION_RESPONSE = """{"data":[{"id":"med-1","patientId":"patient-1","name":"アムロジピン","dosageText":"5mg","doseCountPerIntake":1,"dosageStrengthValue":5,"dosageStrengthUnit":"mg","startDate":"2026-07-01T00:00:00Z","isActive":true,"isArchived":false,"regimenTimes":["08:00","18:00"],"regimenDaysOfWeek":[]}]}"""
        const val MEDICATION_ITEM_RESPONSE = """{"data":{"id":"med-1","patientId":"patient-1","name":"アムロジピン","dosageText":"5mg","doseCountPerIntake":1,"dosageStrengthValue":5,"dosageStrengthUnit":"mg","startDate":"2026-07-01T00:00:00Z","isActive":true,"isArchived":false}}"""
    }
}
