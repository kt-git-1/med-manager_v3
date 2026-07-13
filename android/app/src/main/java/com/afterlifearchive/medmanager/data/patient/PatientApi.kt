package com.afterlifearchive.medmanager.data.patient

import com.afterlifearchive.medmanager.data.network.ApiClient
import org.json.JSONObject
import java.time.Instant

interface PatientDataSource {
    suspend fun today(): List<PatientDose>
    suspend fun recordDose(dose: PatientDose)
    suspend fun history(year: Int, month: Int): List<HistoryDay>
    suspend fun revokeSession()
}

class PatientApi(private val client: ApiClient) : PatientDataSource {
    override suspend fun today(): List<PatientDose> {
        val response = client.get("api/patient/today")
        val data = response.getJSONArray("data")
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.getJSONObject(index)
                val medication = item.getJSONObject("medicationSnapshot")
                add(
                    PatientDose(
                        key = item.getString("key"),
                        medicationId = item.getString("medicationId"),
                        scheduledAt = Instant.parse(item.getString("scheduledAt")),
                        status = item.optString("effectiveStatus", "pending").toDoseStatus(),
                        medicationName = medication.getString("name"),
                        dosageText = medication.getString("dosageText"),
                        doseCount = medication.optDouble("doseCountPerIntake", 1.0),
                    ),
                )
            }
        }
    }

    override suspend fun recordDose(dose: PatientDose) {
        client.post(
            "api/patient/dose-records",
            JSONObject()
                .put("medicationId", dose.medicationId)
                .put("scheduledAt", dose.scheduledAt.toString()),
        )
    }

    override suspend fun history(year: Int, month: Int): List<HistoryDay> {
        val response = client.get("api/patient/history/month?year=$year&month=$month")
        val days = response.optJSONArray("days") ?: response.getJSONArray("monthSummary")
        val prn = response.optJSONObject("prnCountByDay")
        return buildList {
            for (index in 0 until days.length()) {
                val day = days.getJSONObject(index)
                val date = day.getString("date")
                val summary = day.getJSONObject("slotSummary")
                add(
                    HistoryDay(
                        date = date,
                        morning = summary.optString("morning", "none").toHistoryStatus(),
                        noon = summary.optString("noon", "none").toHistoryStatus(),
                        evening = summary.optString("evening", "none").toHistoryStatus(),
                        bedtime = summary.optString("bedtime", "none").toHistoryStatus(),
                        prnCount = prn?.optInt(date, 0) ?: 0,
                    ),
                )
            }
        }
    }

    override suspend fun revokeSession() {
        client.delete("api/patient/session")
    }
}

private fun String.toDoseStatus() = when (lowercase()) {
    "taken" -> DoseStatus.TAKEN
    "missed" -> DoseStatus.MISSED
    else -> DoseStatus.PENDING
}

private fun String.toHistoryStatus() = when (lowercase()) {
    "taken" -> HistoryStatus.TAKEN
    "missed" -> HistoryStatus.MISSED
    "pending" -> HistoryStatus.PENDING
    else -> HistoryStatus.NONE
}
