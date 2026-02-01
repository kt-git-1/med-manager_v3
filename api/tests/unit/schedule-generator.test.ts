import { describe, expect, it } from "vitest";
import { generateSchedule } from "../../src/services/scheduleService";

const baseMedication = {
  id: "med-1",
  patientId: "patient-1",
  name: "Medication A",
  dosageText: "1 tablet",
  doseCountPerIntake: 1,
  dosageStrengthValue: 10,
  dosageStrengthUnit: "mg",
  notes: null,
  startDate: new Date("2026-02-01T00:00:00Z"),
  endDate: null,
  inventoryCount: null,
  inventoryUnit: null,
  isActive: true,
  isArchived: false
};

const baseRegimen = {
  id: "reg-1",
  patientId: "patient-1",
  medicationId: "med-1",
  timezone: "UTC",
  startDate: new Date("2026-02-01T00:00:00Z"),
  endDate: null,
  times: ["08:00"],
  daysOfWeek: ["MON", "WED"],
  enabled: true
};

describe("schedule generator", () => {
  it("generates doses for matching days within range", () => {
    const from = new Date("2026-02-01T00:00:00Z");
    const to = new Date("2026-02-05T00:00:00Z");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [baseRegimen],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual([
      "2026-02-02T08:00:00.000Z",
      "2026-02-04T08:00:00.000Z"
    ]);
  });

  it("respects timezone when generating scheduledAt", () => {
    const from = new Date("2026-02-01T00:00:00+09:00");
    const to = new Date("2026-02-02T00:00:00+09:00");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          timezone: "Asia/Tokyo",
          daysOfWeek: []
        }
      ],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual(["2026-01-31T23:00:00.000Z"]);
  });

  it("excludes disabled regimens and inactive or archived medications", () => {
    const from = new Date("2026-02-01T00:00:00Z");
    const to = new Date("2026-02-02T00:00:00Z");

    const doses = generateSchedule({
      medications: [
        { ...baseMedication, id: "med-active" },
        { ...baseMedication, id: "med-inactive", isActive: false },
        { ...baseMedication, id: "med-archived", isArchived: true }
      ],
      regimens: [
        { ...baseRegimen, medicationId: "med-active", enabled: false },
        { ...baseRegimen, medicationId: "med-inactive" },
        { ...baseRegimen, medicationId: "med-archived" }
      ],
      from,
      to
    });

    expect(doses).toEqual([]);
  });

  it("treats endDate as exclusive and normalizes to minutes", () => {
    const from = new Date("2026-02-01T00:00:00Z");
    const to = new Date("2026-02-04T00:00:00Z");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          daysOfWeek: [],
          times: ["08:00"],
          endDate: new Date("2026-02-03T00:00:00Z")
        }
      ],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual([
      "2026-02-01T08:00:00.000Z",
      "2026-02-02T08:00:00.000Z"
    ]);
  });
});
