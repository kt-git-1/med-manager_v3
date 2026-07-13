package com.afterlifearchive.medmanager.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
)

data class HttpResponse(val status: Int, val body: String)

fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

class UrlConnectionTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = 15_000
            readTimeout = 20_000
            request.headers.forEach(::setRequestProperty)
            if (request.body != null) {
                doOutput = true
                outputStream.bufferedWriter().use { it.write(request.body) }
            }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (_: IOException) {
            throw ApiException.Network()
        } finally {
            connection.disconnect()
        }
    }
}

class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val isPatientSession: () -> Boolean = { false },
    private val refreshPatientIfNeeded: suspend () -> Boolean = { true },
    private val forceRefreshPatient: suspend () -> Boolean = { false },
    private val onPatientAuthFailure: () -> Unit = {},
    private val transport: HttpTransport = UrlConnectionTransport(),
) {
    suspend fun get(path: String, allowsPatientRefreshRetry: Boolean = true): JSONObject =
        request(path, "GET", null, allowsPatientRefreshRetry)

    suspend fun post(path: String, body: JSONObject, allowsPatientRefreshRetry: Boolean = true): JSONObject =
        request(path, "POST", body, allowsPatientRefreshRetry)

    suspend fun delete(path: String, allowsPatientRefreshRetry: Boolean = true): JSONObject =
        request(path, "DELETE", null, allowsPatientRefreshRetry)

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject?,
        allowsPatientRefreshRetry: Boolean,
    ): JSONObject {
        if (allowsPatientRefreshRetry && isPatientSession() && !refreshPatientIfNeeded()) {
            onPatientAuthFailure()
            throw ApiException.Unauthorized()
        }

        val initial = transport.execute(buildRequest(path, method, body))
        if (initial.status == 401 && allowsPatientRefreshRetry && isPatientSession()) {
            if (!forceRefreshPatient()) {
                onPatientAuthFailure()
                throw ApiException.Unauthorized()
            }
            val retried = transport.execute(buildRequest(path, method, body))
            if (retried.status == 401) onPatientAuthFailure()
            return parse(retried)
        }
        return parse(initial)
    }

    private fun buildRequest(path: String, method: String, body: JSONObject?): HttpRequest {
        val headers = buildMap {
            put("Accept", "application/json")
            put("Content-Type", "application/json")
            tokenProvider()?.let { put("Authorization", "Bearer $it") }
        }
        return HttpRequest(
            url = URL(URL(baseUrl), path).toString(),
            method = method,
            headers = headers,
            body = body?.toString(),
        )
    }

    private fun parse(response: HttpResponse): JSONObject {
        if (response.status !in 200..299) throw ApiErrorMapper.map(response.status, response.body)
        return if (response.body.isBlank()) JSONObject() else JSONObject(response.body)
    }
}
