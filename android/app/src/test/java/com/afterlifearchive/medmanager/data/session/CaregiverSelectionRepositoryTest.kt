package com.afterlifearchive.medmanager.data.session

import com.afterlifearchive.medmanager.ui.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaregiverSelectionRepositoryTest {
    @Test
    fun selectionRestoresAndPersistsOutsideSessionState() {
        val storage = SelectionStorage().apply { currentPatientId = "patient-1" }
        val repository = CaregiverSelectionRepository(storage)

        repository.restore()
        assertEquals("patient-1", repository.state.value.patientId)

        repository.select(" patient-2 ")
        assertEquals("patient-2", repository.state.value.patientId)
        assertEquals("patient-2", storage.currentPatientId)

        repository.clear()
        assertNull(repository.state.value.patientId)
        assertNull(storage.currentPatientId)
    }
}

private class SelectionStorage : SessionStorage {
    override var mode: AppMode? = null
    override var currentPatientId: String? = null
    private val secrets = mutableMapOf<String, String>()
    override fun getSecret(key: String) = secrets[key]
    override fun putSecret(key: String, value: String?) {
        if (value == null) secrets.remove(key) else secrets[key] = value
    }
}
