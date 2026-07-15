package com.afterlifearchive.medmanager.data.patient

import com.afterlifearchive.medmanager.data.freshness.FreshnessConsumer
import com.afterlifearchive.medmanager.data.freshness.FreshnessCursor
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.freshness.MutationRevisions
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
    val historyStreak: PatientHistoryStreak? = null,
    val historyYear: Int? = null,
    val historyMonth: Int? = null,
    val historyDayDetail: HistoryDayDetail? = null,
    val historyDayLoading: Boolean = false,
    val historyDayError: PatientUserMessage? = null,
    val historyDayRetentionCutoffDate: String? = null,
    val historyDayRetentionDays: Int? = null,
    val detailMedicationId: String? = null,
    val detailMedication: PatientMedication? = null,
    val detailLoading: Boolean = false,
    val detailError: Boolean = false,
    val retentionCutoffDate: String? = null,
    val retentionDays: Int? = null,
    val notificationTarget: PatientNotificationTarget? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val updatingDoseKey: String? = null,
    val updatingSlot: MedicationSlot? = null,
    val updatingPrnMedicationId: String? = null,
    val prnError: PatientUserMessage? = null,
    val prnRecordSuccessRevision: Long = 0,
    val error: PatientUserMessage? = null,
    val message: PatientUserMessage? = null,
    val maintenanceWarning: PatientMaintenanceWarning? = null,
) {
    val medicationById: Map<String, PatientMedication> get() = medications.associateBy(PatientMedication::id)
    val prnMedications: List<PatientMedication> get() = medications.filter { it.isPrn && it.isActive && !it.isArchived }
    val insufficientMedicationIds: Set<String> get() = medications.filter(PatientMedication::isInsufficientForDose).mapTo(mutableSetOf(), PatientMedication::id)
}

enum class PatientMaintenanceWarning {
    REMINDER_REFRESH_FAILED,
    TODAY_REFRESH_FAILED,
}

class PatientRepository(
    private val api: PatientDataSource,
    private val freshnessStore: MutationFreshnessStore = MutationFreshnessStore(),
) {
    private val mutableState = MutableStateFlow(PatientUiState())
    val state: StateFlow<PatientUiState> = mutableState.asStateFlow()
    val freshness: StateFlow<MutationRevisions> = freshnessStore.revisions

    fun newFreshnessCursor(consumer: FreshnessConsumer): FreshnessCursor = freshnessStore.newCursor(consumer)

    suspend fun loadToday() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        val hasRenderedContent = mutableState.value.doses.isNotEmpty() || mutableState.value.medications.isNotEmpty()
        mutableState.value = mutableState.value.copy(
            loading = !hasRenderedContent,
            refreshing = hasRenderedContent,
            error = null,
            message = null,
            maintenanceWarning = null,
        )
        runCatching {
            val slotTimes = runCatching { api.slotTimes() }.getOrElse { mutableState.value.slotTimes }
            val medications = api.medications()
            val doses = api.today()
                .map { it.copy(slot = slotTimes.resolve(it.scheduledAt)) }
                .sortedWith(compareBy<PatientDose>({ it.status.sortRank }, { it.scheduledAt }))
            Triple(slotTimes, medications, doses)
        }
            .onSuccess { (slotTimes, medications, doses) ->
                mutableState.value = mutableState.value.copy(
                    slotTimes = slotTimes,
                    medications = medications,
                    doses = doses,
                    loading = false,
                    refreshing = false,
                )
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    refreshing = false,
                    error = PatientUserMessage.TodayLoadFailed,
                )
            }
    }

    suspend fun refreshTodayAfterAction() {
        val previousMessage = mutableState.value.message
        mutableState.value = mutableState.value.copy(refreshing = true, maintenanceWarning = null)
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
                refreshing = false,
                message = previousMessage,
            )
        }.onFailure {
            mutableState.value = mutableState.value.copy(
                refreshing = false,
                message = previousMessage,
                maintenanceWarning = PatientMaintenanceWarning.TODAY_REFRESH_FAILED,
            )
        }
    }

    suspend fun loadDoseDetail(medicationId: String) {
        if (mutableState.value.detailMedicationId == medicationId && mutableState.value.detailLoading) return
        val cached = mutableState.value.medicationById[medicationId]
        if (cached != null) {
            mutableState.value = mutableState.value.copy(
                detailMedicationId = medicationId,
                detailMedication = cached,
                detailLoading = false,
                detailError = false,
            )
            return
        }

        mutableState.value = mutableState.value.copy(
            detailMedicationId = medicationId,
            detailMedication = null,
            detailLoading = true,
            detailError = false,
        )
        runCatching { api.medications() }
            .onSuccess { medications ->
                if (mutableState.value.detailMedicationId == medicationId) {
                    mutableState.value = mutableState.value.copy(
                        medications = medications,
                        detailMedication = medications.firstOrNull { it.id == medicationId },
                        detailLoading = false,
                        detailError = false,
                    )
                }
            }
            .onFailure {
                if (mutableState.value.detailMedicationId == medicationId) {
                    mutableState.value = mutableState.value.copy(
                        detailMedication = null,
                        detailLoading = false,
                        detailError = true,
                    )
                }
            }
    }

    fun clearDoseDetail() {
        mutableState.value = mutableState.value.copy(
            detailMedicationId = null,
            detailMedication = null,
            detailLoading = false,
            detailError = false,
        )
    }

    suspend fun record(dose: PatientDose) {
        if (dose.status == DoseStatus.TAKEN) return
        if (mutableState.value.insufficientMedicationIds.contains(dose.medicationId)) {
            mutableState.value = mutableState.value.copy(error = PatientUserMessage.InventoryInsufficient)
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
                    message = PatientUserMessage.DoseRecorded,
                )
                freshnessStore.markScheduledDoseChanged()
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(updatingDoseKey = null, error = it.toPatientUserMessage())
            }
    }

    suspend fun recordSlot(slot: MedicationSlot, date: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))) {
        if (mutableState.value.updatingSlot != null) return
        mutableState.value = mutableState.value.copy(updatingSlot = slot, error = null, message = null)
        runCatching { api.recordSlot(date.toString(), slot) }
            .onSuccess { result ->
                val message = when {
                    result.updatedCount > 0 && result.insufficientCount > 0 -> PatientUserMessage.SlotPartial(result.updatedCount, result.insufficientCount)
                    result.insufficientCount > 0 -> PatientUserMessage.InventoryInsufficient
                    result.updatedCount > 0 -> PatientUserMessage.SlotRecorded(result.updatedCount)
                    else -> PatientUserMessage.NoRecordableMedication
                }
                val updated = mutableState.value.doses.map { dose ->
                    if (result.updatedCount > 0 && dose.slot == slot && dose.status != DoseStatus.TAKEN && !mutableState.value.insufficientMedicationIds.contains(dose.medicationId)) {
                        dose.copy(status = DoseStatus.TAKEN)
                    } else dose
                }
                mutableState.value = mutableState.value.copy(doses = updated, updatingSlot = null, message = message)
                if (result.updatedCount > 0) freshnessStore.markScheduledDoseChanged()
            }
            .onFailure { error -> mutableState.value = mutableState.value.copy(updatingSlot = null, error = error.toPatientUserMessage()) }
    }

    suspend fun recordPrn(medication: PatientMedication): Boolean {
        if (!medication.isPrn || mutableState.value.updatingPrnMedicationId != null) return false
        if (medication.isInsufficientForDose) {
            mutableState.value = mutableState.value.copy(prnError = PatientUserMessage.InventoryInsufficient)
            return false
        }
        mutableState.value = mutableState.value.copy(updatingPrnMedicationId = medication.id, prnError = null, message = null)
        return runCatching { api.recordPrn(medication) }
            .fold(
                onSuccess = {
                    mutableState.value = mutableState.value.copy(
                        updatingPrnMedicationId = null,
                        message = PatientUserMessage.PrnRecorded,
                        prnRecordSuccessRevision = mutableState.value.prnRecordSuccessRevision + 1,
                    )
                    freshnessStore.markDoseChanged()
                    true
                },
                onFailure = { error ->
                    val mapped = error.toPatientUserMessage()
                    mutableState.value = mutableState.value.copy(
                        updatingPrnMedicationId = null,
                        prnError = if (mapped == PatientUserMessage.InsufficientInventory) {
                            mapped
                        } else {
                            PatientUserMessage.PrnRecordFailed
                        },
                    )
                    false
                },
            )
    }

    fun clearPrnFeedback() {
        mutableState.value = mutableState.value.copy(prnError = null)
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
                    error = if (retention == null) error.toPatientUserMessage() else null,
                    retentionCutoffDate = retention?.cutoffDate,
                    retentionDays = retention?.retentionDays,
                )
            }
        loadHistoryStreak()
    }

    private suspend fun loadHistoryStreak() {
        runCatching { api.historyStreak() }
            .onSuccess { streak -> mutableState.value = mutableState.value.copy(historyStreak = streak) }
            .onFailure { mutableState.value = mutableState.value.copy(historyStreak = null) }
    }

    suspend fun loadHistoryDay(date: LocalDate) {
        mutableState.value = mutableState.value.copy(
            historyDayLoading = true,
            historyDayDetail = null,
            historyDayError = null,
            historyDayRetentionCutoffDate = null,
            historyDayRetentionDays = null,
        )
        runCatching { api.historyDay(date.toString()) }
            .onSuccess { mutableState.value = mutableState.value.copy(historyDayDetail = it, historyDayLoading = false) }
            .onFailure { error ->
                val retention = error as? ApiException.HistoryRetentionLimit
                mutableState.value = mutableState.value.copy(
                    historyDayLoading = false,
                    historyDayError = if (retention == null) error.toPatientUserMessage() else null,
                    historyDayRetentionCutoffDate = retention?.cutoffDate,
                    historyDayRetentionDays = retention?.retentionDays,
                )
            }
    }

    fun clearHistoryDay() {
        mutableState.value = mutableState.value.copy(
            historyDayDetail = null,
            historyDayLoading = false,
            historyDayError = null,
            historyDayRetentionCutoffDate = null,
            historyDayRetentionDays = null,
        )
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
                    mutableState.value = mutableState.value.copy(loading = false, error = it.toPatientUserMessage())
                    false
                },
            )
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(error = null, message = null, maintenanceWarning = null)
    }

    fun reportReminderMaintenanceFailure() {
        mutableState.value = mutableState.value.copy(
            maintenanceWarning = PatientMaintenanceWarning.REMINDER_REFRESH_FAILED,
        )
    }

    fun clearReminderMaintenanceWarning() {
        mutableState.value = mutableState.value.copy(maintenanceWarning = null)
    }
}

private fun Throwable.toPatientUserMessage(): PatientUserMessage? = when (this) {
    is ApiException.Validation -> PatientUserMessage.Validation(safeMessage)
    is ApiException.Unauthorized -> PatientUserMessage.Unauthorized
    is ApiException.Forbidden -> PatientUserMessage.Forbidden
    is ApiException.NotFound -> PatientUserMessage.NotFound
    is ApiException.Conflict -> PatientUserMessage.Conflict(safeMessage)
    is ApiException.InsufficientInventory -> PatientUserMessage.InsufficientInventory
    is ApiException.PatientLimitExceeded -> PatientUserMessage.PatientLimit(limit)
    is ApiException.RateLimited -> PatientUserMessage.RateLimited
    is ApiException.Network -> PatientUserMessage.Network
    is ApiException.Server -> PatientUserMessage.Server
    is ApiException.HistoryRetentionLimit -> null
    else -> message?.let(PatientUserMessage::Raw)
}

private val DoseStatus.sortRank: Int get() = when (this) {
    DoseStatus.PENDING -> 0
    DoseStatus.MISSED -> 1
    DoseStatus.TAKEN -> 2
}
