package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverHistoryRepositoryTest {
    @Test
    fun apiUsesExactCaregiverMonthDayAndBackfillContracts() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val api = CaregiverHistoryApi(ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "token" },
            transport = HttpTransport { request ->
                requests += request
                when {
                    request.url.contains("/month?") -> HttpResponse(200, MONTH_JSON)
                    request.url.contains("/day?") -> HttpResponse(200, DAY_JSON)
                    else -> HttpResponse(200, "{}")
                }
            },
        ))

        assertEquals(1, api.month("patient-1", YearMonth.of(2026, 7)).size)
        val detail = api.day("patient-1", LocalDate.of(2026, 7, 15))
        api.recordMissed("patient-1", detail.doses.single())

        assertEquals("https://example.test/api/patients/patient-1/history/month?year=2026&month=7", requests[0].url)
        assertEquals("https://example.test/api/patients/patient-1/history/day?date=2026-07-15", requests[1].url)
        assertEquals("https://example.test/api/patients/patient-1/dose-records", requests[2].url)
        assertEquals("{\"medicationId\":\"med-1\",\"scheduledAt\":\"2026-07-15T23:00:00Z\"}", requests[2].body)
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer token" })
    }

    @Test
    fun patientSwitchAndMonthSwitchNeverExposePreviousSnapshot() = runTest {
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(day("${yearMonth}-01"))
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }
        val repository = CaregiverHistoryRepository(source, MutationFreshnessStore())
        repository.loadMonth("one", YearMonth.of(2026, 7))
        repository.loadMonth("one", YearMonth.of(2026, 6))
        assertEquals("2026-06-01", repository.state.value.days.single().date)
        repository.loadMonth("two", YearMonth.of(2026, 5))
        assertEquals("two", repository.state.value.patientId)
        assertEquals("2026-05-01", repository.state.value.days.single().date)
    }

    @Test
    fun retentionIsStructuredAndGenericFailureStaysSeparate() = runTest {
        var retention = true
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth): List<HistoryDay> {
                if (retention) throw ApiException.HistoryRetentionLimit("2026-06-16", 30)
                error("offline")
            }
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }
        val repository = CaregiverHistoryRepository(source, MutationFreshnessStore())

        repository.loadMonth("p1", YearMonth.of(2026, 5))
        assertEquals("2026-06-16", repository.state.value.retentionCutoffDate)
        assertEquals(30, repository.state.value.retentionDays)
        assertFalse(repository.state.value.monthFailed)

        retention = false
        repository.loadMonth("p1", YearMonth.of(2026, 5))
        assertTrue(repository.state.value.monthFailed)
        assertNull(repository.state.value.retentionCutoffDate)
    }

    @Test
    fun failedMonthRefreshKeepsSnapshotAndDoesNotBecomeInitialLoadFailure() = runTest {
        var fail = false
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) =
                if (fail) error("offline") else listOf(day("2026-07-15"))
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }
        val repository = CaregiverHistoryRepository(source, MutationFreshnessStore())
        repository.loadMonth("p1", YearMonth.of(2026, 7))
        fail = true

        repository.loadMonth("p1", YearMonth.of(2026, 7))

        assertEquals(listOf("2026-07-15"), repository.state.value.days.map { it.date })
        assertTrue(repository.state.value.monthRefreshFailed)
        assertFalse(repository.state.value.monthFailed)
    }

    @Test
    fun failedRefreshPreservesLoadedEmptyHistoryMonth() = runTest {
        var fail = false
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth): List<HistoryDay> =
                if (fail) error("offline") else emptyList()
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        }
        val repository = CaregiverHistoryRepository(source, MutationFreshnessStore())
        repository.loadMonth("p1", YearMonth.of(2026, 7))
        fail = true

        repository.loadMonth("p1", YearMonth.of(2026, 7))

        assertTrue(repository.state.value.monthLoaded)
        assertTrue(repository.state.value.monthRefreshFailed)
        assertFalse(repository.state.value.monthFailed)
    }

    @Test
    fun backfillPublishesOnlyAfterSuccessAndRefreshesAuthoritativeDay() = runTest {
        val freshness = MutationFreshnessStore()
        var recorded = false
        var fail = false
        val missed = missedDose()
        val source = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(day("2026-07-15"))
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), listOf(missed.copy(status = if (recorded) DoseStatus.TAKEN else DoseStatus.MISSED)), emptyList())
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) { if (fail) error("offline") else recorded = true }
        }
        val repository = CaregiverHistoryRepository(source, freshness)
        repository.loadMonth("p1", YearMonth.of(2026, 7))
        repository.selectDate(LocalDate.of(2026, 7, 15))
        repository.loadDay("p1", LocalDate.of(2026, 7, 15))

        assertTrue(repository.recordMissed("p1", missed))
        assertEquals(DoseStatus.TAKEN, repository.state.value.dayDetail?.doses?.single()?.status)
        assertEquals(1L, freshness.revisions.value.dose)
        assertEquals(1L, freshness.revisions.value.inventory)
        assertEquals(1L, freshness.revisions.value.notificationPlan)

        fail = true
        assertFalse(repository.recordMissed("p1", missed.copy(status = DoseStatus.MISSED)))
        assertEquals(1L, freshness.revisions.value.dose)
    }

    @Test
    fun remotePushParserRequiresExactPrivatePayloadAndKeepsTarget() {
        val repository = CaregiverHistoryRepository(noopSource(), MutationFreshnessStore())

        assertFalse(repository.handleNotificationTarget("OTHER", "p1", "2026-07-15", "morning"))
        assertFalse(repository.handleNotificationTarget("DOSE_TAKEN", "p1", "bad", "morning"))
        assertTrue(repository.handleNotificationTarget("DOSE_TAKEN", "p2", "2026-07-15", "evening"))

        assertEquals("p2", repository.state.value.notificationPatientId)
        assertEquals(LocalDate.of(2026, 7, 15), repository.state.value.selectedDate)
        assertEquals(YearMonth.of(2026, 7), repository.state.value.displayedMonth)
        assertEquals(MedicationSlot.EVENING, repository.state.value.highlightedSlot)
        assertEquals(1L, repository.state.value.navigationRequestId)
    }

    private fun noopSource() = object : CaregiverHistoryDataSource {
        override suspend fun month(patientId: String, yearMonth: YearMonth) = emptyList<HistoryDay>()
        override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(date.toString(), emptyList(), emptyList())
        override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
    }

    private fun day(date: String) = HistoryDay(date, HistoryStatus.MISSED, HistoryStatus.NONE, HistoryStatus.NONE, HistoryStatus.NONE, 0)
    private fun missedDose() = HistoryScheduledDose("med-1", "薬A", "1錠", 1.0, Instant.parse("2026-07-15T23:00:00Z"), MedicationSlot.MORNING, DoseStatus.MISSED, null)

    companion object {
        const val MONTH_JSON = """{"year":2026,"month":7,"days":[{"date":"2026-07-15","slotSummary":{"morning":"missed","noon":"none","evening":"none","bedtime":"none"}}],"prnCountByDay":{"2026-07-15":1}}"""
        const val DAY_JSON = """{"date":"2026-07-15","doses":[{"medicationId":"med-1","medicationName":"薬A","dosageText":"1錠","doseCountPerIntake":1.0,"scheduledAt":"2026-07-15T23:00:00Z","slot":"morning","effectiveStatus":"missed","recordedByType":null}],"prnItems":[]}"""
    }
}
