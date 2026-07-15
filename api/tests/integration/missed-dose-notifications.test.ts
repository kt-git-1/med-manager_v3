import { beforeEach, describe, expect, it, vi } from "vitest";

const regimenFindManyMock = vi.fn();
const patientFindManyMock = vi.fn();
const doseRecordCountMock = vi.fn();
const generateScheduleMock = vi.fn();
const timelineMock = vi.fn();
const notifyMock = vi.fn();

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    regimen: { findMany: (...args: unknown[]) => regimenFindManyMock(...args) },
    patient: { findMany: (...args: unknown[]) => patientFindManyMock(...args) },
    doseRecord: { count: (...args: unknown[]) => doseRecordCountMock(...args) }
  }
}));

vi.mock("../../src/services/scheduleService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../src/services/scheduleService")>();
  return {
    ...actual,
    generateScheduleForPatientWithStatus: (...args: unknown[]) => generateScheduleMock(...args)
  };
});

vi.mock("../../src/services/patientSlotTimeService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../src/services/patientSlotTimeService")>();
  return {
    ...actual,
    getPatientSlotTimeTimeline: (...args: unknown[]) => timelineMock(...args)
  };
});

vi.mock("../../src/services/pushNotificationService", () => ({
  notifyCaregiversOfDoseMissed: (...args: unknown[]) => notifyMock(...args)
}));

vi.mock("../../src/logging/logger", () => ({ log: vi.fn() }));

const patient = {
  id: "patient-1",
  displayName: "花子",
  morningTime: "08:00",
  noonTime: "12:00",
  eveningTime: "19:00",
  bedtimeTime: "22:00"
};

function missedDose(medicationId: string) {
  return {
    patientId: "patient-1",
    medicationId,
    scheduledAt: "2026-07-15T03:00:00.000Z",
    medicationSnapshot: {
      name: "薬",
      dosageText: "1錠",
      doseCountPerIntake: 1,
      dosageStrengthValue: 1,
      dosageStrengthUnit: "錠"
    },
    effectiveStatus: "missed" as const
  };
}

describe("sendDueMissedDoseNotifications", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    regimenFindManyMock.mockResolvedValue([{ patientId: "patient-1" }]);
    patientFindManyMock.mockResolvedValue([patient]);
    timelineMock.mockResolvedValue([
      {
        effectiveFrom: new Date("2026-01-01T00:00:00.000Z"),
        slotTimes: {
          morning: "08:00",
          noon: "12:00",
          evening: "19:00",
          bedtime: "22:00"
        }
      }
    ]);
    doseRecordCountMock.mockResolvedValue(0);
    notifyMock.mockResolvedValue(undefined);
  });

  it("groups medications in one time slot into a single caregiver notification", async () => {
    generateScheduleMock.mockResolvedValue([missedDose("med-1"), missedDose("med-2")]);
    const { sendDueMissedDoseNotifications } =
      await import("../../src/services/missedDoseNotificationService");

    const result = await sendDueMissedDoseNotifications(new Date("2026-07-15T04:05:00.000Z"));

    expect(notifyMock).toHaveBeenCalledTimes(1);
    expect(notifyMock).toHaveBeenCalledWith({
      patientId: "patient-1",
      displayName: "花子",
      date: "2026-07-15",
      slot: "noon"
    });
    expect(result).toEqual({
      scannedPatients: 1,
      dueSlots: 1,
      notifiedSlots: 1,
      skippedRecordedSlots: 0
    });
  });

  it("rechecks records immediately before sending and skips a completed slot", async () => {
    generateScheduleMock.mockResolvedValue([missedDose("med-1")]);
    doseRecordCountMock.mockResolvedValue(1);
    const { sendDueMissedDoseNotifications } =
      await import("../../src/services/missedDoseNotificationService");

    const result = await sendDueMissedDoseNotifications(new Date("2026-07-15T04:05:00.000Z"));

    expect(notifyMock).not.toHaveBeenCalled();
    expect(result.skippedRecordedSlots).toBe(1);
    expect(result.notifiedSlots).toBe(0);
  });

  it("does nothing when no patient has an enabled scheduled regimen", async () => {
    regimenFindManyMock.mockResolvedValue([]);
    const { sendDueMissedDoseNotifications } =
      await import("../../src/services/missedDoseNotificationService");

    const result = await sendDueMissedDoseNotifications(new Date("2026-07-15T04:05:00.000Z"));

    expect(patientFindManyMock).not.toHaveBeenCalled();
    expect(notifyMock).not.toHaveBeenCalled();
    expect(result.scannedPatients).toBe(0);
  });
});
