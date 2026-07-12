package com.afterlifearchive.medmanager.data.session

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientSessionTokenTest {
    @Test
    fun parsesRefreshContract() {
        val response = JSONObject(
            """{"data":{"patientSessionToken":"patient-new","expiresAt":"2026-08-12T00:00:00Z"}}""",
        )

        val session = PatientSessionToken.fromJson(response)

        assertEquals("patient-new", session.token)
        assertEquals(1786492800L, session.expiresAtEpochSeconds)
    }

    @Test
    fun acceptsMissingExpiryLikeIosDto() {
        val response = JSONObject("""{"data":{"patientSessionToken":"patient-new"}}""")

        val session = PatientSessionToken.fromJson(response)

        assertNull(session.expiresAtEpochSeconds)
    }

    @Test
    fun rejectsMissingToken() {
        val response = JSONObject("""{"data":{"expiresAt":"2026-08-12T00:00:00Z"}}""")

        assertTrue(runCatching { PatientSessionToken.fromJson(response) }.isFailure)
    }
}
