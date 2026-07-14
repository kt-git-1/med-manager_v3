import { beforeEach, describe, expect, it, vi } from "vitest";

const { getPatientRecordForCaregiverMock, deleteMocks, txClient } = vi.hoisted(() => {
  const getPatientRecordForCaregiverMock = vi.fn();
  const deleteMocks = {
    patientSlotTimeRevision: vi.fn(),
    inventoryAlertEvent: vi.fn(),
    medicationInventoryAdjustment: vi.fn(),
    prnDoseRecord: vi.fn(),
    doseRecord: vi.fn(),
    doseRecordEvent: vi.fn(),
    regimen: vi.fn(),
    medication: vi.fn(),
    patientSession: vi.fn(),
    linkingCode: vi.fn(),
    linkingAttempt: vi.fn(),
    caregiverPatientLink: vi.fn(),
    patient: vi.fn()
  };
  const txClient = {
    patientSlotTimeRevision: { deleteMany: deleteMocks.patientSlotTimeRevision },
    inventoryAlertEvent: { deleteMany: deleteMocks.inventoryAlertEvent },
    medicationInventoryAdjustment: { deleteMany: deleteMocks.medicationInventoryAdjustment },
    prnDoseRecord: { deleteMany: deleteMocks.prnDoseRecord },
    doseRecord: { deleteMany: deleteMocks.doseRecord },
    doseRecordEvent: { deleteMany: deleteMocks.doseRecordEvent },
    regimen: { deleteMany: deleteMocks.regimen },
    medication: { deleteMany: deleteMocks.medication },
    patientSession: { deleteMany: deleteMocks.patientSession },
    linkingCode: { deleteMany: deleteMocks.linkingCode },
    linkingAttempt: { deleteMany: deleteMocks.linkingAttempt },
    caregiverPatientLink: { deleteMany: deleteMocks.caregiverPatientLink },
    patient: { delete: deleteMocks.patient }
  };
  return { getPatientRecordForCaregiverMock, deleteMocks, txClient };
});

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: getPatientRecordForCaregiverMock,
  listPatientRecordsByCaregiver: vi.fn()
}));

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    $transaction: vi.fn(async (callback: (tx: typeof txClient) => Promise<unknown>) =>
      callback(txClient)
    )
  }
}));

import { deletePatientForCaregiver } from "../../src/services/linkingService";

describe("deletePatientForCaregiver", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getPatientRecordForCaregiverMock.mockResolvedValue({
      id: "patient-1",
      caregiverId: "caregiver-1"
    });
    Object.values(deleteMocks).forEach((mock) => mock.mockResolvedValue({ count: 1 }));
  });

  it("deletes slot-time revisions before deleting the patient", async () => {
    await expect(deletePatientForCaregiver("caregiver-1", "patient-1")).resolves.toEqual({
      deleted: true
    });

    expect(deleteMocks.patientSlotTimeRevision).toHaveBeenCalledWith({
      where: { patientId: "patient-1" }
    });
    expect(deleteMocks.patient).toHaveBeenCalledWith({
      where: { id: "patient-1" }
    });
    expect(deleteMocks.patientSlotTimeRevision.mock.invocationCallOrder[0]).toBeLessThan(
      deleteMocks.patient.mock.invocationCallOrder[0]
    );
  });
});
