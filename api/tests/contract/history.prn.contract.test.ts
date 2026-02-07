import { describe, expect, it } from "vitest";

describe("history day PRN contract", () => {
  it("includes PRN items in day payload", () => {
    const payload = {
      date: "2026-02-02",
      doses: [],
      prnItems: [
        {
          medicationId: "med-1",
          medicationName: "Medication A",
          takenAt: "2026-02-02T10:00:00.000Z",
          quantityTaken: 1,
          actorType: "PATIENT"
        }
      ]
    };

    const item = payload.prnItems[0];
    expect(payload.date).toBe("2026-02-02");
    expect(item).toMatchObject({
      medicationId: "med-1",
      medicationName: "Medication A",
      takenAt: "2026-02-02T10:00:00.000Z",
      quantityTaken: 1,
      actorType: "PATIENT"
    });
  });
});
