package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.freshness.FreshnessCursor
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.network.RequestAuthPolicy
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientHistoryDayResponseDto
import com.afterlifearchive.medmanager.data.patient.PatientHistoryMonthResponseDto
import com.afterlifearchive.medmanager.data.patient.PatientWireJson
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class CaregiverHistoryState(
    val patientId: String? = null,
    val displayedMonth: YearMonth = YearMonth.now(TOKYO_ZONE),
    val monthLoaded: Boolean = false,
    val days: List<HistoryDay> = emptyList(),
    val selectedDate: LocalDate? = null,
    val dayDetail: HistoryDayDetail? = null,
    val loadingMonth: Boolean = false,
    val refreshingMonth: Boolean = false,
    val monthFailed: Boolean = false,
    val monthRefreshFailed: Boolean = false,
    val loadingDay: Boolean = false,
    val dayFailed: Boolean = false,
    val dayRefreshFailed: Boolean = false,
    val updating: Boolean = false,
    val mutationFailed: Boolean = false,
    val mutationSucceeded: Boolean = false,
    val retentionCutoffDate: String? = null,
    val retentionDays: Int? = null,
    val highlightedSlot: MedicationSlot? = null,
    val notificationPatientId: String? = null,
    val navigationRequestId: Long = 0,
)

interface CaregiverHistoryDataSource {
    suspend fun month(patientId: String, yearMonth: YearMonth): List<HistoryDay>
    suspend fun day(patientId: String, date: LocalDate): HistoryDayDetail
    suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose)
}

class CaregiverHistoryApi(private val client: ApiClient) : CaregiverHistoryDataSource {
    override suspend fun month(patientId: String, yearMonth: YearMonth): List<HistoryDay> {
        val body = client.getBody(
            "api/patients/$patientId/history/month?year=${yearMonth.year}&month=${yearMonth.monthValue}",
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<PatientHistoryMonthResponseDto>(body).toDomain()
    }

    override suspend fun day(patientId: String, date: LocalDate): HistoryDayDetail {
        val body = client.getBody(
            "api/patients/$patientId/history/day?date=$date",
            RequestAuthPolicy.CAREGIVER,
        )
        return PatientWireJson.decodeFromString<PatientHistoryDayResponseDto>(body).toDomain()
    }

    override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) {
        client.postBody(
            "api/patients/$patientId/dose-records",
            PatientWireJson.encodeToString(CaregiverHistoryDoseRequest(dose.medicationId, dose.scheduledAt.toString())),
            RequestAuthPolicy.CAREGIVER,
        )
    }
}

@Serializable
private data class CaregiverHistoryDoseRequest(val medicationId: String, val scheduledAt: String)

class CaregiverHistoryRepository(
    private val dataSource: CaregiverHistoryDataSource,
    private val freshnessStore: MutationFreshnessStore,
) {
    private val mutableState = MutableStateFlow(CaregiverHistoryState())
    val state: StateFlow<CaregiverHistoryState> = mutableState.asStateFlow()
    val freshness = freshnessStore.revisions

    suspend fun loadMonth(patientId: String, yearMonth: YearMonth = mutableState.value.displayedMonth) {
        val current = mutableState.value
        if ((current.loadingMonth || current.refreshingMonth) && current.patientId == patientId && current.displayedMonth == yearMonth) return
        val sameSnapshot = current.patientId == patientId && current.displayedMonth == yearMonth && current.monthLoaded
        mutableState.value = when {
            current.patientId != patientId -> CaregiverHistoryState(
                patientId = patientId,
                displayedMonth = yearMonth,
                monthLoaded = false,
                selectedDate = current.selectedDate?.takeIf { current.notificationPatientId == patientId && YearMonth.from(it) == yearMonth },
                highlightedSlot = current.highlightedSlot.takeIf { current.notificationPatientId == patientId },
                notificationPatientId = current.notificationPatientId.takeIf { it == patientId },
                navigationRequestId = current.navigationRequestId,
                loadingMonth = true,
            )
            current.displayedMonth != yearMonth -> current.copy(
                displayedMonth = yearMonth,
                monthLoaded = false,
                days = emptyList(),
                selectedDate = null,
                dayDetail = null,
                loadingMonth = true,
                refreshingMonth = false,
                monthFailed = false,
                monthRefreshFailed = false,
                retentionCutoffDate = null,
                retentionDays = null,
            )
            else -> current.copy(
                loadingMonth = !sameSnapshot,
                refreshingMonth = sameSnapshot,
                monthFailed = false,
                monthRefreshFailed = false,
                retentionCutoffDate = null,
                retentionDays = null,
            )
        }
        try {
            val days = dataSource.month(patientId, yearMonth)
            val previous = mutableState.value
            val selected = previous.selectedDate?.takeIf { YearMonth.from(it) == yearMonth }
                ?: if (yearMonth == YearMonth.now(TOKYO_ZONE)) LocalDate.now(TOKYO_ZONE) else yearMonth.atDay(1)
            mutableState.value = previous.copy(
                patientId = patientId,
                displayedMonth = yearMonth,
                monthLoaded = true,
                days = days,
                selectedDate = selected,
                dayDetail = previous.dayDetail?.takeIf { it.date == selected.toString() },
                loadingMonth = false,
                refreshingMonth = false,
                monthFailed = false,
                monthRefreshFailed = false,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            acceptLoadFailure(error, month = true)
        }
    }

    suspend fun loadDay(patientId: String, date: LocalDate) {
        val current = mutableState.value
        if (current.loadingDay || current.patientId != patientId) return
        mutableState.value = current.copy(
            selectedDate = date,
            loadingDay = true,
            dayFailed = false,
            dayRefreshFailed = false,
            retentionCutoffDate = null,
            retentionDays = null,
        )
        try {
            val detail = dataSource.day(patientId, date)
            mutableState.value = mutableState.value.copy(
                dayDetail = detail,
                loadingDay = false,
                dayFailed = false,
                dayRefreshFailed = false,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            acceptLoadFailure(error, month = false)
        }
    }

    fun selectDate(date: LocalDate) {
        mutableState.value = mutableState.value.copy(
            selectedDate = date,
            dayDetail = null,
            dayFailed = false,
            dayRefreshFailed = false,
            mutationSucceeded = false,
        )
    }

    suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose): Boolean {
        val current = mutableState.value
        if (current.patientId != patientId || current.monthRefreshFailed || current.dayRefreshFailed || current.updating || dose.status.name != "MISSED") return false
        mutableState.value = current.copy(updating = true, mutationFailed = false, mutationSucceeded = false)
        return try {
            dataSource.recordMissed(patientId, dose)
            freshnessStore.markScheduledDoseChanged(inventoryChanged = true)
            mutableState.value = mutableState.value.copy(updating = false, mutationSucceeded = true)
            val date = mutableState.value.selectedDate
            loadMonth(patientId, mutableState.value.displayedMonth)
            if (date != null) loadDay(patientId, date)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(updating = false, mutationFailed = true)
            false
        }
    }

    fun handleNotificationTarget(type: String?, patientId: String?, date: String?, slot: String?): Boolean {
        if (type != "DOSE_TAKEN" || patientId.isNullOrBlank()) return false
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        val parsedSlot = runCatching { MedicationSlot.valueOf(slot.orEmpty().uppercase()) }.getOrNull() ?: return false
        mutableState.value = mutableState.value.copy(
            displayedMonth = YearMonth.from(parsedDate),
            selectedDate = parsedDate,
            dayDetail = null,
            highlightedSlot = parsedSlot,
            notificationPatientId = patientId,
            navigationRequestId = mutableState.value.navigationRequestId + 1,
        )
        return true
    }

    fun clearHighlight() { mutableState.value = mutableState.value.copy(highlightedSlot = null, notificationPatientId = null) }
    fun clear() { mutableState.value = CaregiverHistoryState() }
    fun newFreshnessCursor(): FreshnessCursor = freshnessStore.newCursor(FreshnessConsumer.CAREGIVER_HISTORY)

    private fun acceptLoadFailure(error: Exception, month: Boolean) {
        val retention = error as? ApiException.HistoryRetentionLimit
        val current = mutableState.value
        val hasMonthContent = current.monthLoaded
        val hasDayContent = current.dayDetail?.date == current.selectedDate?.toString()
        mutableState.value = mutableState.value.copy(
            loadingMonth = false,
            refreshingMonth = false,
            monthFailed = month && retention == null && !hasMonthContent,
            monthRefreshFailed = month && retention == null && hasMonthContent,
            loadingDay = false,
            dayFailed = !month && retention == null && !hasDayContent,
            dayRefreshFailed = !month && retention == null && hasDayContent,
            retentionCutoffDate = retention?.cutoffDate,
            retentionDays = retention?.retentionDays,
        )
    }
}

private val TOKYO_ZONE = java.time.ZoneId.of("Asia/Tokyo")
