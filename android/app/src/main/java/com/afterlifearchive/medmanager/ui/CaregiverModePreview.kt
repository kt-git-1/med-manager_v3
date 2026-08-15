package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverHistoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryItem
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventoryRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverInventorySummary
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverMedicationRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatient
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverPatientRepository
import com.afterlifearchive.medmanager.data.caregiver.CaregiverSlotTimes
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayDataSource
import com.afterlifearchive.medmanager.data.caregiver.CaregiverTodayRepository
import com.afterlifearchive.medmanager.data.freshness.MutationFreshnessStore
import com.afterlifearchive.medmanager.data.patient.DoseStatus
import com.afterlifearchive.medmanager.data.patient.HistoryDay
import com.afterlifearchive.medmanager.data.patient.HistoryDayDetail
import com.afterlifearchive.medmanager.data.patient.HistoryScheduledDose
import com.afterlifearchive.medmanager.data.patient.HistoryStatus
import com.afterlifearchive.medmanager.data.patient.MedicationSlot
import com.afterlifearchive.medmanager.data.patient.PatientDose
import com.afterlifearchive.medmanager.data.patient.PatientMedication
import com.afterlifearchive.medmanager.data.patient.RecordedByType
import com.afterlifearchive.medmanager.data.session.CaregiverSelectionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun CaregiverModePreview(initialTab: CaregiverTab = CaregiverTab.TODAY) {
    val patientName = stringResource(R.string.caregiver_preview_patient_name)
    val preview = remember(patientName) {
        val selection = CaregiverSelectionRepository(CaregiverPreviewSessionStorage()).also { it.restore() }
        val patientRepository = CaregiverPatientRepository(
            dataSource = CaregiverPatientDataSource {
                listOf(
                    CaregiverPatient(
                        id = CAREGIVER_PREVIEW_PATIENT_ID,
                        displayName = patientName,
                        slotTimes = CaregiverSlotTimes("08:00", "12:00", "18:00", "21:00"),
                    ),
                )
            },
            selectionRepository = selection,
        )
        caregiverPreviewRepositories(patientRepository)
    }
    key(initialTab) {
        CaregiverHomeScreen(
            repository = preview.patient,
            medicationRepository = preview.medications,
            todayRepository = preview.today,
            inventoryRepository = preview.inventory,
            historyRepository = preview.history,
            tutorialEnabled = false,
            initialTab = initialTab,
        )
    }
}

private data class CaregiverPreviewRepositories(
    val patient: CaregiverPatientRepository,
    val medications: CaregiverMedicationRepository,
    val today: CaregiverTodayRepository,
    val inventory: CaregiverInventoryRepository,
    val history: CaregiverHistoryRepository,
)

private fun caregiverPreviewRepositories(patientRepository: CaregiverPatientRepository): CaregiverPreviewRepositories {
    val freshness = MutationFreshnessStore()
    val zone = ZoneId.of("Asia/Tokyo")
    val today = LocalDate.now(zone)
    fun at(hour: Int, minute: Int): Instant = today.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()
    val medications = listOf(
        previewMedication("blood-pressure", "血圧の薬", "5 mg", false, 18.0, listOf("morning", "noon")),
        previewMedication("stomach", "整腸剤", "50 mg", false, 10.0, listOf("evening")),
        previewMedication("headache", "頭痛薬", "200 mg", true, 8.0, null),
    )
    val doses = listOf(
        PatientDose("preview-morning", "blood-pressure", at(8, 0), DoseStatus.TAKEN, "血圧の薬", "5 mg", 1.0, patientId = CAREGIVER_PREVIEW_PATIENT_ID, recordedByType = RecordedByType.PATIENT, slot = MedicationSlot.MORNING, takenAt = at(13, 21)),
        PatientDose("preview-noon", "blood-pressure", at(12, 30), DoseStatus.PENDING, "血圧の薬", "5 mg", 1.0, patientId = CAREGIVER_PREVIEW_PATIENT_ID, slot = MedicationSlot.NOON),
        PatientDose("preview-evening", "stomach", at(19, 0), DoseStatus.TAKEN, "整腸剤", "50 mg", 1.0, patientId = CAREGIVER_PREVIEW_PATIENT_ID, recordedByType = RecordedByType.PATIENT, slot = MedicationSlot.EVENING, takenAt = at(19, 5)),
        PatientDose("preview-bedtime", "stomach", at(23, 0), DoseStatus.PENDING, "整腸剤", "50 mg", 1.0, patientId = CAREGIVER_PREVIEW_PATIENT_ID, slot = MedicationSlot.BEDTIME),
    )
    val inventoryItems = listOf(
        previewInventory("blood-pressure", "血圧の薬 5 mg", 4.0, low = true, daysRemaining = 2),
        previewInventory("stomach", "整腸剤 50 mg", 10.0, low = false, daysRemaining = 5),
    )
    val medicationRepository = CaregiverMedicationRepository(CaregiverMedicationDataSource { medications }, freshness)
    val todayRepository = CaregiverTodayRepository(
        dataSource = object : CaregiverTodayDataSource {
            override suspend fun today(patientId: String) = doses
            override suspend fun medications(patientId: String) = medications
            override suspend fun inventory(patientId: String) = inventoryItems.map {
                CaregiverInventorySummary(it.medicationId, it.inventoryEnabled, it.inventoryQuantity, it.doseCountPerIntake, it.low, it.out)
            }
        },
        freshnessStore = freshness,
        now = { at(13, 47) },
    )
    val inventoryRepository = CaregiverInventoryRepository(
        dataSource = object : CaregiverInventoryDataSource {
            override suspend fun list(patientId: String) = inventoryItems
            override suspend fun update(patientId: String, medicationId: String, enabled: Boolean, quantity: Double?) = inventoryItems.first { it.medicationId == medicationId }
            override suspend fun adjust(patientId: String, medicationId: String, reason: String, delta: Double?, absoluteQuantity: Double?) = inventoryItems.first { it.medicationId == medicationId }
        },
        freshnessStore = freshness,
    )
    val historyRepository = CaregiverHistoryRepository(
        dataSource = object : CaregiverHistoryDataSource {
            override suspend fun month(patientId: String, yearMonth: YearMonth) = listOf(
                HistoryDay(today.toString(), HistoryStatus.TAKEN, HistoryStatus.PENDING, HistoryStatus.TAKEN, HistoryStatus.PENDING, 0),
                HistoryDay(today.minusDays(1).toString(), HistoryStatus.TAKEN, HistoryStatus.TAKEN, HistoryStatus.NONE, HistoryStatus.NONE, 0),
            )
            override suspend fun day(patientId: String, date: LocalDate) = HistoryDayDetail(
                date.toString(),
                doses.map { dose ->
                    HistoryScheduledDose(dose.medicationId, dose.medicationName, dose.dosageText, dose.doseCount, dose.scheduledAt, checkNotNull(dose.slot), dose.status, dose.recordedByType, dose.takenAt)
                },
                emptyList(),
            )
            override suspend fun recordMissed(patientId: String, dose: HistoryScheduledDose) = Unit
        },
        freshnessStore = freshness,
    )
    return CaregiverPreviewRepositories(patientRepository, medicationRepository, todayRepository, inventoryRepository, historyRepository)
}

private fun previewMedication(
    id: String,
    name: String,
    dosage: String,
    isPrn: Boolean,
    inventory: Double,
    regimenTimes: List<String>?,
) = PatientMedication(
    id = id,
    patientId = CAREGIVER_PREVIEW_PATIENT_ID,
    name = name,
    dosageText = dosage,
    doseCountPerIntake = 1.0,
    dosageStrengthValue = dosage.substringBefore(' ').toDoubleOrNull() ?: 0.0,
    dosageStrengthUnit = dosage.substringAfter(' ', ""),
    notes = null,
    isPrn = isPrn,
    prnInstructions = if (isPrn) "頭痛があるとき" else null,
    startDate = Instant.EPOCH,
    endDate = null,
    inventoryCount = inventory,
    inventoryUnit = "錠",
    inventoryEnabled = true,
    inventoryQuantity = inventory,
    inventoryOut = false,
    isActive = true,
    isArchived = false,
    nextScheduledAt = null,
    regimenTimes = regimenTimes,
    regimenDaysOfWeek = emptyList(),
)

private fun previewInventory(
    id: String,
    name: String,
    quantity: Double,
    low: Boolean,
    daysRemaining: Int,
) = CaregiverInventoryItem(
    medicationId = id,
    name = name,
    isPrn = false,
    doseCountPerIntake = 1.0,
    inventoryEnabled = true,
    inventoryQuantity = quantity,
    inventoryLowThreshold = 3,
    periodEnded = false,
    low = low,
    out = false,
    dailyPlannedUnits = 2.0,
    nextSevenDaysPlannedUnits = 14.0,
    nextFourteenDaysPlannedUnits = 28.0,
    nextTwentyOneDaysPlannedUnits = 42.0,
    daysRemaining = daysRemaining,
    refillDueDate = LocalDate.now(ZoneId.of("Asia/Tokyo")).plusDays(daysRemaining.toLong()).toString(),
)

private class CaregiverPreviewSessionStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = CAREGIVER_PREVIEW_PATIENT_ID
    override fun getSecret(key: String): String? = null
    override fun putSecret(key: String, value: String?) = Unit
}

private const val CAREGIVER_PREVIEW_PATIENT_ID = "preview-caregiver-patient"
