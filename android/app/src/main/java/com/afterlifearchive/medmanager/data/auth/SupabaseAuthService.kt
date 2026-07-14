package com.afterlifearchive.medmanager.data.auth

import com.afterlifearchive.medmanager.AppConfig
import com.afterlifearchive.medmanager.data.network.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseAuthService(private val config: AppConfig) : AuthService {
    override suspend fun login(email: String, password: String): AuthSession {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            throw AuthException(AuthFailure.MISSING_CREDENTIALS)
        }
        return authenticate("password", JSONObject().put("email", trimmedEmail).put("password", password))
    }

    override suspend fun refresh(refreshToken: String): AuthSession {
        if (refreshToken.isBlank()) throw AuthException(AuthFailure.MISSING_REFRESH_TOKEN)
        return authenticate("refresh_token", JSONObject().put("refresh_token", refreshToken))
    }

    override suspend fun signup(email: String, password: String): AuthSession {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) throw AuthException(AuthFailure.INVALID_INPUT)
        return send(
            path = "auth/v1/signup?redirect_to=${URLEncoder.encode(config.emailConfirmationRedirectUrl, Charsets.UTF_8.name())}",
            payload = JSONObject().put("email", trimmedEmail).put("password", password),
            allowMissingAccessToken = true,
        )
    }

    override suspend fun resendSignupConfirmation(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) throw AuthException(AuthFailure.INVALID_EMAIL)
        send(
            path = "auth/v1/resend",
            payload = JSONObject().put("type", "signup").put("email", trimmedEmail)
                .put("options", JSONObject().put("email_redirect_to", config.emailConfirmationRedirectUrl)),
            allowMissingAccessToken = true,
        )
    }

    private suspend fun authenticate(grantType: String, payload: JSONObject): AuthSession = withContext(Dispatchers.IO) {
        if (!config.hasSupabaseConfiguration) throw AuthException(AuthFailure.MISSING_CONFIGURATION)
        val grant = URLEncoder.encode(grantType, Charsets.UTF_8.name())
        send("auth/v1/token?grant_type=$grant", payload, allowMissingAccessToken = false)
    }

    private suspend fun send(path: String, payload: JSONObject, allowMissingAccessToken: Boolean): AuthSession = withContext(Dispatchers.IO) {
        if (!config.hasSupabaseConfiguration) throw AuthException(AuthFailure.MISSING_CONFIGURATION)
        val connection = (URL("${config.supabaseUrl}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", config.supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer ${config.supabaseAnonKey}")
            outputStream.bufferedWriter().use { it.write(payload.toString()) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw authError(status, response)
            val json = JSONObject(response)
            val accessToken = json.optString("access_token")
            if (accessToken.isBlank() && !allowMissingAccessToken) throw AuthException(AuthFailure.MISSING_ACCESS_TOKEN)
            AuthSession(
                accessToken = accessToken.takeIf(String::isNotBlank),
                refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
                expiresInSeconds = json.optLong("expires_in").takeIf { it > 0 },
            )
        } catch (error: ApiException) {
            throw error
        } catch (_: IOException) {
            throw ApiException.Network()
        } finally {
            connection.disconnect()
        }
    }

    private fun authError(status: Int, response: String): AuthException {
        val json = runCatching { JSONObject(response) }.getOrNull()
        val raw = json?.optString("error_description")?.takeIf(String::isNotBlank)
            ?: json?.optString("message")?.takeIf(String::isNotBlank)
            ?: json?.optString("error")?.takeIf(String::isNotBlank)
            ?: "authentication_failed"
        val normalized = raw.lowercase()
        val failure = when {
            "invalid login credentials" in normalized || "invalid credentials" in normalized -> AuthFailure.INVALID_CREDENTIALS
            "email not confirmed" in normalized -> AuthFailure.EMAIL_NOT_CONFIRMED
            status == 429 -> AuthFailure.RATE_LIMITED
            else -> AuthFailure.LOGIN_FAILED
        }
        return AuthException(failure)
    }
}
