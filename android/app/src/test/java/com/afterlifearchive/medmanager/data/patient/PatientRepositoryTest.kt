package com.afterlifearchive.medmanager.data.patient

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PatientRepositoryTest {
    @Test
    fun loadingTodayPublishesDoses() = runTest {
        val source = FakePatientDataSource()
        val repository = PatientRepository(source)

        repository.loadToday()

        assertEquals(1, repository.state.value.doses.size)
        assertEquals(MedicationSlot.MORNING, repository.state.value.doses.single().slot)
        assertEquals("07:30", repository.state.value.slotTimes.morning)
        assertFalse(repository.state.value.loading)
        assertNull(repository.state.value.error)
    }

    @Test
    fun recordingDoseUpdatesVisibleStatusWithoutWaitingForReload() = runTest {
        val source = FakePatientDataSource()
        val repository = PatientRepository(source)
        repository.loadToday()

        repository.record(repository.state.value.doses.single())

        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.single().status)
        assertEquals("服薬を記録しました。", repository.state.value.message)
        assertEquals(1, source.recordCount)
    }

    @Test
    fun todayStillLoadsWithDefaultsWhenSlotTimeSyncFails() = runTest {
        val source = object : PatientDataSource by FakePatientDataSource() {
            override suspend fun slotTimes(): PatientSlotTimes = error("slot endpoint unavailable")
        }
        val repository = PatientRepository(source)

        repository.loadToday()

        assertEquals(1, repository.state.value.doses.size)
        assertEquals(PatientSlotTimes.DEFAULT, repository.state.value.slotTimes)
        assertEquals(MedicationSlot.MORNING, repository.state.value.doses.single().slot)
        assertNull(repository.state.value.error)
    }

    @Test
    fun individualRecordIsBlockedLocallyWhenInventoryIsInsufficient() = runTest {
        val source = InventoryPatientDataSource()
        val repository = PatientRepository(source)
        repository.loadToday()

        repository.record(repository.state.value.doses.single())

        assertEquals(0, source.recordCount)
        assertEquals(DoseStatus.PENDING, repository.state.value.doses.single().status)
        assertEquals("在庫が不足しているため記録できません。", repository.state.value.error)
    }

    @Test
    fun partialBulkSuccessOnlyMarksRecordableMedicationTaken() = runTest {
        val source = PartialBulkPatientDataSource()
        val repository = PatientRepository(source)
        repository.loadToday()

        repository.recordSlot(MedicationSlot.MORNING, java.time.LocalDate.parse("2026-07-13"))

        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.first { it.medicationId == "enough" }.status)
        assertEquals(DoseStatus.PENDING, repository.state.value.doses.first { it.medicationId == "short" }.status)
        assertEquals("1件を記録しました。在庫不足の1件は記録されていません。", repository.state.value.message)
        assertNull(repository.state.value.updatingSlot)
    }

    @Test
    fun prnRecordPublishesSuccessAndClearsSubmittingState() = runTest {
        val source = PrnPatientDataSource()
        val repository = PatientRepository(source)
        repository.loadToday()
        val medication = repository.state.value.prnMedications.single()

        repository.recordPrn(medication)

        assertEquals(1, source.prnRecordCount)
        assertEquals("頓服の服用を記録しました。", repository.state.value.message)
        assertNull(repository.state.value.updatingPrnMedicationId)
    }

    @Test
    fun postActionRefreshUsesServerTruthAndPreservesSuccessMessage() = runTest {
        val source = RefreshingPatientDataSource()
        val repository = PatientRepository(source)
        repository.loadToday()
        repository.record(repository.state.value.doses.single())

        repository.refreshTodayAfterAction()

        assertEquals(2, source.todayCount)
        assertEquals(DoseStatus.TAKEN, repository.state.value.doses.single().status)
        assertEquals("服薬を記録しました。", repository.state.value.message)
    }

    @Test
    fun historyRetentionPublishesLockMetadataWithoutGenericError() = runTest {
        val source = object : PatientDataSource by FakePatientDataSource() {
            override suspend fun history(year: Int, month: Int): List<HistoryDay> =
                throw com.afterlifearchive.medmanager.data.network.ApiException.HistoryRetentionLimit("2026-06-14", 30)
        }
        val repository = PatientRepository(source)

        repository.loadHistory(java.time.LocalDate.parse("2026-05-01"))

        assertEquals("2026-06-14", repository.state.value.retentionCutoffDate)
        assertEquals(30, repository.state.value.retentionDays)
        assertNull(repository.state.value.error)
    }

    @Test
    fun dayHistoryPublishesScheduledAndPrnDetails() = runTest {
        val detail = HistoryDayDetail(
            date = "2026-07-13",
            doses = listOf(HistoryScheduledDose("med", "薬", "1錠", 1.0, Instant.EPOCH, MedicationSlot.MORNING, DoseStatus.TAKEN, RecordedByType.PATIENT)),
            prnItems = listOf(PrnHistoryItem("prn", "頓服", Instant.EPOCH, 1.0, PrnActorType.CAREGIVER)),
        )
        val source = object : PatientDataSource by FakePatientDataSource() {
            override suspend fun historyDay(date: String) = detail
        }
        val repository = PatientRepository(source)

        repository.loadHistoryDay(java.time.LocalDate.parse("2026-07-13"))

        assertEquals(detail, repository.state.value.historyDayDetail)
        assertFalse(repository.state.value.historyDayLoading)
    }

    @Test
    fun revokeOnlyReportsSuccessAfterServerCompletes() = runTest {
        var revokeCount = 0
        val source = object : PatientDataSource by FakePatientDataSource() {
            override suspend fun revokeSession() { revokeCount += 1 }
        }
        val repository = PatientRepository(source)

        assertTrue(repository.revokeSession())
        assertEquals(1, revokeCount)
        assertFalse(repository.state.value.loading)
    }

    @Test
    fun revokeFailureKeepsSessionOwnerInControlAndPublishesError() = runTest {
        val source = object : PatientDataSource by FakePatientDataSource() {
            override suspend fun revokeSession(): Unit = error("解除に失敗しました")
        }
        val repository = PatientRepository(source)

        assertFalse(repository.revokeSession())
        assertEquals("解除に失敗しました", repository.state.value.error)
        assertFalse(repository.state.value.loading)
    }

    @Test
    fun notificationTargetAcceptsCanonicalDateAndSlotThenConsumesOnce() {
        val repository = PatientRepository(FakePatientDataSource())

        assertTrue(repository.handleNotificationTarget("2026-07-13", "evening"))
        assertEquals(java.time.LocalDate.parse("2026-07-13"), repository.state.value.notificationTarget?.date)
        assertEquals(MedicationSlot.EVENING, repository.state.value.notificationTarget?.slot)

        repository.consumeNotificationTarget()
        assertNull(repository.state.value.notificationTarget)
    }

    @Test
    fun notificationTargetRejectsMalformedPayloadWithoutChangingState() {
        val repository = PatientRepository(FakePatientDataSource())
        assertFalse(repository.handleNotificationTarget("13-07-2026", "breakfast"))
        assertNull(repository.state.value.notificationTarget)
    }
}

private class FakePatientDataSource : PatientDataSource {
    var recordCount = 0
    private val dose = PatientDose(
        key = "dose-1",
        medicationId = "med-1",
        scheduledAt = Instant.parse("2026-07-12T23:00:00Z"),
        status = DoseStatus.PENDING,
        medicationName = "テスト薬",
        dosageText = "1錠",
        doseCount = 1.0,
    )

    override suspend fun today() = listOf(dose)
    override suspend fun slotTimes() = PatientSlotTimes("07:30", "12:15", "18:45", "21:30")
    override suspend fun recordDose(dose: PatientDose) { recordCount += 1 }
    override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
    override suspend fun revokeSession() = Unit
}

private open class InventoryPatientDataSource : PatientDataSource {
    var recordCount = 0
    protected open val doses = listOf(testDose("short"))
    override suspend fun today() = doses
    override suspend fun slotTimes() = PatientSlotTimes.DEFAULT
    override suspend fun medications() = listOf(testMedication("short", 0.5))
    override suspend fun recordDose(dose: PatientDose) { recordCount += 1 }
    override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
    override suspend fun revokeSession() = Unit
}

private class PartialBulkPatientDataSource : InventoryPatientDataSource() {
    override val doses = listOf(testDose("enough"), testDose("short"))
    override suspend fun medications() = listOf(testMedication("enough", 10.0), testMedication("short", 0.5))
    override suspend fun recordSlot(date: String, slot: MedicationSlot) = SlotBulkRecordResult(
        updatedCount = 1, remainingCount = 1, insufficientCount = 1, totalPills = 2.0,
        medCount = 2, slotTime = "08:00", slotSummary = emptyMap(), recordingGroupId = "group",
    )
}

private class PrnPatientDataSource : InventoryPatientDataSource() {
    var prnRecordCount = 0
    override suspend fun medications() = listOf(testMedication("prn", 10.0).copy(isPrn = true, prnInstructions = "痛い時"))
    override suspend fun recordPrn(medication: PatientMedication) { prnRecordCount += 1 }
}

private class RefreshingPatientDataSource : PatientDataSource {
    var todayCount = 0
    private var taken = false
    override suspend fun today(): List<PatientDose> {
        todayCount += 1
        return listOf(testDose("med").copy(status = if (taken) DoseStatus.TAKEN else DoseStatus.PENDING))
    }
    override suspend fun slotTimes() = PatientSlotTimes.DEFAULT
    override suspend fun medications() = listOf(testMedication("med", 10.0))
    override suspend fun recordDose(dose: PatientDose) { taken = true }
    override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
    override suspend fun revokeSession() = Unit
}

private fun testDose(medicationId: String) = PatientDose(
    key = "dose-$medicationId", medicationId = medicationId,
    scheduledAt = Instant.parse("2026-07-12T23:00:00Z"), status = DoseStatus.PENDING,
    medicationName = medicationId, dosageText = "1錠", doseCount = 1.0,
)

private fun testMedication(id: String, quantity: Double) = PatientMedication(
    id = id, patientId = "patient", name = id, dosageText = "1錠", doseCountPerIntake = 1.0,
    dosageStrengthValue = 1.0, dosageStrengthUnit = "mg", notes = null, isPrn = false,
    prnInstructions = null, startDate = Instant.EPOCH, endDate = null, inventoryCount = quantity,
    inventoryUnit = "錠", inventoryEnabled = true, inventoryQuantity = quantity, inventoryOut = quantity <= 0,
    isActive = true, isArchived = false, nextScheduledAt = null, regimenTimes = null, regimenDaysOfWeek = null,
)
