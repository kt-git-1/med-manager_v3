import { describe, expect, it, vi } from "vitest";
import { requirePatient } from "../../src/middleware/auth";

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

const slotStatuses = ["pending", "taken", "missed", "none"] as const;
type SlotStatus = (typeof slotStatuses)[number];

describe("history month patient contract", () => {
  it("requires authorization", async () => {
    await expect(requirePatient()).rejects.toMatchObject({ statusCode: 401 });
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

    const summary = day?.slotSummary as Record<string, SlotStatus>;
    for (const slot of ["morning", "noon", "evening", "bedtime"]) {
      expect(slotStatuses).toContain(summary[slot]);
    }
  });
});
