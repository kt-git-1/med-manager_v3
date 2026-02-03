import { describe, expect, it } from "vitest";
import { applyDoseStatuses, type ScheduleDose } from "../../src/services/scheduleService";

function makeDose(scheduledAt: string): ScheduleDose {
  return {
    patientId: "patient-1",
    medicationId: "med-1",
    scheduledAt,
    medicationSnapshot: {
      name: "Medication A",
      dosageText: "10mg",
      doseCountPerIntake: 1,
      dosageStrengthValue: 10,
      dosageStrengthUnit: "mg"
    }
  };
}

describe("dose missed status derivation integration", () => {
  it("marks a dose missed only after 60 minutes have passed", () => {
    const scheduledAt = new Date("2026-02-02T08:00:00.000Z").toISOString();
    const doses = [makeDose(scheduledAt)];

    const atThreshold = applyDoseStatuses(doses, [], new Date("2026-02-02T09:00:00.000Z"));
    expect(atThreshold[0]?.effectiveStatus).toBe("pending");

    const afterThreshold = applyDoseStatuses(doses, [], new Date("2026-02-02T09:00:01.000Z"));
    expect(afterThreshold[0]?.effectiveStatus).toBe("missed");
  });

  it("keeps a taken dose as taken even after the missed threshold", () => {
    const scheduledAt = new Date("2026-02-02T08:00:00.000Z").toISOString();
    const doses = [makeDose(scheduledAt)];
    const records = [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        scheduledAt: new Date(scheduledAt),
        recordedByType: "PATIENT"
      }
    ];

    const result = applyDoseStatuses(doses, records, new Date("2026-02-02T10:00:00.000Z"));
    expect(result[0]?.effectiveStatus).toBe("taken");
  });
});
