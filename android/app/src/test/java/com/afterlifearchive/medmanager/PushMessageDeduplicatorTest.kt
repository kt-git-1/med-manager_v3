package com.afterlifearchive.medmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushMessageDeduplicatorTest {
    @Test
    fun sameFcmMessageIsDisplayedOnlyOnceAcrossInstances() {
        val storage = MemoryMessageIdStorage()

        assertTrue(PushMessageDeduplicator(storage).shouldDisplay("message-1"))
        assertFalse(PushMessageDeduplicator(storage).shouldDisplay("message-1"))
        assertTrue(PushMessageDeduplicator(storage).shouldDisplay("message-2"))
    }

    @Test
    fun boundedHistoryRetainsOnlyNewestIdsAndMissingIdRemainsDeliverable() {
        val storage = MemoryMessageIdStorage()
        val deduplicator = PushMessageDeduplicator(storage, capacity = 2)

        assertTrue(deduplicator.shouldDisplay(null))
        assertTrue(deduplicator.shouldDisplay("a"))
        assertTrue(deduplicator.shouldDisplay("b"))
        assertTrue(deduplicator.shouldDisplay("c"))
        assertEquals(listOf("b", "c"), storage.ids)
        assertTrue(deduplicator.shouldDisplay("a"))
    }

    private class MemoryMessageIdStorage : PushMessageIdStorage {
        var ids = emptyList<String>()
        override fun read() = ids
        override fun write(ids: List<String>) { this.ids = ids }
    }
}
