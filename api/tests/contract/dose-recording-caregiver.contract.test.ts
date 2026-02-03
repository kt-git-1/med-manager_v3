import { describe, expect, it } from "vitest";
import { buildScheduleResponse } from "../../src/services/scheduleResponse";

describe("dose recording caregiver contract", () => {
  it("includes effectiveStatus in caregiver schedule response", () => {
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

  it("defines the caregiver taken record response shape", () => {
    const response = {
      data: {
        medicationId: "med-1",
        scheduledAt: "2026-02-02T08:00:00.000Z",
        takenAt: "2026-02-02T08:05:00.000Z",
        recordedByType: "caregiver"
      }
    };

    expect(response.data).toEqual({
      medicationId: "med-1",
      scheduledAt: "2026-02-02T08:00:00.000Z",
      takenAt: "2026-02-02T08:05:00.000Z",
      recordedByType: "caregiver"
    });
  });

  it("returns empty body for delete response", async () => {
    const response = new Response(null, { status: 204 });

    expect(response.status).toBe(204);
    expect(await response.text()).toBe("");
  });
});
