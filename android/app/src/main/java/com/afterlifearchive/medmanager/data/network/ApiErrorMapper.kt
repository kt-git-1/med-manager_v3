package com.afterlifearchive.medmanager.data.network

import org.json.JSONObject

object ApiErrorMapper {
    fun map(status: Int, text: String): ApiException {
        val payload = runCatching { JSONObject(text) }.getOrNull()
        val code = payload?.optString("code")?.takeIf(String::isNotBlank)
            ?: payload?.optString("error")?.takeIf(String::isNotBlank)
        val message = errorMessage(payload)

        if (payload != null && status == 403 && code == "PATIENT_LIMIT_EXCEEDED") {
            return ApiException.PatientLimitExceeded(
                limit = payload.optInt("limit"),
                current = payload.optInt("current"),
            )
        }
        if (payload != null && status == 403 && code == "HISTORY_RETENTION_LIMIT") {
            return ApiException.HistoryRetentionLimit(
                cutoffDate = payload.optString("cutoffDate"),
                retentionDays = payload.optInt("retentionDays"),
            )
        }
        if (status == 409 && code == "insufficient_inventory") {
            return ApiException.InsufficientInventory()
        }

        return when (status) {
            400, 422 -> ApiException.Validation(message)
            401 -> ApiException.Unauthorized()
            403 -> ApiException.Forbidden()
            404 -> ApiException.NotFound()
            409 -> ApiException.Conflict(message)
            429 -> ApiException.RateLimited()
            in 500..599 -> ApiException.Server()
            else -> ApiException.Network()
        }
    }

    private fun errorMessage(payload: JSONObject?): String? {
        if (payload == null) return null
        val messages = payload.optJSONArray("messages")
        if (messages != null && messages.length() > 0) {
            return buildList {
                for (index in 0 until messages.length()) {
                    messages.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
        }
        return payload.optString("message").takeIf(String::isNotBlank)
            ?: payload.optString("error").takeIf { it.isNotBlank() && !it.looksLikeCode() }
    }

    private fun String.looksLikeCode(): Boolean = all { it.isLowerCase() || it == '_' || it.isDigit() }
}
