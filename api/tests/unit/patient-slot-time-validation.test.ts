import { describe, expect, it } from "vitest";
import { validatePatientSlotTimes } from "../../src/services/patientSlotTimeService";

describe("patient slot time ordering", () => {
  it("accepts strictly increasing times", () => {
    const result = validatePatientSlotTimes({
      morning: "08:00",
      noon: "13:00",
      evening: "19:00",
      bedtime: "23:00"
    });

    expect(result.errors).toEqual([]);
    expect(result.slotTimes).toBeDefined();
  });

  it("rejects noon earlier than morning", () => {
    const result = validatePatientSlotTimes({
      morning: "08:00",
      noon: "07:30",
      evening: "19:00",
      bedtime: "23:00"
    });

    expect(result.errors).toContain("noon must be later than morning");
    expect(result.slotTimes).toBeUndefined();
  });

  it("rejects equal adjacent times", () => {
    const result = validatePatientSlotTimes({
      morning: "08:00",
      noon: "08:00",
      evening: "19:00",
      bedtime: "23:00"
    });

    expect(result.errors).toContain("noon must be later than morning");
  });

  it("rejects evening earlier than noon", () => {
    const result = validatePatientSlotTimes({
      morning: "08:00",
      noon: "13:00",
      evening: "12:00",
      bedtime: "23:00"
    });

    expect(result.errors).toContain("evening must be later than noon");
  });

  it("rejects bedtime earlier than evening", () => {
    const result = validatePatientSlotTimes({
      morning: "08:00",
      noon: "13:00",
      evening: "19:00",
      bedtime: "18:00"
    });

    expect(result.errors).toContain("bedtime must be later than evening");
  });
});
