package com.afterlifearchive.medmanager.data.patient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Instant
import com.afterlifearchive.medmanager.data.network.ApiException
import java.time.ZoneId

data class PatientUiState(
    val doses: List<PatientDose> = emptyList(),
    val slotTimes: PatientSlotTimes = PatientSlotTimes.DEFAULT,
    val medications: List<PatientMedication> = emptyList(),
    val history: List<HistoryDay> = emptyList(),
    val historyYear: Int? = null,
    val historyMonth: Int? = null,
    val historyDayDetail: HistoryDayDetail? = null,
    val historyDayLoading: Boolean = false,
    val retentionCutoffDate: String? = null,
    val retentionDays: Int? = null,
    val notificationTarget: PatientNotificationTarget? = null,
    val loading: Boolean = false,
    val updatingDoseKey: String? = null,
    val updatingSlot: MedicationSlot? = null,
    val updatingPrnMedicationId: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val medicationById: Map<String, PatientMedication> get() = medications.associateBy(PatientMedication::id)
    val prnMedications: List<PatientMedication> get() = medications.filter { it.isPrn && it.isActive && !it.isArchived }
    val insufficientMedicationIds: Set<String> get() = medications.filter(PatientMedication::isInsufficientForDose).mapTo(mutableSetOf(), PatientMedication::id)
}

class PatientRepository(private val api: PatientDataSource) {
    private val mutableState = MutableStateFlow(PatientUiState())
    val state: StateFlow<PatientUiState> = mutableState.asStateFlow()

    suspend fun loadToday() {
        mutableState.value = mutableState.value.copy(loading = true, error = null, message = null)
        runCatching {
            val slotTimes = runCatching { api.slotTimes() }.getOrElse { mutableState.value.slotTimes }
            val medications = api.medications()
            val doses = api.today()
                .map { it.copy(slot = slotTimes.resolve(it.scheduledAt)) }
                .sortedWith(compareBy<PatientDose>({ it.status.sortRank }, { it.scheduledAt }))
            Triple(slotTimes, medications, doses)
        }
            .onSuccess { (slotTimes, medications, doses) ->
                mutableState.value = mutableState.value.copy(slotTimes = slotTimes, medications = medications, doses = doses, loading = false)
            }
            .onFailure { mutableState.value = mutableState.value.copy(loading = false, error = it.message) }
    }

    suspend fun refreshTodayAfterAction() {
        val previousMessage = mutableState.value.message
        runCatching {
            val slotTimes = runCatching { api.slotTimes() }.getOrElse { mutableState.value.slotTimes }
            val medications = api.medications()
            val doses = api.today().map { it.copy(slot = slotTimes.resolve(it.scheduledAt)) }
                .sortedWith(compareBy<PatientDose>({ it.status.sortRank }, { it.scheduledAt }))
            Triple(slotTimes, medications, doses)
        }.onSuccess { (slotTimes, medications, doses) ->
            mutableState.value = mutableState.value.copy(
                slotTimes = slotTimes,
                medications = medications,
                doses = doses,
                loading = false,
                message = previousMessage,
            )
        }
    }

    suspend fun record(dose: PatientDose) {
        if (dose.status == DoseStatus.TAKEN) return
        if (mutableState.value.insufficientMedicationIds.contains(dose.medicationId)) {
            mutableState.value = mutableState.value.copy(error = "在庫が不足しているため記録できません。")
            return
        }
        mutableState.value = mutableState.value.copy(updatingDoseKey = dose.key, error = null, message = null)
        runCatching { api.recordDose(dose) }
            .onSuccess {
                val updated = mutableState.value.doses.map {
                    if (it.key == dose.key) it.copy(status = DoseStatus.TAKEN) else it
                }
                mutableState.value = mutableState.value.copy(
                    doses = updated,
                    updatingDoseKey = null,
                    message = "服薬を記録しました。",
                )
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(updatingDoseKey = null, error = it.message)
            }
    }

    suspend fun recordSlot(slot: MedicationSlot, date: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))) {
        if (mutableState.value.updatingSlot != null) return
        mutableState.value = mutableState.value.copy(updatingSlot = slot, error = null, message = null)
        runCatching { api.recordSlot(date.toString(), slot) }
            .onSuccess { result ->
                val message = when {
                    result.updatedCount > 0 && result.insufficientCount > 0 ->
                        "${result.updatedCount}件を記録しました。在庫不足の${result.insufficientCount}件は記録されていません。"
                    result.insufficientCount > 0 -> "在庫が不足しているため記録できません。"
                    result.updatedCount > 0 -> "${result.updatedCount}件の服薬を記録しました。"
                    else -> "記録できるお薬はありません。"
                }
                val updated = mutableState.value.doses.map { dose ->
                    if (result.updatedCount > 0 && dose.slot == slot && dose.status != DoseStatus.TAKEN && !mutableState.value.insufficientMedicationIds.contains(dose.medicationId)) {
                        dose.copy(status = DoseStatus.TAKEN)
                    } else dose
                }
                mutableState.value = mutableState.value.copy(doses = updated, updatingSlot = null, message = message)
            }
            .onFailure { error -> mutableState.value = mutableState.value.copy(updatingSlot = null, error = error.message) }
    }

    suspend fun recordPrn(medication: PatientMedication) {
        if (!medication.isPrn || mutableState.value.updatingPrnMedicationId != null) return
        if (medication.isInsufficientForDose) {
            mutableState.value = mutableState.value.copy(error = "在庫が不足しているため記録できません。")
            return
        }
        mutableState.value = mutableState.value.copy(updatingPrnMedicationId = medication.id, error = null, message = null)
        runCatching { api.recordPrn(medication) }
            .onSuccess { mutableState.value = mutableState.value.copy(updatingPrnMedicationId = null, message = "頓服の服用を記録しました。") }
            .onFailure { error -> mutableState.value = mutableState.value.copy(updatingPrnMedicationId = null, error = error.message) }
    }

    fun nextActionSlot(now: Instant = Instant.now()): MedicationSlot? {
        val state = mutableState.value
        return PatientTodayNextSlotSelector.select(
            state.doses.groupBy { it.slot }.mapNotNull { (slot, doses) ->
                slot ?: return@mapNotNull null
                val remaining = doses.filter { it.status != DoseStatus.TAKEN }
                val scheduledAt = doses.minOfOrNull(PatientDose::scheduledAt) ?: return@mapNotNull null
                NextSlotCandidate(
                    slot = slot,
                    scheduledAt = scheduledAt,
                    remainingCount = remaining.size,
                    isWithinRecordingWindow = now >= scheduledAt.minusSeconds(30 * 60) && now <= scheduledAt.plusSeconds(60 * 60),
                    hasRecordableInventory = remaining.any { !state.insufficientMedicationIds.contains(it.medicationId) },
                )
            },
            now,
        )
    }

    suspend fun loadHistory(date: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))) {
        mutableState.value = mutableState.value.copy(loading = true, error = null, message = null, retentionCutoffDate = null, retentionDays = null)
        runCatching { api.history(date.year, date.monthValue) }
            .onSuccess { mutableState.value = mutableState.value.copy(history = it, historyYear = date.year, historyMonth = date.monthValue, loading = false) }
            .onFailure { error ->
                val retention = error as? ApiException.HistoryRetentionLimit
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = if (retention == null) error.message else null,
                    retentionCutoffDate = retention?.cutoffDate,
                    retentionDays = retention?.retentionDays,
                )
            }
    }

    suspend fun loadHistoryDay(date: LocalDate) {
        mutableState.value = mutableState.value.copy(historyDayLoading = true, historyDayDetail = null, error = null, retentionCutoffDate = null, retentionDays = null)
        runCatching { api.historyDay(date.toString()) }
            .onSuccess { mutableState.value = mutableState.value.copy(historyDayDetail = it, historyDayLoading = false) }
            .onFailure { error ->
                val retention = error as? ApiException.HistoryRetentionLimit
                mutableState.value = mutableState.value.copy(
                    historyDayLoading = false,
                    error = if (retention == null) error.message else null,
                    retentionCutoffDate = retention?.cutoffDate,
                    retentionDays = retention?.retentionDays,
                )
            }
    }

    fun clearHistoryDay() {
        mutableState.value = mutableState.value.copy(historyDayDetail = null, historyDayLoading = false, error = null)
    }

    fun handleNotificationTarget(date: String?, slot: String?): Boolean {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        val parsedSlot = runCatching { MedicationSlot.valueOf(slot.orEmpty().uppercase()) }.getOrNull() ?: return false
        mutableState.value = mutableState.value.copy(notificationTarget = PatientNotificationTarget(parsedDate, parsedSlot))
        return true
    }

    fun consumeNotificationTarget() {
        mutableState.value = mutableState.value.copy(notificationTarget = null)
    }

    suspend fun notificationHistory(now: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))): Pair<List<HistoryDay>, PatientSlotTimes> {
        val slotTimes = runCatching { api.slotTimes() }.getOrElse { mutableState.value.slotTimes }
        val end = now.plusDays(6)
        val first = api.history(now.year, now.monthValue)
        val days = if (end.monthValue == now.monthValue && end.year == now.year) first else first + api.history(end.year, end.monthValue)
        return days to slotTimes
    }

    suspend fun revokeSession(): Boolean {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        return runCatching { api.revokeSession() }
            .fold(
                onSuccess = {
                    mutableState.value = mutableState.value.copy(loading = false)
                    true
                },
                onFailure = {
                    mutableState.value = mutableState.value.copy(loading = false, error = it.message)
                    false
                },
            )
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(error = null, message = null)
    }
}

private val DoseStatus.sortRank: Int get() = when (this) {
    DoseStatus.PENDING -> 0
    DoseStatus.MISSED -> 1
    DoseStatus.TAKEN -> 2
}
