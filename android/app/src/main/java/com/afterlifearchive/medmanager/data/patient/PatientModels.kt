package com.afterlifearchive.medmanager.data.patient

import java.time.Instant

enum class DoseStatus { PENDING, TAKEN, MISSED }

data class PatientDose(
    val key: String,
    val medicationId: String,
    val scheduledAt: Instant,
    val status: DoseStatus,
    val medicationName: String,
    val dosageText: String,
    val doseCount: Double,
)

enum class HistoryStatus { PENDING, TAKEN, MISSED, NONE }

data class HistoryDay(
    val date: String,
    val morning: HistoryStatus,
    val noon: HistoryStatus,
    val evening: HistoryStatus,
    val bedtime: HistoryStatus,
    val prnCount: Int,
)
