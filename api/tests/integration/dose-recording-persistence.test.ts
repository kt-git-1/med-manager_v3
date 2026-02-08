import { beforeEach, describe, expect, it, vi } from "vitest";
import { generateScheduleForPatientWithStatus } from "../../src/services/scheduleService";

const prismaMock = vi.hoisted(() => ({
  prisma: {
    medication: { findMany: vi.fn() },
    regimen: { findMany: vi.fn() }
  }
}));

const doseRecordRepoMock = vi.hoisted(() => ({
  listDoseRecordsByPatientRange: vi.fn()
}));

vi.mock("../../src/repositories/prisma", () => prismaMock);
vi.mock("../../src/repositories/doseRecordRepo", () => doseRecordRepoMock);

describe("dose recording persistence integration", () => {
  beforeEach(() => {
    prismaMock.prisma.medication.findMany.mockReset();
    prismaMock.prisma.regimen.findMany.mockReset();
    doseRecordRepoMock.listDoseRecordsByPatientRange.mockReset();
  });

  it("derives missed/pending without persisted records", async () => {
    prismaMock.prisma.medication.findMany.mockResolvedValue([
      {
        id: "med-1",
        patientId: "patient-1",
        name: "Medication A",
        dosageText: "10mg",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg",
        isActive: true,
        isArchived: false,
        startDate: new Date("2026-02-01T00:00:00.000Z"),
        endDate: null
      }
    ]);
    prismaMock.prisma.regimen.findMany.mockResolvedValue([
      {
        id: "reg-1",
        patientId: "patient-1",
        medicationId: "med-1",
        timezone: "Asia/Tokyo",
        startDate: new Date("2026-02-01T00:00:00.000Z"),
        endDate: null,
        times: ["08:00", "09:00"],
        daysOfWeek: [],
        enabled: true
      }
    ]);
    doseRecordRepoMock.listDoseRecordsByPatientRange.mockResolvedValue([]);

    const from = new Date("2026-02-01T15:00:00.000Z");
    const to = new Date("2026-02-02T15:00:00.000Z");
    const now = new Date("2026-02-02T00:30:00.000Z");

    const schedule = await generateScheduleForPatientWithStatus({
      patientId: "patient-1",
      from,
      to,
      now
    });

    expect(doseRecordRepoMock.listDoseRecordsByPatientRange).toHaveBeenCalledWith({
      patientId: "patient-1",
      from,
      to
    });
    expect(schedule).toHaveLength(2);

    const statusByTime = new Map(schedule.map((dose) => [dose.scheduledAt, dose.effectiveStatus]));
    expect(statusByTime.get(new Date("2026-02-01T23:00:00.000Z").toISOString())).toBe("missed");
    expect(statusByTime.get(new Date("2026-02-02T00:00:00.000Z").toISOString())).toBe("pending");
  });
});
