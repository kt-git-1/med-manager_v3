import { beforeEach, describe, expect, it, vi } from "vitest";
import { getScheduleWithStatus } from "../../src/services/scheduleService";

const mockData = vi.hoisted(() => ({
  medications: [
    {
      id: "med-1",
      patientId: "patient-1",
      name: "Medication A",
      dosageText: "1 tablet",
      doseCountPerIntake: 1,
      dosageStrengthValue: 10,
      dosageStrengthUnit: "mg",
      isActive: true,
      isArchived: false,
      startDate: new Date("2026-02-01T00:00:00.000Z")
    }
  ],
  regimens: [
    {
      id: "regimen-1",
      patientId: "patient-1",
      medicationId: "med-1",
      timezone: "UTC",
      startDate: new Date("2026-02-01T00:00:00.000Z"),
      times: ["08:00"],
      enabled: true
    }
  ],
  listDoseRecordsByPatientRange: vi.fn(async () => [])
}));

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    medication: { findMany: vi.fn(async () => mockData.medications) },
    regimen: { findMany: vi.fn(async () => mockData.regimens) }
  }
}));

vi.mock("../../src/repositories/doseRecordRepo", () => ({
  listDoseRecordsByPatientRange: mockData.listDoseRecordsByPatientRange
}));

describe("history day status integration", () => {
  beforeEach(() => {
    mockData.listDoseRecordsByPatientRange.mockResolvedValue([]);
  });

  it("marks a dose missed only after 60 minutes have passed", async () => {
    const from = new Date("2026-02-02T00:00:00.000Z");
    const to = new Date("2026-02-03T00:00:00.000Z");

    const atThreshold = await getScheduleWithStatus(
      "patient-1",
      from,
      to,
      "UTC",
      new Date("2026-02-02T09:00:00.000Z")
    );
    expect(atThreshold[0]?.effectiveStatus).toBe("pending");

    const afterThreshold = await getScheduleWithStatus(
      "patient-1",
      from,
      to,
      "UTC",
      new Date("2026-02-02T09:00:01.000Z")
    );
    expect(afterThreshold[0]?.effectiveStatus).toBe("missed");
  });

  it("keeps a taken dose as taken even after the missed threshold", async () => {
    mockData.listDoseRecordsByPatientRange.mockResolvedValue([
      {
        patientId: "patient-1",
        medicationId: "med-1",
        scheduledAt: new Date("2026-02-02T08:00:00.000Z"),
        recordedByType: "PATIENT"
      }
    ]);

    const result = await getScheduleWithStatus(
      "patient-1",
      new Date("2026-02-02T00:00:00.000Z"),
      new Date("2026-02-03T00:00:00.000Z"),
      "UTC",
      new Date("2026-02-02T10:00:00.000Z")
    );

    expect(result[0]?.effectiveStatus).toBe("taken");
  });
});
