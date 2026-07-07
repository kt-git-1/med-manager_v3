import { describe, expect, it } from "vitest";
import { applyDoseStatuses, generateSchedule } from "../../src/services/scheduleService";

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
  timezone: "Asia/Tokyo",
  startDate: new Date("2026-02-01T00:00:00Z"),
  endDate: null,
  times: ["08:00"],
  daysOfWeek: ["MON", "WED"],
  enabled: true,
  createdAt: new Date("2026-01-31T00:00:00Z")
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
      "2026-02-01T23:00:00.000Z",
      "2026-02-03T23:00:00.000Z"
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

    expect(doses.map((dose) => dose.scheduledAt)).toEqual(["2026-02-01T23:00:00.000Z"]);
  });

  it("avoids duplicate doses across day boundary", () => {
    const from = new Date("2026-02-01T23:59:00+09:00");
    const to = new Date("2026-02-02T23:59:00+09:00");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          timezone: "Asia/Tokyo",
          daysOfWeek: [],
          times: ["00:00"]
        }
      ],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual(["2026-02-01T15:00:00.000Z"]);
  });

  it("generates multiple times for specified weekdays", () => {
    const from = new Date("2026-02-02T00:00:00Z");
    const to = new Date("2026-02-09T00:00:00Z");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          timezone: "Asia/Tokyo",
          daysOfWeek: ["MON", "FRI"],
          times: ["08:00", "18:30"]
        }
      ],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual([
      "2026-02-02T09:30:00.000Z",
      "2026-02-05T23:00:00.000Z",
      "2026-02-06T09:30:00.000Z",
      "2026-02-08T23:00:00.000Z"
    ]);
  });

  it("resolves regimen slot keys using provided slot times", () => {
    const from = new Date("2026-02-01T00:00:00+09:00");
    const to = new Date("2026-02-02T00:00:00+09:00");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          timezone: "Asia/Tokyo",
          daysOfWeek: [],
          times: ["morning"]
        }
      ],
      from,
      to,
      slotTimes: { morning: "07:30" }
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual(["2026-01-31T22:30:00.000Z"]);
  });

  it("resolves noon slot key to one pm by default", () => {
    const from = new Date("2026-02-01T00:00:00+09:00");
    const to = new Date("2026-02-02T00:00:00+09:00");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          timezone: "Asia/Tokyo",
          daysOfWeek: [],
          times: ["noon"]
        }
      ],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual(["2026-02-01T04:00:00.000Z"]);
  });

  it("starts newly created regimens from their creation time on the first day", () => {
    const from = new Date("2026-02-01T00:00:00+09:00");
    const to = new Date("2026-02-02T00:00:00+09:00");

    const doses = generateSchedule({
      medications: [baseMedication],
      regimens: [
        {
          ...baseRegimen,
          timezone: "Asia/Tokyo",
          startDate: new Date("2026-02-01T00:00:00Z"),
          createdAt: new Date("2026-02-01T14:00:00+09:00"),
          daysOfWeek: [],
          times: ["morning", "noon", "evening", "bedtime"]
        }
      ],
      from,
      to
    });

    expect(doses.map((dose) => dose.scheduledAt)).toEqual([
      "2026-02-01T10:00:00.000Z",
      "2026-02-01T13:00:00.000Z"
    ]);
  });

  it("keeps an existing same-day slot record taken after a slot preset time changes", () => {
    const doses = [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        scheduledAt: "2026-07-06T11:00:00.000Z", // 20:00 JST, new bedtime preset
        medicationSnapshot: {
          name: "Medication A",
          dosageText: "1 tablet",
          doseCountPerIntake: 1,
          dosageStrengthValue: 10,
          dosageStrengthUnit: "mg",
          notes: null
        }
      }
    ];
    const records = [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        scheduledAt: new Date("2026-07-06T13:00:00.000Z"), // 22:00 JST, old bedtime preset
        takenAt: new Date("2026-07-06T13:05:00.000Z"),
        recordedByType: "patient"
      }
    ];

    const result = applyDoseStatuses(doses, records, new Date("2026-07-07T00:00:00.000Z"), {
      timeZone: "Asia/Tokyo",
      slotTimes: { bedtime: "20:00" }
    });

    expect(result[0].effectiveStatus).toBe("taken");
    expect(result[0].recordedByType).toBe("patient");
  });
});
