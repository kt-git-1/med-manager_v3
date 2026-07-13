package com.afterlifearchive.medmanager.data.patient

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PatientRepositoryTest {
    @Test
    fun loadingTodayPublishesDoses() = runTest {
        val source = FakePatientDataSource()
        val repository = PatientRepository(source)

        repository.loadToday()

        assertEquals(1, repository.state.value.doses.size)
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
    override suspend fun recordDose(dose: PatientDose) { recordCount += 1 }
    override suspend fun history(year: Int, month: Int) = emptyList<HistoryDay>()
    override suspend fun revokeSession() = Unit
}
