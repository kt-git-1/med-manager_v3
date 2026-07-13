package com.afterlifearchive.medmanager.data.auth

data class AuthSession(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
)

interface AuthService {
    suspend fun login(email: String, password: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
    suspend fun signup(email: String, password: String): AuthSession = error("signup not implemented")
    suspend fun resendSignupConfirmation(email: String): Unit = error("resend not implemented")
}
