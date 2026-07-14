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
            Triple(429, "Too many requests", AuthFailure.RATE_LIMITED),
            Triple(500, "Unexpected server failure", AuthFailure.LOGIN_FAILED),
        )

        cases.forEach { (status, message, expected) ->
            assertEquals(expected, classifyAuthFailure(status, message))
        }
    }
}
