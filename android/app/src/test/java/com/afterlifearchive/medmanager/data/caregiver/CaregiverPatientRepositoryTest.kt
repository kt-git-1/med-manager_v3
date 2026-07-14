package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiException
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.AppMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverPatientRepositoryTest {
    @Test
    fun solePatientIsAutoSelectedAndPersisted() = runTest {
        val storage = FakeStorage()
        val repository = repository(storage) { listOf(patient("one", "さくら")) }

        repository.refresh()

        assertEquals("one", repository.state.value.selectedPatientId)
        assertEquals("one", storage.currentPatientId)
    }

    @Test
    fun invalidStoredSelectionIsClearedWhenSeveralPatientsExist() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "removed" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            CaregiverPatientDataSource { listOf(patient("one", "さくら"), patient("two", "あおい")) },
            selection,
        )

        repository.refresh()

        assertNull(repository.state.value.selectedPatientId)
        assertNull(storage.currentPatientId)
    }

    @Test
    fun validSelectionIsRestoredAndCanBeChanged() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "two" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val repository = CaregiverPatientRepository(
            CaregiverPatientDataSource { listOf(patient("one", "さくら"), patient("two", "あおい")) },
            selection,
        )

        repository.refresh()
        repository.selectPatient("one")

        assertEquals("one", repository.state.value.selectedPatientId)
        assertEquals("one", storage.currentPatientId)
    }

    @Test
    fun loadFailureIsAnExplicitStateAndDoesNotInventPatients() = runTest {
        val repository = repository(FakeStorage()) { error("offline") }

        repository.refresh()

        assertTrue(repository.state.value.loadFailed)
        assertFalse(repository.state.value.loading)
        assertTrue(repository.state.value.patients.isEmpty())
    }

    @Test
    fun createTrimsNamePrependsPatientAndSelectsIt() = runTest {
        val storage = FakeStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        var submitted = ""
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("old", "あおい"))
            override suspend fun createPatient(displayName: String): CaregiverPatient {
                submitted = displayName
                return patient("new", displayName)
            }
        }
        val repository = CaregiverPatientRepository(source, selection)
        repository.refresh()

        assertTrue(repository.createPatient("  さくら  "))

        assertEquals("さくら", submitted)
        assertEquals("new", repository.state.value.patients.first().id)
        assertEquals("new", storage.currentPatientId)
    }

    @Test
    fun invalidCreateNeverCallsDataSource() = runTest {
        var calls = 0
        val storage = FakeStorage()
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = emptyList<CaregiverPatient>()
            override suspend fun createPatient(displayName: String): CaregiverPatient {
                calls += 1
                return patient("new", displayName)
            }
        }
        val repository = CaregiverPatientRepository(source, selection)

        assertFalse(repository.createPatient(" "))
        assertFalse(repository.createPatient("x".repeat(51)))
        assertEquals(0, calls)
        assertEquals(CaregiverCreateError.TOO_LONG, repository.state.value.createError)
    }

    @Test
    fun patientLimitResponseKeepsExistingSelectionAndShowsSpecificError() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "old" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("old", "あおい"))
            override suspend fun createPatient(displayName: String): CaregiverPatient {
                throw ApiException.PatientLimitExceeded(limit = 1, current = 1)
            }
        }
        val repository = CaregiverPatientRepository(source, selection)
        repository.refresh()

        assertFalse(repository.createPatient("さくら"))
        assertEquals(CaregiverCreateError.PATIENT_LIMIT, repository.state.value.createError)
        assertEquals("old", repository.state.value.selectedPatientId)
        assertEquals("old", storage.currentPatientId)
    }

    @Test
    fun slotTimeSaveUpdatesPatientAndAdvancesFreshnessOnlyAfterSuccess() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "old" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val freshness = MutationFreshnessStore()
        val original = CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00")
        val changed = CaregiverSlotTimes("07:30", "12:15", "18:45", "22:00")
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(CaregiverPatient("old", "あおい", original))
            override suspend fun updateSlotTimes(patientId: String, slotTimes: CaregiverSlotTimes) = slotTimes
        }
        val repository = CaregiverPatientRepository(source, selection, freshness)
        repository.refresh()

        assertTrue(repository.updateSelectedPatientSlotTimes(changed))

        assertEquals(changed, repository.state.value.selectedPatient?.slotTimes)
        assertEquals(1L, freshness.revisions.value.slotTimes)
    }

    @Test
    fun malformedSlotTimeIsRejectedWithoutMutation() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "old" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        var calls = 0
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("old", "あおい"))
            override suspend fun updateSlotTimes(patientId: String, slotTimes: CaregiverSlotTimes): CaregiverSlotTimes {
                calls += 1
                return slotTimes
            }
        }
        val repository = CaregiverPatientRepository(source, selection)
        repository.refresh()

        assertFalse(repository.updateSelectedPatientSlotTimes(CaregiverSlotTimes("24:00", "12:00", "18:00", "21:00")))
        assertEquals(0, calls)
        assertTrue(repository.state.value.slotTimesSaveFailed)
    }

    @Test
    fun issuedCodeBelongsToCurrentSelectionAndIsClearedWhenSelectionChanges() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "one" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("one", "あおい"), patient("two", "さくら"))
            override suspend fun issueLinkingCode(patientId: String) =
                CaregiverLinkingCode(if (patientId == "one") "123456" else "654321", "2026-07-14T12:15:00Z")
        }
        val repository = CaregiverPatientRepository(source, selection)
        repository.refresh()

        assertTrue(repository.issueLinkingCode())
        assertEquals("123456", repository.state.value.linkingCode?.code)

        repository.selectPatient("two")
        assertNull(repository.state.value.linkingCode)
    }

    @Test
    fun successfulPatientDeleteRemovesThenAutoSelectsSoleRemainingPatient() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "one" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val freshness = MutationFreshnessStore()
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("one", "あおい"), patient("two", "さくら"))
            override suspend fun deletePatient(patientId: String) = Unit
        }
        val repository = CaregiverPatientRepository(source, selection, freshness)
        repository.refresh()

        assertTrue(repository.deleteSelectedPatient())

        assertEquals(listOf("two"), repository.state.value.patients.map { it.id })
        assertEquals("two", storage.currentPatientId)
        assertEquals(1L, freshness.revisions.value.dose)
    }

    @Test
    fun failedDestructiveActionPreservesPatientAndSelection() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "one" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("one", "あおい"))
            override suspend fun revokePatient(patientId: String) = error("offline")
        }
        val repository = CaregiverPatientRepository(source, selection)
        repository.refresh()

        assertFalse(repository.revokeSelectedPatient())

        assertEquals("one", repository.state.value.selectedPatientId)
        assertEquals("one", storage.currentPatientId)
        assertTrue(repository.state.value.destructiveActionFailed)
    }

    @Test
    fun failedAccountDeleteDoesNotClearLocalState() = runTest {
        val storage = FakeStorage().apply { currentPatientId = "one" }
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        val source = object : CaregiverPatientDataSource {
            override suspend fun listPatients() = listOf(patient("one", "あおい"))
            override suspend fun deleteCaregiverAccount() = error("server")
        }
        val repository = CaregiverPatientRepository(source, selection)
        repository.refresh()

        assertFalse(repository.deleteCaregiverAccount())

        assertEquals("one", repository.state.value.selectedPatientId)
        assertEquals("one", storage.currentPatientId)
    }

    private fun repository(storage: FakeStorage, block: suspend () -> List<CaregiverPatient>): CaregiverPatientRepository {
        val selection = CaregiverSelectionRepository(storage).also { it.restore() }
        return CaregiverPatientRepository(CaregiverPatientDataSource { block() }, selection)
    }

    private fun patient(id: String, name: String) = CaregiverPatient(id, name)
}

private class FakeStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    private val secrets = mutableMapOf<String, String>()
    override fun getSecret(key: String) = secrets[key]
    override fun putSecret(key: String, value: String?) {
        if (value == null) secrets.remove(key) else secrets[key] = value
    }
}
