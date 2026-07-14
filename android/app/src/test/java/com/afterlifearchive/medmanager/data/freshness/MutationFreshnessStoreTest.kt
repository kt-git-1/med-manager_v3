package com.afterlifearchive.medmanager.data.freshness

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MutationFreshnessStoreTest {
    @Test
    fun revisionsAdvanceOnlyForChangedDomains() {
        val store = MutationFreshnessStore()

        store.markDoseChanged()
        store.markMedicationChanged(inventoryChanged = false, notificationPlanChanged = false)
        store.markInventoryChanged()

        assertEquals(MutationRevisions(dose = 1, medication = 1, inventory = 2), store.revisions.value)
    }

    @Test
    fun slotTimeChangeDoesNotInventDoseOrMedicationMutation() {
        val store = MutationFreshnessStore()

        store.markSlotTimesChanged()

        assertEquals(1L, store.revisions.value.slotTimes)
        assertEquals(0L, store.revisions.value.dose)
        assertEquals(0L, store.revisions.value.medication)
    }

    @Test
    fun scheduledDoseRevisionIsSeparateFromPrnDoseRevision() {
        val store = MutationFreshnessStore()

        store.markDoseChanged()
        assertEquals(0, store.revisions.value.notificationPlan)

        store.markScheduledDoseChanged()
        assertEquals(2, store.revisions.value.dose)
        assertEquals(1, store.revisions.value.notificationPlan)
    }

    @Test
    fun firstVisitAndProcessRecreatedCursorsRefreshAuthoritativeData() = runTest {
        val store = MutationFreshnessStore()
        val firstCursor = store.newCursor(FreshnessConsumer.PATIENT_HISTORY)
        assertTrue(firstCursor.refreshIfStale {})
        assertFalse(firstCursor.refreshIfStale {})

        val processRecreatedCursor = store.newCursor(FreshnessConsumer.PATIENT_HISTORY)
        assertTrue(processRecreatedCursor.refreshIfStale {})
    }

    @Test
    fun mutationBeforeDestinationExistsIsConsumedOnFirstVisit() = runTest {
        val store = MutationFreshnessStore()
        store.markDoseChanged()

        val historyCreatedLater = store.newCursor(FreshnessConsumer.PATIENT_HISTORY)

        assertTrue(historyCreatedLater.refreshIfStale {})
        assertFalse(historyCreatedLater.refreshIfStale {})
    }

    @Test
    fun hiddenConsumerKeepsRevisionPendingUntilNextVisibleRefresh() = runTest {
        val store = MutationFreshnessStore()
        val cursor = store.newCursor(FreshnessConsumer.PATIENT_HISTORY)
        cursor.refreshIfStale {}

        store.markDoseChanged()

        assertTrue(cursor.refreshIfStale {})
        assertFalse(cursor.refreshIfStale {})
    }

    @Test
    fun concurrentCollectorsDoNotDuplicateRefreshForSameRevision() = runTest {
        val store = MutationFreshnessStore()
        val cursor = store.newCursor(FreshnessConsumer.CAREGIVER_INVENTORY)
        var refreshCount = 0

        val results = List(8) {
            async {
                cursor.refreshIfStale {
                    refreshCount += 1
                    delay(10)
                }
            }
        }.awaitAll()

        assertEquals(1, refreshCount)
        assertEquals(1, results.count { it })
    }

    @Test
    fun mutationPublishedDuringRefreshRemainsPending() = runTest {
        val store = MutationFreshnessStore()
        val cursor = store.newCursor(FreshnessConsumer.PATIENT_HISTORY)

        assertTrue(cursor.refreshIfStale { store.markDoseChanged() })
        assertTrue(cursor.refreshIfStale {})
        assertFalse(cursor.refreshIfStale {})
    }

    @Test
    fun failedRefreshDoesNotConsumeRevision() = runTest {
        val store = MutationFreshnessStore()
        val cursor = store.newCursor(FreshnessConsumer.PATIENT_HISTORY)

        try {
            cursor.refreshIfStale { error("temporary") }
            fail("refresh should fail")
        } catch (_: IllegalStateException) {
            // The same revision must remain pending for a later retry.
        }

        assertTrue(cursor.refreshIfStale {})
        assertFalse(cursor.refreshIfStale {})
    }

    @Test
    fun consumerMappingsRefreshOnlyForRelevantDomains() = runTest {
        val history = MutationFreshnessStore().let { store ->
            store.newCursor(FreshnessConsumer.CAREGIVER_HISTORY).also { it.refreshIfStale {} } to store
        }
        history.second.markInventoryChanged()
        assertFalse(history.first.refreshIfStale {})
        history.second.markDoseChanged(inventoryChanged = false)
        assertTrue(history.first.refreshIfStale {})
    }

    @Test
    fun medicationMutationInvalidatesEveryMedicationDependentFeatureButNotHistory() = runTest {
        val store = MutationFreshnessStore()
        val affected = listOf(
            FreshnessConsumer.PATIENT_TODAY,
            FreshnessConsumer.CAREGIVER_TODAY,
            FreshnessConsumer.CAREGIVER_MEDICATIONS,
            FreshnessConsumer.CAREGIVER_INVENTORY,
            FreshnessConsumer.NOTIFICATION_SCHEDULER,
        ).associateWith { store.newCursor(it) }
        val unaffected = listOf(
            store.newCursor(FreshnessConsumer.PATIENT_HISTORY),
            store.newCursor(FreshnessConsumer.CAREGIVER_HISTORY),
        )
        (affected.values + unaffected).forEach { it.refreshIfStale {} }

        store.markMedicationChanged(inventoryChanged = true, notificationPlanChanged = true)

        affected.forEach { (consumer, cursor) ->
            assertTrue("$consumer must refresh after a medication mutation", cursor.refreshIfStale {})
            assertFalse(cursor.refreshIfStale {})
        }
        unaffected.forEach { assertFalse(it.refreshIfStale {}) }
    }
}
