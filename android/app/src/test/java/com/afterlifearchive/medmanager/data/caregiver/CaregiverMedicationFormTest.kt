package com.afterlifearchive.medmanager.data.caregiver

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverMedicationFormTest {
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
            ),
            errors.map { it.field }.toSet(),
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
    }
}
