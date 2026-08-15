package com.afterlifearchive.medmanager.data.caregiver

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverMedicationFormTest {

    @Test
    fun newMedicationUsesThePublishedEmptyDoseField() {
        assertEquals("", CaregiverMedicationDraft().doseCountPerIntake)
    }

    @Test
    fun dailySupplyDaysCalculateInitialInventory() {
        val draft = CaregiverMedicationDraft(
            doseCountPerIntake = "1.5",
            supplyDays = "30",
            selectedSlots = setOf(CaregiverScheduleSlot.MORNING, CaregiverScheduleSlot.EVENING),
        )

        assertEquals(2, draft.dosesPerDay)
        assertEquals(90.0, draft.calculatedInventoryCount())
        assertEquals(
            CaregiverInventoryCalculation("1.5", 2, 30, "90", CaregiverScheduleFrequency.DAILY),
            draft.inventoryCalculation(),
        )
        assertEquals("90", draft.recalculateInventory().inventoryCount)
    }

    @Test
    fun weeklySupplyDaysCountOnlySelectedCalendarDays() {
        val draft = CaregiverMedicationDraft(
            doseCountPerIntake = "1",
            supplyDays = "7",
            startDate = LocalDate.parse("2026-08-17"), // Monday
            scheduleFrequency = CaregiverScheduleFrequency.WEEKLY,
            selectedDays = setOf(CaregiverScheduleDay.MON, CaregiverScheduleDay.WED, CaregiverScheduleDay.FRI),
            selectedSlots = setOf(CaregiverScheduleSlot.MORNING, CaregiverScheduleSlot.NOON),
        )

        assertEquals(6.0, draft.calculatedInventoryCount())
        assertEquals(
            CaregiverInventoryCalculation("1", 2, 3, "6", CaregiverScheduleFrequency.WEEKLY),
            draft.inventoryCalculation(),
        )
    }

    @Test
    fun calculatorClearsScheduledInventoryWhenInputsAreIncompleteButLeavesPrnManualValue() {
        val scheduled = CaregiverMedicationDraft(supplyDays = "30", inventoryCount = "20")
        val prn = scheduled.copy(isPrn = true)

        assertEquals("", scheduled.recalculateInventory().inventoryCount)
        assertEquals("20", prn.recalculateInventory().inventoryCount)
    }
    @Test
    fun requiredAndNumericRulesAreReportedTogether() {
        val errors = CaregiverMedicationDraft(
            name = " ",
            dosageStrengthValue = "zero",
            dosageStrengthUnit = "mg",
            doseCountPerIntake = "0",
            startDate = LocalDate.parse("2026-07-15"),
            endDate = LocalDate.parse("2026-07-14"),
            inventoryCount = "-1",
        ).validate()

        assertEquals(
            setOf(
                CaregiverMedicationField.NAME,
                CaregiverMedicationField.DOSAGE_VALUE,
                CaregiverMedicationField.DOSE_COUNT,
                CaregiverMedicationField.END_DATE,
                CaregiverMedicationField.INVENTORY_COUNT,
                CaregiverMedicationField.SCHEDULE_SLOT,
            ),
            errors.map { it.field }.toSet(),
        )
        assertEquals(
            setOf(
                CaregiverMedicationValidationMessage.NAME_REQUIRED,
                CaregiverMedicationValidationMessage.DOSAGE_VALUE_INVALID,
                CaregiverMedicationValidationMessage.DOSE_COUNT_INVALID,
                CaregiverMedicationValidationMessage.END_DATE_INVALID,
                CaregiverMedicationValidationMessage.INVENTORY_COUNT_INVALID,
                CaregiverMedicationValidationMessage.SCHEDULE_SLOT_REQUIRED,
            ),
            errors.map { it.message }.toSet(),
        )
        assertEquals(
            CaregiverMedicationValidationMessage.DOSAGE_UNIT_REQUIRED,
            CaregiverMedicationDraft(name = "薬", isPrn = true).validate().single().message,
        )
        assertEquals(
            CaregiverMedicationValidationMessage.DOSAGE_VALUE_REQUIRED,
            CaregiverMedicationDraft(name = "薬", dosageStrengthUnit = "mg", isPrn = true).validate().single().message,
        )
    }

    @Test
    fun unknownStrengthAndEmptyOptionalValuesAreValid() {
        val draft = CaregiverMedicationDraft(
            name = "ロキソニン",
            dosageStrengthUnit = "不明",
            doseCountPerIntake = "",
            isPrn = true,
        )

        assertTrue(draft.validate().isEmpty())
        val wire = draft.toWire("patient-1")
        assertEquals("不明", wire.dosageText)
        assertEquals(0.0, wire.dosageStrengthValue, 0.0)
        assertEquals(null, wire.inventoryCount)
        assertEquals(null, wire.prnInstructions)
    }

    @Test
    fun validRegularMedicationMapsTrimmedApiContract() {
        val draft = CaregiverMedicationDraft(
            name = " アムロジピン ",
            dosageStrengthValue = "5",
            dosageStrengthUnit = "mg",
            doseCountPerIntake = "1.5",
            startDate = LocalDate.parse("2026-07-15"),
            notes = " 朝食後 ",
            inventoryCount = "30",
            selectedSlots = setOf(CaregiverScheduleSlot.MORNING, CaregiverScheduleSlot.EVENING),
        )

        assertTrue(draft.validate().isEmpty())
        val wire = draft.toWire("patient-1")
        assertEquals("アムロジピン", wire.name)
        assertEquals("5mg", wire.dosageText)
        assertEquals(1.5, wire.doseCountPerIntake, 0.0)
        assertEquals("朝食後", wire.notes)
        assertEquals("錠", wire.inventoryUnit)
        assertTrue(wire.startDate.startsWith("2026-07-14T15:00:00Z"))
        assertFalse(wire.isPrn)
        assertEquals(listOf("morning", "evening"), draft.toRegimenWire().times)
    }

    @Test
    fun weeklyScheduleRequiresDaysAndUsesCanonicalOrder() {
        val draft = CaregiverMedicationDraft(
            name = "薬",
            dosageStrengthValue = "5",
            dosageStrengthUnit = "mg",
            scheduleFrequency = CaregiverScheduleFrequency.WEEKLY,
            selectedSlots = setOf(CaregiverScheduleSlot.BEDTIME),
        )
        assertTrue(
            draft.validate().any {
                it.field == CaregiverMedicationField.SCHEDULE_DAY &&
                    it.message == CaregiverMedicationValidationMessage.SCHEDULE_DAY_REQUIRED
            },
        )

        val selected = draft.copy(selectedDays = setOf(CaregiverScheduleDay.FRI, CaregiverScheduleDay.MON))
        assertTrue(selected.validate().isEmpty())
        assertEquals(listOf("MON", "FRI"), selected.toRegimenWire().daysOfWeek)
    }
}
