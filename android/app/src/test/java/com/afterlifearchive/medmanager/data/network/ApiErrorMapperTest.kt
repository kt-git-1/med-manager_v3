package com.afterlifearchive.medmanager.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMapperTest {
    @Test
    fun validationJoinsAllMessages() {
        val error = ApiErrorMapper.map(
            422,
            """{"error":"validation","messages":["名前を入力してください。","文字数を確認してください。"]}""",
        )

        assertTrue(error is ApiException.Validation)
        assertEquals("名前を入力してください。\n文字数を確認してください。", error.message)
    }

    @Test
    fun mapsInsufficientInventoryContract() {
        val error = ApiErrorMapper.map(
            409,
            """{"error":"insufficient_inventory","message":"Insufficient inventory"}""",
        )

        assertTrue(error is ApiException.InsufficientInventory)
        assertEquals("お薬の在庫が不足しています。", error.message)
    }

    @Test
    fun mapsPatientLimitWithStructuredValues() {
        val error = ApiErrorMapper.map(
            403,
            """{"code":"PATIENT_LIMIT_EXCEEDED","message":"limit","limit":1,"current":1}""",
        )

        assertTrue(error is ApiException.PatientLimitExceeded)
        error as ApiException.PatientLimitExceeded
        assertEquals(1, error.limit)
        assertEquals(1, error.current)
    }

    @Test
    fun mapsHistoryRetentionWithoutTreatingItAsAuthFailure() {
        val error = ApiErrorMapper.map(
            403,
            """{"code":"HISTORY_RETENTION_LIMIT","message":"履歴の閲覧は直近30日間に制限されています。","cutoffDate":"2026-06-12","retentionDays":30}""",
        )

        assertTrue(error is ApiException.HistoryRetentionLimit)
        error as ApiException.HistoryRetentionLimit
        assertEquals("2026-06-12", error.cutoffDate)
        assertEquals(30, error.retentionDays)
    }

    @Test
    fun mapsRateLimitToSafeJapaneseMessage() {
        val error = ApiErrorMapper.map(429, """{"error":"rate_limited","message":"Too many requests"}""")

        assertTrue(error is ApiException.RateLimited)
        assertEquals("操作が続きました。しばらく待ってから、もう一度お試しください。", error.message)
    }

    @Test
    fun mapsGenericStatusFamilies() {
        assertTrue(ApiErrorMapper.map(401, "{}") is ApiException.Unauthorized)
        assertTrue(ApiErrorMapper.map(403, "{}") is ApiException.Forbidden)
        assertTrue(ApiErrorMapper.map(404, "{}") is ApiException.NotFound)
        assertTrue(ApiErrorMapper.map(409, "{}") is ApiException.Conflict)
        assertTrue(ApiErrorMapper.map(500, "{}") is ApiException.Server)
    }
}
