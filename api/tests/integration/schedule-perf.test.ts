import { describe, expect, it } from "vitest";
import { generateSchedule } from "../../src/services/scheduleService";

function buildMedication(index: number) {
  return {
    id: `med-${index}`,
    patientId: "patient-1",
    name: `Medication ${index}`,
    dosageText: "1 tablet",
    doseCountPerIntake: 1,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    isActive: true,
    isArchived: false,
    startDate: new Date("2026-02-01T00:00:00Z"),
    endDate: null
  };
}

function buildRegimen(index: number) {
  return {
    id: `reg-${index}`,
    patientId: "patient-1",
    medicationId: `med-${index}`,
    timezone: "Asia/Tokyo",
    startDate: new Date("2026-02-01T00:00:00Z"),
    endDate: null,
    times: ["08:00", "12:00", "18:00", "22:00"],
    daysOfWeek: [],
    enabled: true
  };
}

describe("schedule performance smoke", () => {
  it("generates a 7-day schedule within target budget", () => {
    const medications = Array.from({ length: 50 }, (_, idx) => buildMedication(idx + 1));
    const regimens = Array.from({ length: 50 }, (_, idx) => buildRegimen(idx + 1));
    const from = new Date("2026-02-01T00:00:00Z");
    const to = new Date("2026-02-08T00:00:00Z");

    const started = Date.now();
    const doses = generateSchedule({ medications, regimens, from, to });
    const elapsedMs = Date.now() - started;

    expect(doses.length).toBeGreaterThan(0);
    expect(elapsedMs).toBeLessThan(2000);
  });
});
