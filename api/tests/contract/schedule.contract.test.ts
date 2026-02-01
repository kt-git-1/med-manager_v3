import { describe, expect, it } from "vitest";
import { buildScheduleResponse } from "../../src/services/scheduleResponse";

describe("schedule contract", () => {
  it("includes stable key and medication snapshot", () => {
    const response = buildScheduleResponse([
      {
        patientId: "patient-1",
        medicationId: "med-1",
        scheduledAt: "2026-02-02T08:00:00.000Z",
        medicationSnapshot: {
          name: "Medication A",
          dosageText: "1 tablet",
          doseCountPerIntake: 1,
          dosageStrengthValue: 10,
          dosageStrengthUnit: "mg"
        }
      }
    ]);

    expect(response).toEqual({
      data: [
        {
          key: "patient-1:med-1:2026-02-02T08:00:00.000Z",
          patientId: "patient-1",
          medicationId: "med-1",
          scheduledAt: "2026-02-02T08:00:00.000Z",
          medicationSnapshot: {
            name: "Medication A",
            dosageText: "1 tablet",
            doseCountPerIntake: 1,
            dosageStrengthValue: 10,
            dosageStrengthUnit: "mg"
          }
        }
      ]
    });
  });
});
