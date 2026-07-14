package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverReportRepositoryTest {
    @Test
    fun presetsAndValidationMatchInclusiveTokyoContract() {
        val today = LocalDate.of(2026, 7, 15)
        assertEquals(ReportPeriod(LocalDate.of(2026, 7, 1), today), ReportPeriod.preset(ReportPeriodPreset.THIS_MONTH, today))
        assertEquals(ReportPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)), ReportPeriod.preset(ReportPeriodPreset.LAST_MONTH, today))
        assertEquals(30, ReportPeriod.preset(ReportPeriodPreset.LAST_30_DAYS, today).days)
        assertEquals(90, ReportPeriod.preset(ReportPeriodPreset.LAST_90_DAYS, today).days)
        assertEquals(ReportPeriodValidation.FUTURE, ReportPeriod.validation(today, today.plusDays(1), today))
        assertEquals(ReportPeriodValidation.REVERSED, ReportPeriod.validation(today, today.minusDays(1), today))
        assertEquals(ReportPeriodValidation.TOO_LONG, ReportPeriod.validation(today.minusDays(90), today, today))
        assertNull(ReportPeriod.validation(today.minusDays(89), today, today))
    }

    @Test
    fun apiUsesExactEntitlementAndReportContracts() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val api = CaregiverReportApi(ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "token" },
            transport = HttpTransport { request ->
                requests += request
                HttpResponse(200, if (request.url.endsWith("entitlements")) """{"data":{"premium":true,"entitlements":[]}}""" else REPORT_JSON)
            },
        ))

        assertTrue(api.premium())
        val report = api.report("p1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15))

        assertEquals("さくら", report.patient.displayName)
        assertEquals("https://example.test/api/me/entitlements", requests[0].url)
        assertEquals("https://example.test/api/patients/p1/history/report?from=2026-07-01&to=2026-07-15", requests[1].url)
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer token" })
    }

    @Test
    fun entitlementAndRetentionFailuresRemainTyped() = runTest {
        var failEntitlement = false
        val source = object : CaregiverReportDataSource {
            override suspend fun premium(): Boolean { if (failEntitlement) error("offline") else return false }
            override suspend fun report(patientId: String, from: LocalDate, to: LocalDate): CaregiverHistoryReport =
                throw ApiException.HistoryRetentionLimit("2026-06-16", 30)
        }
        val repository = CaregiverReportRepository(source)
        assertEquals(CaregiverEntitlement.FREE, repository.refreshEntitlement())
        failEntitlement = true
        repository.clear()
        assertEquals(CaregiverEntitlement.UNKNOWN, repository.refreshEntitlement())
        assertTrue(repository.state.value.entitlementFailed)

        assertNull(repository.loadReport("p1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)))
        assertEquals("2026-06-16", repository.state.value.retentionCutoffDate)
        assertFalse(repository.state.value.generationFailed)
    }

    companion object {
        const val REPORT_JSON = """{"patient":{"id":"p1","displayName":"さくら"},"range":{"from":"2026-07-01","to":"2026-07-15","timezone":"Asia/Tokyo","days":15},"days":[{"date":"2026-07-15","slots":{"morning":[{"medicationId":"m1","name":"薬A","dosageText":"1錠","doseCount":1.0,"status":"TAKEN","recordedAt":"2026-07-14T23:00:00Z","recordedBy":"PATIENT"}],"noon":[],"evening":[],"bedtime":[]},"prn":[]}]}"""
    }
}
