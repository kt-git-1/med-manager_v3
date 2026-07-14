package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpRequest
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.network.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverInventoryRepositoryTest {
    @Test
    fun apiUsesExactListUpdateAndAdjustmentContracts() = runTest {
        val requests = mutableListOf<HttpRequest>()
        var quantity = 2.0
        val client = ApiClient(
            baseUrl = "https://example.test/",
            caregiverTokenProvider = { "caregiver-token" },
            transport = HttpTransport { request ->
                requests += request
                if (request.method == "GET") HttpResponse(200, """{"data":{"patientId":"p1","medications":[${itemJson(quantity)}]}}""")
                else {
                    quantity = if (request.method == "PATCH") quantity else 9.0
                    HttpResponse(200, """{"data":${itemJson(quantity)}}""")
                }
            },
        )
        val api = CaregiverInventoryApi(client)

        assertEquals("薬A", api.list("p1").single().name)
        api.update("p1", "med-1", true, null)
        api.adjust("p1", "med-1", "REFILL", 7.0, null)

        assertEquals(listOf("GET", "PATCH", "POST"), requests.map { it.method })
        assertEquals("https://example.test/api/patients/p1/inventory", requests[0].url)
        assertEquals("https://example.test/api/patients/p1/medications/med-1/inventory", requests[1].url)
        assertEquals("{\"inventoryEnabled\":true,\"inventoryQuantity\":null}", requests[1].body)
        assertEquals("https://example.test/api/patients/p1/medications/med-1/inventory/adjust", requests[2].url)
        assertEquals("{\"reason\":\"REFILL\",\"delta\":7.0,\"absoluteQuantity\":null}", requests[2].body)
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer caregiver-token" })
    }

    @Test
    fun mutationsReplaceAuthoritativeItemAndPublishInventoryOnly() = runTest {
        val freshness = MutationFreshnessStore()
        var authoritative = item(quantity = 2.0, enabled = false)
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = listOf(authoritative)
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?): CaregiverInventoryItem {
                authoritative = authoritative.copy(inventoryEnabled = enabled)
                return authoritative
            }
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?): CaregiverInventoryItem {
                authoritative = authoritative.copy(inventoryQuantity = absoluteQuantity ?: authoritative.inventoryQuantity + (delta ?: 0.0))
                return authoritative
            }
        }
        val repository = CaregiverInventoryRepository(source, freshness)
        repository.load("p1")

        assertTrue(repository.updateSettings("p1", authoritative, true))
        assertTrue(repository.refill("p1", repository.state.value.items.single(), 7.0))
        assertTrue(repository.correct("p1", repository.state.value.items.single(), 4.0))

        assertTrue(repository.state.value.items.single().inventoryEnabled)
        assertEquals(4.0, repository.state.value.items.single().inventoryQuantity, 0.0)
        assertEquals(CaregiverInventoryMutationMessage.CORRECTED, repository.state.value.mutationMessage)
        assertEquals(3L, freshness.revisions.value.inventory)
        assertEquals(0L, freshness.revisions.value.dose)
        assertEquals(0L, freshness.revisions.value.medication)
    }

    @Test
    fun invalidOrFailedMutationPreservesPreviousQuantityAndRevision() = runTest {
        val freshness = MutationFreshnessStore()
        val original = item(quantity = 3.0)
        val source = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = listOf(original)
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = error("offline")
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = error("offline")
        }
        val repository = CaregiverInventoryRepository(source, freshness)
        repository.load("p1")

        assertFalse(repository.refill("p1", original, -1.0))
        assertFalse(repository.correct("p1", original, -1.0))
        assertFalse(repository.refill("p1", original, 5.0))

        assertEquals(3.0, repository.state.value.items.single().inventoryQuantity, 0.0)
        assertTrue(repository.state.value.mutationFailed)
        assertEquals(0L, freshness.revisions.value.inventory)
    }

    private fun item(quantity: Double, enabled: Boolean = true) = CaregiverInventoryItem(
        medicationId = "med-1", name = "薬A", isPrn = false, doseCountPerIntake = 1.0,
        inventoryEnabled = enabled, inventoryQuantity = quantity, inventoryLowThreshold = 3,
        periodEnded = false, low = quantity <= 3, out = quantity <= 0, dailyPlannedUnits = 1.0,
        nextSevenDaysPlannedUnits = 7.0, nextFourteenDaysPlannedUnits = 14.0,
        nextTwentyOneDaysPlannedUnits = 21.0, daysRemaining = quantity.toInt(), refillDueDate = "2026-07-18",
    )

    private fun itemJson(quantity: Double) = """{"medicationId":"med-1","name":"薬A","isPrn":false,"doseCountPerIntake":1.0,"inventoryEnabled":true,"inventoryQuantity":$quantity,"inventoryLowThreshold":3,"periodEnded":false,"low":true,"out":false,"dailyPlannedUnits":1.0,"nextSevenDaysPlannedUnits":7.0,"nextFourteenDaysPlannedUnits":14.0,"nextTwentyOneDaysPlannedUnits":21.0,"daysRemaining":2,"refillDueDate":"2026-07-18"}"""
}
