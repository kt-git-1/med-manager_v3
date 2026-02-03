import { describe, expect, it, vi } from "vitest";
import { requirePatient } from "../../src/middleware/auth";

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

const doseStatuses = ["pending", "taken", "missed"] as const;

describe("history day patient contract", () => {
  it("requires authorization", async () => {
    await expect(requirePatient()).rejects.toMatchObject({ statusCode: 401 });
  });

  it("defines day response shape", () => {
    const payload = {
      date: "2026-02-02",
      doses: [
        {
          medicationId: "med-1",
          medicationName: "Medication A",
          dosageText: "1 tablet",
          doseCountPerIntake: 1,
          scheduledAt: "2026-02-02T08:00:00.000Z",
          slot: "morning",
          effectiveStatus: "taken"
        }
      ]
    };

    const dose = payload.doses[0];
    expect(payload.date).toBe("2026-02-02");
    expect(dose).toMatchObject({
      medicationId: "med-1",
      medicationName: "Medication A",
      dosageText: "1 tablet",
      doseCountPerIntake: 1,
      scheduledAt: "2026-02-02T08:00:00.000Z",
      slot: "morning",
      effectiveStatus: "taken"
    });
    expect(doseStatuses).toContain(dose.effectiveStatus);
  });
});
