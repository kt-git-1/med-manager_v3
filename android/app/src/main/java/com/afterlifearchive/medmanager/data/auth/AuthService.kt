package com.afterlifearchive.medmanager.data.auth

data class AuthSession(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
)

enum class AuthFailure {
    MISSING_CREDENTIALS,
    MISSING_REFRESH_TOKEN,
    INVALID_INPUT,
    INVALID_EMAIL,
    MISSING_CONFIGURATION,
    MISSING_ACCESS_TOKEN,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    CONFIRMATION_EMAIL_FAILED,
    EMAIL_ALREADY_REGISTERED,
    WEAK_PASSWORD,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMITED,
    LOGIN_FAILED,
    UNAVAILABLE,
}

class AuthException(val failure: AuthFailure) : Exception(failure.name)

interface AuthService {
    suspend fun login(email: String, password: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
    suspend fun signup(email: String, password: String): AuthSession = error("signup not implemented")
    suspend fun resendSignupConfirmation(email: String): Unit = error("resend not implemented")
}
