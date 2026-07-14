package com.afterlifearchive.medmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsServiceTest {
    @Test
    fun defaultsOffAndLogsOnlyAfterExplicitConsent() {
        val store = FakeConsentStore()
        val transport = FakeAnalyticsTransport()
        val service = AnalyticsService(store, transport)

        service.configure()
        service.logModeSelected(AnalyticsAppMode.PATIENT)
        assertEquals(listOf(false), transport.collectionChanges)
        assertTrue(transport.events.isEmpty())

        service.setCollectionEnabled(true)
        service.logModeSelected(AnalyticsAppMode.PATIENT)
        assertTrue(store.state.decided)
        assertEquals("app_mode_selected", transport.events.single().first)
        assertEquals(mapOf("mode" to "patient"), transport.events.single().second)
    }

    @Test
    fun disablingStopsCollectionAndResetsDeviceAnalyticsData() {
        val store = FakeConsentStore(AnalyticsConsentState(enabled = true, decided = true))
        val transport = FakeAnalyticsTransport()
        val service = AnalyticsService(store, transport)
        service.configure()

        service.setCollectionEnabled(false)
        service.logScreenViewed(AnalyticsScreen.MODE_SELECT)

        assertEquals(listOf(true, false), transport.collectionChanges)
        assertEquals(1, transport.resetCount)
        assertTrue(transport.events.isEmpty())
    }

    @Test
    fun suppressedEnvironmentNeverCollectsEvenWithPersistedConsent() {
        val transport = FakeAnalyticsTransport()
        val service = AnalyticsService(
            FakeConsentStore(AnalyticsConsentState(enabled = true, decided = true)),
            transport,
            environmentSuppressed = { true },
        )

        service.configure()
        service.logPatientTabViewed(AnalyticsPatientTab.HISTORY)

        assertEquals(listOf(false), transport.collectionChanges)
        assertTrue(transport.events.isEmpty())
    }

    @Test
    fun wrapperBoundaryRejectsUnknownEventsKeysFreeTextAndOutOfRangeSteps() {
        val transport = FakeAnalyticsTransport()
        val service = AnalyticsService(FakeConsentStore(AnalyticsConsentState(true, true)), transport)
        service.configure()

        service.logChecked("screen_viewed", mapOf("patient_id" to "secret"))
        service.logChecked("screen_viewed", mapOf("screen_name" to "free text"))
        service.logChecked("dose_with_medication", mapOf("medication" to "secret"))
        service.logChecked("tutorial_step_viewed", mapOf("mode" to "patient", "step" to "21"))

        assertTrue(transport.events.isEmpty())
        service.logTutorialStepViewed(AnalyticsAppMode.CAREGIVER, 20)
        assertEquals(1, transport.events.size)
    }

    private class FakeConsentStore(initial: AnalyticsConsentState = AnalyticsConsentState()) : AnalyticsConsentStore {
        var state = initial
        override fun state() = state
        override fun save(enabled: Boolean) { state = AnalyticsConsentState(enabled, decided = true) }
    }

    private class FakeAnalyticsTransport : AnalyticsTransport {
        val collectionChanges = mutableListOf<Boolean>()
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var resetCount = 0
        override fun setCollectionEnabled(enabled: Boolean) { collectionChanges += enabled }
        override fun reset() { resetCount += 1 }
        override fun log(name: String, parameters: Map<String, String>) { events += name to parameters }
    }
}
