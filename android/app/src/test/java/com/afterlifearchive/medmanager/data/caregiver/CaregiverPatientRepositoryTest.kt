package com.afterlifearchive.medmanager.data.caregiver

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
