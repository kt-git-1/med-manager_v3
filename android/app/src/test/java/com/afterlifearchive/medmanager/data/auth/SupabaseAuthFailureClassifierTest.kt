package com.afterlifearchive.medmanager.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseAuthFailureClassifierTest {
    @Test
    fun mapsCurrentSupabaseMessagesToIosParityFailures() {
        val cases = listOf(
            Triple(400, "Invalid login credentials", AuthFailure.INVALID_CREDENTIALS),
            Triple(400, "Error sending confirmation email", AuthFailure.CONFIRMATION_EMAIL_FAILED),
            Triple(422, "User already registered", AuthFailure.EMAIL_ALREADY_REGISTERED),
            Triple(422, "user_already_exists", AuthFailure.EMAIL_ALREADY_REGISTERED),
            Triple(400, "Email not confirmed", AuthFailure.EMAIL_NOT_CONFIRMED),
            Triple(422, "Password should be at least 6 characters", AuthFailure.WEAK_PASSWORD),
            Triple(422, "Email format is invalid", AuthFailure.INVALID_EMAIL),
            Triple(400, "Malformed request", AuthFailure.LOGIN_FAILED),
            Triple(403, "Access denied", AuthFailure.FORBIDDEN),
            Triple(404, "Endpoint missing", AuthFailure.NOT_FOUND),
            Triple(409, "Conflict", AuthFailure.EMAIL_ALREADY_REGISTERED),
            Triple(429, "Too many requests", AuthFailure.RATE_LIMITED),
            Triple(500, "Unexpected server failure", AuthFailure.UNAVAILABLE),
        )

        cases.forEach { (status, message, expected) ->
            assertEquals(expected, classifyAuthFailure(status, message))
        }
    }
}
