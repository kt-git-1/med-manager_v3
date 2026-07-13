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
            throw ApiException.Validation("メールアドレスとパスワードを入力してください。")
        }
        return authenticate("password", JSONObject().put("email", trimmedEmail).put("password", password))
    }

    override suspend fun refresh(refreshToken: String): AuthSession {
        if (refreshToken.isBlank()) throw ApiException.Validation("更新トークンがありません。")
        return authenticate("refresh_token", JSONObject().put("refresh_token", refreshToken))
    }

    private suspend fun authenticate(grantType: String, payload: JSONObject): AuthSession = withContext(Dispatchers.IO) {
        if (!config.hasSupabaseConfiguration) throw ApiException.Validation("Supabaseの設定がありません。")
        val grant = URLEncoder.encode(grantType, Charsets.UTF_8.name())
        val connection = (URL("${config.supabaseUrl}auth/v1/token?grant_type=$grant").openConnection() as HttpURLConnection).apply {
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
            if (accessToken.isBlank()) throw ApiException.Validation("認証トークンを取得できませんでした。")
            AuthSession(
                accessToken = accessToken,
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

    private fun authError(status: Int, response: String): ApiException {
        val json = runCatching { JSONObject(response) }.getOrNull()
        val raw = json?.optString("error_description")?.takeIf(String::isNotBlank)
            ?: json?.optString("message")?.takeIf(String::isNotBlank)
            ?: json?.optString("error")?.takeIf(String::isNotBlank)
            ?: "認証に失敗しました。"
        val normalized = raw.lowercase()
        val message = when {
            "invalid login credentials" in normalized || "invalid credentials" in normalized -> "メールアドレスまたはパスワードが正しくありません。"
            "email not confirmed" in normalized -> "メールアドレスの確認が完了していません。"
            status == 429 -> "しばらく待ってから、もう一度お試しください。"
            else -> "ログインできませんでした。入力内容を確認してください。"
        }
        return if (status == 429) ApiException.Network(message) else ApiException.Validation(message)
    }
}
