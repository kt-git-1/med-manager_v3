package com.afterlifearchive.medmanager.data.freshness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MutationDomain {
    DOSE,
    MEDICATION,
    INVENTORY,
    NOTIFICATION_PLAN,
    SLOT_TIMES,
}

enum class FreshnessConsumer(val domains: Set<MutationDomain>) {
    PATIENT_TODAY(setOf(MutationDomain.DOSE, MutationDomain.MEDICATION, MutationDomain.INVENTORY, MutationDomain.SLOT_TIMES)),
    PATIENT_HISTORY(setOf(MutationDomain.DOSE, MutationDomain.SLOT_TIMES)),
    CAREGIVER_TODAY(setOf(MutationDomain.DOSE, MutationDomain.MEDICATION, MutationDomain.INVENTORY, MutationDomain.SLOT_TIMES)),
    CAREGIVER_MEDICATIONS(setOf(MutationDomain.MEDICATION, MutationDomain.INVENTORY, MutationDomain.SLOT_TIMES)),
    CAREGIVER_HISTORY(setOf(MutationDomain.DOSE, MutationDomain.SLOT_TIMES)),
    CAREGIVER_INVENTORY(setOf(MutationDomain.DOSE, MutationDomain.MEDICATION, MutationDomain.INVENTORY)),
    NOTIFICATION_SCHEDULER(setOf(MutationDomain.NOTIFICATION_PLAN, MutationDomain.SLOT_TIMES)),
}

data class MutationRevisions(
    val dose: Long = 0,
    val medication: Long = 0,
    val inventory: Long = 0,
    val notificationPlan: Long = 0,
    val slotTimes: Long = 0,
) {
    internal fun advanced(domains: Set<MutationDomain>): MutationRevisions = copy(
        dose = if (MutationDomain.DOSE in domains) dose + 1 else dose,
        medication = if (MutationDomain.MEDICATION in domains) medication + 1 else medication,
        inventory = if (MutationDomain.INVENTORY in domains) inventory + 1 else inventory,
        notificationPlan = if (MutationDomain.NOTIFICATION_PLAN in domains) notificationPlan + 1 else notificationPlan,
        slotTimes = if (MutationDomain.SLOT_TIMES in domains) slotTimes + 1 else slotTimes,
    )

    internal fun isNewerThan(other: MutationRevisions, domains: Set<MutationDomain>): Boolean =
        (MutationDomain.DOSE in domains && dose > other.dose) ||
            (MutationDomain.MEDICATION in domains && medication > other.medication) ||
            (MutationDomain.INVENTORY in domains && inventory > other.inventory) ||
            (MutationDomain.NOTIFICATION_PLAN in domains && notificationPlan > other.notificationPlan) ||
            (MutationDomain.SLOT_TIMES in domains && slotTimes > other.slotTimes)
}

class MutationFreshnessStore {
    private val mutableRevisions = MutableStateFlow(MutationRevisions())
    val revisions: StateFlow<MutationRevisions> = mutableRevisions.asStateFlow()

    fun markChanged(vararg domains: MutationDomain) {
        require(domains.isNotEmpty())
        val changed = domains.toSet()
        mutableRevisions.update { it.advanced(changed) }
    }

    fun markDoseChanged(inventoryChanged: Boolean = true) {
        if (inventoryChanged) markChanged(MutationDomain.DOSE, MutationDomain.INVENTORY)
        else markChanged(MutationDomain.DOSE)
    }

    fun markScheduledDoseChanged(inventoryChanged: Boolean = true) {
        if (inventoryChanged) {
            markChanged(MutationDomain.DOSE, MutationDomain.INVENTORY, MutationDomain.NOTIFICATION_PLAN)
        } else {
            markChanged(MutationDomain.DOSE, MutationDomain.NOTIFICATION_PLAN)
        }
    }

    fun markMedicationChanged(inventoryChanged: Boolean = true, notificationPlanChanged: Boolean = true) {
        val domains = buildList {
            add(MutationDomain.MEDICATION)
            if (inventoryChanged) add(MutationDomain.INVENTORY)
            if (notificationPlanChanged) add(MutationDomain.NOTIFICATION_PLAN)
        }
        markChanged(*domains.toTypedArray())
    }

    fun markInventoryChanged() = markChanged(MutationDomain.INVENTORY)

    fun markSlotTimesChanged() = markChanged(MutationDomain.SLOT_TIMES)

    fun newCursor(consumer: FreshnessConsumer): FreshnessCursor = FreshnessCursor(this, consumer.domains)
}

class FreshnessCursor internal constructor(
    private val store: MutationFreshnessStore,
    private val domains: Set<MutationDomain>,
) {
    private val refreshMutex = Mutex()
    private var consumed: MutationRevisions? = null

    /**
     * Runs at most one refresh for a revision snapshot. A newly created cursor is intentionally
     * stale so first-visit and process-recreated screens always fetch authoritative data.
     * A revision emitted during refresh remains pending for the next call.
     */
    suspend fun refreshIfStale(refresh: suspend () -> Unit): Boolean = refreshMutex.withLock {
        val target = store.revisions.value
        val previous = consumed
        if (previous != null && !target.isNewerThan(previous, domains)) return@withLock false
        refresh()
        consumed = target
        true
    }
}
