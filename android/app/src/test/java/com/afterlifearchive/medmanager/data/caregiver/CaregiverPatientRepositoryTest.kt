package com.afterlifearchive.medmanager.data.caregiver

import com.afterlifearchive.medmanager.data.network.ApiException
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
