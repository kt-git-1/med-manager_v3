import { describe, expect, it } from "vitest";
import { buildScheduleResponse } from "../../src/services/scheduleResponse";

describe("dose recording patient contract", () => {
  it("includes effectiveStatus in today schedule response", () => {
    const payload = buildScheduleResponse([
      {
        patientId: "patient-1",
        medicationId: "med-1",
        scheduledAt: "2026-02-02T08:00:00.000Z",
        effectiveStatus: "taken",
        medicationSnapshot: {
          name: "Medication A",
          dosageText: "1 tablet",
          doseCountPerIntake: 1,
          dosageStrengthValue: 10,
          dosageStrengthUnit: "mg"
        }
      }
    ]);

    expect(payload).toEqual({
      data: [
        {
          key: "patient-1:med-1:2026-02-02T08:00:00.000Z",
          patientId: "patient-1",
          medicationId: "med-1",
          scheduledAt: "2026-02-02T08:00:00.000Z",
          effectiveStatus: "taken",
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

  it("defines the taken record response shape", () => {
    const response = {
      data: {
        medicationId: "med-1",
        scheduledAt: "2026-02-02T08:00:00.000Z",
        takenAt: "2026-02-02T08:05:00.000Z",
        recordedByType: "patient"
      }
    };

    expect(response.data).toEqual({
      medicationId: "med-1",
      scheduledAt: "2026-02-02T08:00:00.000Z",
      takenAt: "2026-02-02T08:05:00.000Z",
      recordedByType: "patient"
    });
  });
});
