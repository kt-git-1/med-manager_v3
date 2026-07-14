package com.afterlifearchive.medmanager.data.network

sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized : ApiException("unauthorized")
    class Forbidden : ApiException("forbidden")
    class NotFound : ApiException("not_found")
    class Conflict(val safeMessage: String? = null) : ApiException(safeMessage ?: "conflict")
    class InsufficientInventory : ApiException("insufficient_inventory")
    class PatientLimitExceeded(val limit: Int, val current: Int) :
        ApiException("patient_limit_exceeded")
    class HistoryRetentionLimit(val cutoffDate: String, val retentionDays: Int) :
        ApiException("history_retention_limit")
    class Validation(val safeMessage: String? = null) : ApiException(safeMessage ?: "validation")
    class RateLimited : ApiException("rate_limited")
    class Network(val safeMessage: String? = null) : ApiException(safeMessage ?: "network")
    class Server : ApiException("server")
}
