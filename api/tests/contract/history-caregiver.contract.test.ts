import { describe, expect, it, vi } from "vitest";
import { assertCaregiverPatientScope, requireCaregiver } from "../../src/middleware/auth";

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: vi.fn(async (patientId: string, caregiverUserId: string) => {
    if (patientId === "patient-1" && caregiverUserId === "caregiver-1") {
      return {
        id: patientId,
        caregiverId: caregiverUserId,
        displayName: "Care Recipient",
        createdAt: new Date(),
        updatedAt: new Date()
      };
    }
    return null;
  })
}));

const slotStatuses = ["pending", "taken", "missed", "none"] as const;
const doseStatuses = ["pending", "taken", "missed"] as const;

describe("history caregiver contract", () => {
  it("requires authorization", async () => {
    await expect(requireCaregiver()).rejects.toMatchObject({ statusCode: 401 });
  });

  it("conceals non-owned patient access", async () => {
    await expect(
      assertCaregiverPatientScope("caregiver-1", "patient-999")
    ).rejects.toMatchObject({ statusCode: 404 });
  });

  it("defines month response shape", () => {
    const payload = {
      year: 2026,
      month: 2,
      days: [
        {
          date: "2026-02-02",
          slotSummary: {
            morning: "taken",
            noon: "none",
            evening: "pending",
            bedtime: "missed"
          }
        }
      ]
    };

    const day = payload.days[0];
    expect(day?.date).toBe("2026-02-02");
    expect(day?.slotSummary).toBeDefined();

    const summary = day?.slotSummary as Record<string, (typeof slotStatuses)[number]>;
    for (const slot of ["morning", "noon", "evening", "bedtime"]) {
      expect(slotStatuses).toContain(summary[slot]);
    }
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
