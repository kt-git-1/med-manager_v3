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

enum class RequestAuthPolicy {
    PUBLIC,
    PATIENT,
    CAREGIVER,
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
    private val patientTokenProvider: () -> String? = { null },
    private val caregiverTokenProvider: () -> String? = { null },
    private val refreshPatientIfNeeded: suspend () -> Boolean = { true },
    private val forceRefreshPatient: suspend () -> Boolean = { false },
    private val onPatientAuthFailure: () -> Unit = {},
    private val refreshCaregiverIfNeeded: suspend () -> Boolean = { true },
    private val onCaregiverAuthFailure: () -> Unit = {},
    private val transport: HttpTransport = UrlConnectionTransport(),
) {
    suspend fun get(
        path: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): JSONObject = JSONObject(getBody(path, authPolicy, allowsAuthRefresh))

    suspend fun getBody(
        path: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): String = request(path, "GET", null, authPolicy, allowsAuthRefresh)

    suspend fun post(
        path: String,
        body: JSONObject,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): JSONObject = JSONObject(postBody(path, body.toString(), authPolicy, allowsAuthRefresh))

    suspend fun postBody(
        path: String,
        body: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): String = request(path, "POST", body, authPolicy, allowsAuthRefresh)

    suspend fun postEmpty(
        path: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): String = request(path, "POST", null, authPolicy, allowsAuthRefresh)

    suspend fun patchBody(
        path: String,
        body: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): String = request(path, "PATCH", body, authPolicy, allowsAuthRefresh)

    suspend fun delete(
        path: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): JSONObject = JSONObject(deleteBody(path, authPolicy, allowsAuthRefresh))

    suspend fun deleteBody(
        path: String,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean = true,
    ): String = request(path, "DELETE", null, authPolicy, allowsAuthRefresh)

    private suspend fun request(
        path: String,
        method: String,
        body: String?,
        authPolicy: RequestAuthPolicy,
        allowsAuthRefresh: Boolean,
    ): String {
        if (allowsAuthRefresh) {
            when (authPolicy) {
                RequestAuthPolicy.PUBLIC -> Unit
                RequestAuthPolicy.PATIENT -> if (!refreshPatientIfNeeded()) {
                    onPatientAuthFailure()
                    throw ApiException.Unauthorized()
                }
                RequestAuthPolicy.CAREGIVER -> if (!refreshCaregiverIfNeeded()) {
                    onCaregiverAuthFailure()
                    throw ApiException.Unauthorized()
                }
            }
        }

        val initial = transport.execute(buildRequest(path, method, body, authPolicy))
        if (initial.status == 401 && allowsAuthRefresh && authPolicy == RequestAuthPolicy.PATIENT) {
            if (!forceRefreshPatient()) {
                onPatientAuthFailure()
                throw ApiException.Unauthorized()
            }
            val retried = transport.execute(buildRequest(path, method, body, authPolicy))
            if (retried.status == 401) onPatientAuthFailure()
            return parseBody(retried)
        }
        if (initial.status == 401 && authPolicy == RequestAuthPolicy.CAREGIVER) {
            onCaregiverAuthFailure()
        }
        return parseBody(initial)
    }

    private fun buildRequest(
        path: String,
        method: String,
        body: String?,
        authPolicy: RequestAuthPolicy,
    ): HttpRequest {
        val headers = buildMap {
            put("Accept", "application/json")
            put("Content-Type", "application/json")
            tokenFor(authPolicy)?.let { put("Authorization", "Bearer $it") }
        }
        return HttpRequest(
            url = URL(URL(baseUrl), path).toString(),
            method = method,
            headers = headers,
            body = body,
        )
    }

    private fun tokenFor(authPolicy: RequestAuthPolicy): String? = when (authPolicy) {
        RequestAuthPolicy.PUBLIC -> null
        RequestAuthPolicy.PATIENT -> patientTokenProvider()
        RequestAuthPolicy.CAREGIVER -> caregiverTokenProvider()
    }

    private fun parseBody(response: HttpResponse): String {
        if (response.status !in 200..299) throw ApiErrorMapper.map(response.status, response.body)
        return response.body.ifBlank { "{}" }
    }
}
