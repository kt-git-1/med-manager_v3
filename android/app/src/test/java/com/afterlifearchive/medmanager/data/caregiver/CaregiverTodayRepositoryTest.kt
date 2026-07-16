package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.SlotBulkRecordResult
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    fun individualRecordAndDeleteUseExactCaregiverContracts() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                requests += request
                if (request.method == "POST") HttpResponse(200, """{"data":{"ok":true}}""") else HttpResponse(204, "")
            },
        )
        val api = CaregiverTodayApi(client)
        val dose = dose("med / one", DoseStatus.PENDING, "2026-07-15T08:00:00Z")

        api.recordDose("patient-1", dose)
        api.deleteDose("patient-1", dose.copy(status = DoseStatus.TAKEN))

        assertEquals(listOf("POST", "DELETE"), requests.map { it.method })
        assertEquals("https://example.test/api/patients/patient-1/dose-records", requests[0].url)
        assertEquals("{\"medicationId\":\"med / one\",\"scheduledAt\":\"2026-07-15T08:00:00Z\"}", requests[0].body)
        assertEquals(
            "https://example.test/api/patients/patient-1/dose-records?medicationId=med+%2F+one&scheduledAt=2026-07-15T08%3A00%3A00Z",
            requests[1].url,
        )
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer caregiver-token" })
    }

    @Test
    fun bulkSlotUsesSelectedPatientRouteAndMapsPartialResult() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(200, """{"updatedCount":1,"remainingCount":1,"insufficientCount":1,"totalPills":2.0,"medCount":2,"slotTime":"08:00","slotSummary":{"morning":"taken","noon":"pending","evening":"none","bedtime":"none"},"recordingGroupId":"group-1"}""")
            },
        )

        val result = CaregiverTodayApi(client).recordSlot("patient-1", "2026-07-14", MedicationSlot.MORNING)

        assertEquals("POST", captured?.method)
        assertEquals("https://example.test/api/patients/patient-1/dose-records/slot", captured?.url)
        assertEquals("{\"date\":\"2026-07-14\",\"slot\":\"morning\"}", captured?.body)
        assertEquals("Bearer caregiver-token", captured?.headers?.get("Authorization"))
        assertEquals(1, result.updatedCount)
        assertEquals(1, result.insufficientCount)
        assertEquals(HistoryStatus.TAKEN, result.slotSummary[MedicationSlot.MORNING])
    }

    @Test
    fun prnRecordUsesSelectedPatientCaregiverContract() = runTest {
        var captured: HttpRequest? = null
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                captured = request
                HttpResponse(200, """{"record":{"id":"prn-record-1"},"medicationInventory":null}""")
            },
        )
        val medication = medication("prn-1", "痛み止め", isPrn = true, active = true)

        CaregiverTodayApi(client).recordPrn("patient-1", medication)

        assertEquals("POST", captured?.method)
        assertEquals("https://example.test/api/patients/patient-1/prn-dose-records", captured?.url)
        assertEquals("{\"medicationId\":\"prn-1\",\"takenAt\":null,\"quantityTaken\":null}", captured?.body)
        assertEquals("Bearer caregiver-token", captured?.headers?.get("Authorization"))
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

    @Test
    fun successfulIndividualRecordAndDeleteUpdateLocalStateAndFreshness() = runTest {
        val freshness = MutationFreshnessStore()
        val calls = mutableListOf<String>()
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-15T08:00:00Z")
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordDose(patientId: String, dose: PatientDose) { calls += "record:${dose.key}" }
            override suspend fun deleteDose(patientId: String, dose: PatientDose) { calls += "delete:${dose.key}" }
        }
        val repository = CaregiverTodayRepository(
            source,
            freshness,
            now = { Instant.parse("2026-07-15T08:30:00Z") },
        )
        repository.load("patient-1")

        assertTrue(repository.recordDose("patient-1", original))
        val recorded = repository.state.value.doses.single()
        assertEquals(DoseStatus.TAKEN, recorded.status)
        assertEquals(com.afterlifearchive.medmanager.data.patient.RecordedByType.CAREGIVER, recorded.recordedByType)
        assertEquals(CaregiverTodayMutationMessage.RECORDED, repository.state.value.mutationMessage)
        assertTrue(repository.deleteDose("patient-1", recorded))

        assertEquals(DoseStatus.PENDING, repository.state.value.doses.single().status)
        assertEquals(CaregiverTodayMutationMessage.DELETED, repository.state.value.mutationMessage)
        assertEquals(listOf("record:dose-1", "delete:dose-1"), calls)
        assertEquals(2L, freshness.revisions.value.dose)
        assertEquals(2L, freshness.revisions.value.inventory)
        assertEquals(2L, freshness.revisions.value.notificationPlan)
    }

    @Test
    fun deletingOverdueDoseRestoresMissedStatus() = runTest {
        val original = dose("dose-1", DoseStatus.TAKEN, "2026-07-15T08:00:00Z")
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun deleteDose(patientId: String, dose: PatientDose) = Unit
        }
        val repository = CaregiverTodayRepository(
            source,
            MutationFreshnessStore(),
            now = { Instant.parse("2026-07-15T09:00:01Z") },
        )
        repository.load("patient-1")

        assertTrue(repository.deleteDose("patient-1", original))
        assertEquals(DoseStatus.MISSED, repository.state.value.doses.single().status)
    }

    @Test
    fun silentReconciliationKeepsOptimisticCaregiverResultInteractive() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-15T08:00:00Z")
        var todayCalls = 0
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> {
                todayCalls += 1
                if (todayCalls > 1) refreshGate.await()
                return listOf(original.copy(status = if (todayCalls > 1) DoseStatus.TAKEN else DoseStatus.PENDING))
            }
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordDose(patientId: String, dose: PatientDose) = Unit
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        repository.load("patient-1")
        assertTrue(repository.recordDose("patient-1", original))

        val refresh = launch { repository.load("patient-1", showProgress = false) }
        runCurrent()

        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.single().status)
        assertEquals(CaregiverTodayMutationMessage.RECORDED, repository.state.value.mutationMessage)
        assertFalse(repository.state.value.refreshing)

        refreshGate.complete(Unit)
        refresh.join()
    }

    @Test
    fun inventoryGuardPreservesDoseStateWithoutCallingServer() = runTest {
        var recordCalls = 0
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-15T08:00:00Z")
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = listOf(CaregiverInventorySummary("dose-1", true, 0.0, 1.0, true, true))
            override suspend fun recordDose(patientId: String, dose: PatientDose) { recordCalls += 1; error("offline") }
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        repository.load("patient-1")

        assertFalse(repository.recordDose("patient-1", original))
        assertEquals(0, recordCalls)
        assertEquals(DoseStatus.PENDING, repository.state.value.doses.single().status)
        assertEquals(CaregiverTodayMutationError.INSUFFICIENT_INVENTORY, repository.state.value.mutationError)
    }

    @Test
    fun serverFailurePreservesDoseAndPublishesNoFreshness() = runTest {
        val freshness = MutationFreshnessStore()
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-15T08:00:00Z")
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordDose(patientId: String, dose: PatientDose) = error("offline")
        }
        val repository = CaregiverTodayRepository(source, freshness)
        repository.load("patient-1")

        assertFalse(repository.recordDose("patient-1", original))

        assertEquals(DoseStatus.PENDING, repository.state.value.doses.single().status)
        assertEquals(CaregiverTodayMutationError.FAILED, repository.state.value.mutationError)
        assertEquals(0L, freshness.revisions.value.dose)
    }

    @Test
    fun caregiverBulkRecordsOlderMissedSlotWithoutClientTimeWindow() = runTest {
        val freshness = MutationFreshnessStore()
        val olderMissed = dose("old-missed", DoseStatus.MISSED, "2026-07-13T23:00:00Z")
        var request: Triple<String, String, MedicationSlot>? = null
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(olderMissed)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot): SlotBulkRecordResult {
                request = Triple(patientId, date, slot)
                return slotResult(updated = 1)
            }
        }
        val repository = CaregiverTodayRepository(source, freshness)
        repository.load("patient-1")

        assertTrue(repository.recordSlot("patient-1", MedicationSlot.MORNING, listOf(olderMissed)))

        assertEquals(Triple("patient-1", "2026-07-14", MedicationSlot.MORNING), request)
        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.single().status)
        assertEquals(CaregiverTodayMutationMessage.SLOT_RECORDED, repository.state.value.mutationMessage)
        assertEquals(1L, freshness.revisions.value.dose)
    }

    @Test
    fun partialBulkResultKeepsInsufficientDoseAndReportsCounts() = runTest {
        val available = dose("available", DoseStatus.PENDING, "2026-07-14T23:00:00Z")
        val insufficient = dose("insufficient", DoseStatus.MISSED, "2026-07-14T23:00:00Z")
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = listOf(available, insufficient)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = listOf(CaregiverInventorySummary("insufficient", true, 0.0, 1.0, true, true))
            override suspend fun recordSlot(patientId: String, date: String, slot: MedicationSlot) = slotResult(updated = 1, insufficient = 1)
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        repository.load("patient-1")

        assertTrue(repository.recordSlot("patient-1", MedicationSlot.MORNING, listOf(available, insufficient)))

        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.first { it.key == "available" }.status)
        assertEquals(DoseStatus.MISSED, repository.state.value.doses.first { it.key == "insufficient" }.status)
        assertEquals(CaregiverTodayMutationMessage.SLOT_PARTIAL, repository.state.value.mutationMessage)
        assertEquals(1, repository.state.value.lastUpdatedCount)
        assertEquals(1, repository.state.value.lastInsufficientCount)
    }

    @Test
    fun prnSuccessPublishesDoseInventoryFreshnessAndFailureDoesNot() = runTest {
        val freshness = MutationFreshnessStore()
        val prn = medication("prn-1", "痛み止め", isPrn = true, active = true)
        var fail = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = emptyList<PatientDose>()
            override suspend fun medications(patientId: String) = listOf(prn)
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordPrn(patientId: String, medication: PatientMedication) {
                if (fail) error("offline")
            }
        }
        val repository = CaregiverTodayRepository(source, freshness)
        repository.load("patient-1")

        assertTrue(repository.recordPrn("patient-1", prn))
        assertEquals(CaregiverTodayMutationMessage.PRN_RECORDED, repository.state.value.mutationMessage)
        assertEquals(1L, freshness.revisions.value.dose)
        assertEquals(1L, freshness.revisions.value.inventory)
        assertEquals(0L, freshness.revisions.value.notificationPlan)

        fail = true
        assertFalse(repository.recordPrn("patient-1", prn))
        assertEquals(CaregiverTodayMutationError.FAILED, repository.state.value.mutationError)
        assertEquals(1L, freshness.revisions.value.dose)
    }

    @Test
    fun failedFollowUpRefreshPreservesSuccessfulMutationUi() = runTest {
        val original = dose("dose-1", DoseStatus.PENDING, "2026-07-15T08:00:00Z")
        var refreshFails = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> = if (refreshFails) error("refresh offline") else listOf(original)
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
            override suspend fun recordDose(patientId: String, dose: PatientDose) = Unit
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        repository.load("patient-1")
        assertTrue(repository.recordDose("patient-1", original))

        refreshFails = true
        repository.load("patient-1")

        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.single().status)
        assertEquals(CaregiverTodayMutationMessage.RECORDED, repository.state.value.mutationMessage)
        assertEquals(null, repository.state.value.mutationError)
        assertTrue(repository.state.value.refreshFailed)
        assertFalse(repository.state.value.loading)
        assertFalse(repository.state.value.refreshing)
        assertFalse(repository.state.value.loadFailed)
        assertFalse(repository.recordDose("patient-1", repository.state.value.doses.single()))
    }

    @Test
    fun failedRefreshPreservesLoadedEmptyTodaySnapshot() = runTest {
        var fail = false
        val source = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String): List<PatientDose> = if (fail) error("offline") else emptyList()
            override suspend fun medications(patientId: String) = emptyList<PatientMedication>()
            override suspend fun inventory(patientId: String) = emptyList<CaregiverInventorySummary>()
        }
        val repository = CaregiverTodayRepository(source, MutationFreshnessStore())
        repository.load("patient-1")
        fail = true

        repository.load("patient-1")

        assertTrue(repository.state.value.hasLoaded)
        assertTrue(repository.state.value.refreshFailed)
        assertFalse(repository.state.value.loadFailed)
        assertTrue(repository.state.value.doses.isEmpty())
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

    private fun slotResult(updated: Int, insufficient: Int = 0) = SlotBulkRecordResult(
        updatedCount = updated,
        remainingCount = insufficient,
        insufficientCount = insufficient,
        totalPills = 2.0,
        medCount = 2,
        slotTime = "08:00",
        slotSummary = MedicationSlot.entries.associateWith { HistoryStatus.NONE },
        recordingGroupId = if (updated > 0) "group-1" else null,
    )
}
