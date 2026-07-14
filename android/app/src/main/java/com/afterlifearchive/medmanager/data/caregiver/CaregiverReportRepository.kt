package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.patient.PatientWireJson
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

enum class CaregiverEntitlement { UNKNOWN, FREE, PREMIUM }

data class CaregiverReportState(
    val entitlement: CaregiverEntitlement = CaregiverEntitlement.UNKNOWN,
    val checkingEntitlement: Boolean = false,
    val entitlementFailed: Boolean = false,
    val generating: Boolean = false,
    val generationFailed: Boolean = false,
    val retentionCutoffDate: String? = null,
    val retentionDays: Int? = null,
)

@Serializable
data class CaregiverHistoryReport(
    val patient: CaregiverReportPatient,
    val range: CaregiverReportRange,
    val days: List<CaregiverReportDay>,
)

@Serializable
data class CaregiverReportPatient(val id: String, val displayName: String)

@Serializable
data class CaregiverReportRange(val from: String, val to: String, val timezone: String, val days: Int)

@Serializable
data class CaregiverReportDay(val date: String, val slots: CaregiverReportSlots, val prn: List<CaregiverReportPrnItem>)

@Serializable
data class CaregiverReportSlots(
    val morning: List<CaregiverReportSlotItem>,
    val noon: List<CaregiverReportSlotItem>,
    val evening: List<CaregiverReportSlotItem>,
    val bedtime: List<CaregiverReportSlotItem>,
) {
    fun all() = morning + noon + evening + bedtime
}

@Serializable
data class CaregiverReportSlotItem(
    val medicationId: String,
    val name: String,
    val dosageText: String,
    val doseCount: Double,
    val status: String,
    val recordedAt: String? = null,
    val recordedBy: String? = null,
)

@Serializable
data class CaregiverReportPrnItem(
    val medicationId: String,
    val name: String,
    val dosageText: String,
    val quantity: Double,
    val recordedAt: String,
    val recordedBy: String,
)

interface CaregiverReportDataSource {
    suspend fun premium(): Boolean
    suspend fun report(patientId: String, from: LocalDate, to: LocalDate): CaregiverHistoryReport
}

class CaregiverReportApi(private val client: ApiClient) : CaregiverReportDataSource {
    override suspend fun premium(): Boolean {
        val body = client.getBody("api/me/entitlements", RequestAuthPolicy.CAREGIVER)
        return PatientWireJson.decodeFromString<EntitlementsEnvelope>(body).data.premium
    }

    override suspend fun report(patientId: String, from: LocalDate, to: LocalDate): CaregiverHistoryReport {
        val body = client.getBody(
            "api/patients/$patientId/history/report?from=$from&to=$to",
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString(body)
    }
}

@Serializable
private data class EntitlementsEnvelope(val data: EntitlementsData)

@Serializable
private data class EntitlementsData(val premium: Boolean)

class CaregiverReportRepository(private val dataSource: CaregiverReportDataSource) {
    private val mutableState = MutableStateFlow(CaregiverReportState())
    val state: StateFlow<CaregiverReportState> = mutableState.asStateFlow()

    suspend fun refreshEntitlement(): CaregiverEntitlement {
        if (mutableState.value.checkingEntitlement) return mutableState.value.entitlement
        mutableState.value = mutableState.value.copy(checkingEntitlement = true, entitlementFailed = false)
        return try {
            val value = if (dataSource.premium()) CaregiverEntitlement.PREMIUM else CaregiverEntitlement.FREE
            mutableState.value = mutableState.value.copy(entitlement = value, checkingEntitlement = false)
            value
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(checkingEntitlement = false, entitlementFailed = true)
            CaregiverEntitlement.UNKNOWN
        }
    }

    suspend fun loadReport(patientId: String, from: LocalDate, to: LocalDate): CaregiverHistoryReport? {
        if (!ReportPeriod.isValid(from, to) || mutableState.value.generating) return null
        mutableState.value = mutableState.value.copy(
            generating = true,
            generationFailed = false,
            retentionCutoffDate = null,
            retentionDays = null,
        )
        return try {
            dataSource.report(patientId, from, to).also {
                mutableState.value = mutableState.value.copy(generating = false)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val retention = error as? ApiException.HistoryRetentionLimit
            mutableState.value = mutableState.value.copy(
                generating = false,
                generationFailed = retention == null,
                retentionCutoffDate = retention?.cutoffDate,
                retentionDays = retention?.retentionDays,
            )
            null
        }
    }

    fun clear() { mutableState.value = CaregiverReportState() }
}

enum class ReportPeriodPreset { THIS_MONTH, LAST_MONTH, LAST_30_DAYS, LAST_90_DAYS, CUSTOM }

data class ReportPeriod(val from: LocalDate, val to: LocalDate) {
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1

    companion object {
        fun preset(value: ReportPeriodPreset, today: LocalDate): ReportPeriod = when (value) {
            ReportPeriodPreset.THIS_MONTH -> ReportPeriod(today.withDayOfMonth(1), today)
            ReportPeriodPreset.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1).let { ReportPeriod(it, it.withDayOfMonth(it.lengthOfMonth())) }
            ReportPeriodPreset.LAST_30_DAYS -> ReportPeriod(today.minusDays(29), today)
            ReportPeriodPreset.LAST_90_DAYS -> ReportPeriod(today.minusDays(89), today)
            ReportPeriodPreset.CUSTOM -> ReportPeriod(today, today)
        }

        fun validation(from: LocalDate, to: LocalDate, today: LocalDate): ReportPeriodValidation? = when {
            to > today -> ReportPeriodValidation.FUTURE
            from > to -> ReportPeriodValidation.REVERSED
            java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > 90 -> ReportPeriodValidation.TOO_LONG
            else -> null
        }

        fun isValid(from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))) = validation(from, to, today) == null
    }
}

enum class ReportPeriodValidation { FUTURE, REVERSED, TOO_LONG }
