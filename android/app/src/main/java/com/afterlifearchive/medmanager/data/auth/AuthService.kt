package com.afterlifearchive.medmanager.data.auth

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
)

interface AuthService {
    suspend fun login(email: String, password: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
}
