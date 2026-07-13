package com.afterlifearchive.medmanager.data.patient

import com.afterlifearchive.medmanager.data.network.ApiClient
import org.json.JSONObject
import java.time.Instant

interface PatientDataSource {
    suspend fun today(): List<PatientDose>
    suspend fun slotTimes(): PatientSlotTimes
    suspend fun medications(): List<PatientMedication> = emptyList()
    suspend fun recordSlot(date: String, slot: MedicationSlot): SlotBulkRecordResult = error("recordSlot not implemented")
    suspend fun recordPrn(medication: PatientMedication) = Unit
    suspend fun recordDose(dose: PatientDose)
    suspend fun history(year: Int, month: Int): List<HistoryDay>
    suspend fun historyDay(date: String): HistoryDayDetail = error("historyDay not implemented")
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
                        patientId = item.getString("patientId"),
                        medicationId = item.getString("medicationId"),
                        scheduledAt = Instant.parse(item.getString("scheduledAt")),
                        status = item.optString("effectiveStatus", "pending").toDoseStatus(),
                        recordedByType = item.optString("recordedByType").takeIf(String::isNotBlank)?.toRecordedByType(),
                        medicationName = medication.getString("name"),
                        dosageText = medication.getString("dosageText"),
                        doseCount = medication.optDouble("doseCountPerIntake", 1.0),
                        dosageStrengthValue = medication.getDouble("dosageStrengthValue"),
                        dosageStrengthUnit = medication.getString("dosageStrengthUnit"),
                        notes = medication.optString("notes").takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    override suspend fun slotTimes(): PatientSlotTimes {
        val data = client.get("api/patient/slot-times").getJSONObject("data").getJSONObject("slotTimes")
        return PatientSlotTimes(
            morning = PatientSlotTimes.requireValid(data.getString("morning")),
            noon = PatientSlotTimes.requireValid(data.getString("noon")),
            evening = PatientSlotTimes.requireValid(data.getString("evening")),
            bedtime = PatientSlotTimes.requireValid(data.getString("bedtime")),
        )
    }

    override suspend fun medications(): List<PatientMedication> {
        val data = client.get("api/medications").getJSONArray("data")
        return buildList {
            for (index in 0 until data.length()) add(data.getJSONObject(index).toPatientMedication())
        }
    }

    override suspend fun recordSlot(date: String, slot: MedicationSlot): SlotBulkRecordResult {
        val response = client.post(
            "api/patient/dose-records/slot",
            JSONObject().put("date", date).put("slot", slot.name.lowercase()),
        )
        val summary = response.getJSONObject("slotSummary")
        return SlotBulkRecordResult(
            updatedCount = response.getInt("updatedCount"),
            remainingCount = response.getInt("remainingCount"),
            insufficientCount = response.getInt("insufficientCount"),
            totalPills = response.getDouble("totalPills"),
            medCount = response.getInt("medCount"),
            slotTime = response.getString("slotTime"),
            slotSummary = MedicationSlot.entries.associateWith { slotValue ->
                summary.optString(slotValue.name.lowercase(), "none").toHistoryStatus()
            },
            recordingGroupId = response.optString("recordingGroupId").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override suspend fun recordPrn(medication: PatientMedication) {
        client.post(
            "api/patients/${medication.patientId}/prn-dose-records",
            JSONObject().put("medicationId", medication.id).put("takenAt", JSONObject.NULL).put("quantityTaken", JSONObject.NULL),
        )
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

    override suspend fun historyDay(date: String): HistoryDayDetail {
        val response = client.get("api/patient/history/day?date=$date")
        val dosesJson = response.optJSONArray("doses") ?: response.getJSONArray("dayDetails")
        val prnJson = response.optJSONArray("prnItems")
        return HistoryDayDetail(
            date = response.getString("date"),
            doses = buildList {
                for (index in 0 until dosesJson.length()) {
                    val item = dosesJson.getJSONObject(index)
                    add(
                        HistoryScheduledDose(
                            medicationId = item.getString("medicationId"),
                            medicationName = item.getString("medicationName"),
                            dosageText = item.getString("dosageText"),
                            doseCountPerIntake = item.getDouble("doseCountPerIntake"),
                            scheduledAt = Instant.parse(item.getString("scheduledAt")),
                            slot = MedicationSlot.valueOf(item.getString("slot").uppercase()),
                            status = item.getString("effectiveStatus").toDoseStatus(),
                            recordedByType = item.optString("recordedByType").takeIf(String::isNotBlank)?.toRecordedByType(),
                        ),
                    )
                }
            },
            prnItems = buildList {
                if (prnJson != null) for (index in 0 until prnJson.length()) {
                    val item = prnJson.getJSONObject(index)
                    add(
                        PrnHistoryItem(
                            medicationId = item.getString("medicationId"),
                            medicationName = item.getString("medicationName"),
                            takenAt = Instant.parse(item.getString("takenAt")),
                            quantityTaken = item.getDouble("quantityTaken"),
                            actorType = PrnActorType.valueOf(item.getString("actorType").uppercase()),
                        ),
                    )
                }
            },
        )
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

private fun String.toRecordedByType() = when (lowercase()) {
    "patient" -> RecordedByType.PATIENT
    "caregiver" -> RecordedByType.CAREGIVER
    else -> null
}

private fun JSONObject.toPatientMedication() = PatientMedication(
    id = getString("id"),
    patientId = getString("patientId"),
    name = getString("name"),
    dosageText = getString("dosageText"),
    doseCountPerIntake = getDouble("doseCountPerIntake"),
    dosageStrengthValue = getDouble("dosageStrengthValue"),
    dosageStrengthUnit = getString("dosageStrengthUnit"),
    notes = nullableString("notes"),
    isPrn = optBoolean("isPrn", false),
    prnInstructions = nullableString("prnInstructions"),
    startDate = Instant.parse(getString("startDate")),
    endDate = nullableString("endDate")?.let(Instant::parse),
    inventoryCount = if (isNull("inventoryCount")) null else optDouble("inventoryCount"),
    inventoryUnit = nullableString("inventoryUnit"),
    inventoryEnabled = optBoolean("inventoryEnabled", false),
    inventoryQuantity = optDouble("inventoryQuantity", 0.0),
    inventoryOut = optBoolean("inventoryOut", false),
    isActive = getBoolean("isActive"),
    isArchived = getBoolean("isArchived"),
    nextScheduledAt = nullableString("nextScheduledAt")?.let(Instant::parse),
    regimenTimes = nullableStringList("regimenTimes"),
    regimenDaysOfWeek = nullableStringList("regimenDaysOfWeek"),
)

private fun JSONObject.nullableString(key: String) = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.nullableStringList(key: String): List<String>? {
    if (isNull(key)) return null
    val array = optJSONArray(key) ?: return null
    return List(array.length()) { array.getString(it) }
}
