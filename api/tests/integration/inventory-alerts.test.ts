import { describe, expect, it, vi } from "vitest";
import { updateMedicationInventorySettings } from "../../src/services/medicationService";

const mockData = vi.hoisted(() => ({
  medication: {
    id: "med-1",
    patientId: "patient-1",
    name: "Medication A",
    dosageText: "1 tablet",
    doseCountPerIntake: 1,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    notes: null,
    startDate: new Date("2026-02-01T00:00:00.000Z"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: true,
    inventoryQuantity: 6,
    inventoryLowThreshold: 5,
    inventoryUpdatedAt: null,
    inventoryLastAlertState: null,
    isActive: true,
    isArchived: false,
    createdAt: new Date("2026-02-01T00:00:00.000Z"),
    updatedAt: new Date("2026-02-01T00:00:00.000Z")
  },
  alertEvents: [] as Array<{ type: string }>
}));

vi.mock("../../src/repositories/prisma", () => {
  const prisma = {
    medication: {
      findFirst: vi.fn(async (input: { where: { id: string; patientId: string } }) => {
        if (
          input.where.id === mockData.medication.id &&
          input.where.patientId === mockData.medication.patientId
        ) {
          return mockData.medication;
        }
        return null;
      }),
      update: vi.fn(async (input: { data: Record<string, unknown> }) => {
        mockData.medication = { ...mockData.medication, ...input.data };
        return mockData.medication;
      })
    },
    medicationInventoryAdjustment: {
      create: vi.fn(async (input: { data: Record<string, unknown> }) => ({
        id: "adj-1",
        ...input.data
      }))
    },
    regimen: {
      findMany: vi.fn(async () => [])
    },
    inventoryAlertEvent: {
      create: vi.fn(async (input: { data: { type: string } }) => {
        mockData.alertEvents.push({ type: input.data.type });
        return { id: `alert-${mockData.alertEvents.length}`, ...input.data };
      })
    },
    $transaction: async (callback: (tx: typeof prisma) => unknown) => callback(prisma)
  };
  return { prisma };
});

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordById: async () => ({
    id: "patient-1",
    caregiverId: "caregiver-1",
    displayName: "Test Patient",
    createdAt: new Date(),
    updatedAt: new Date()
  })
}));

describe("inventory alert emission", () => {
  it("emits low/out events only on state transitions", async () => {
    mockData.alertEvents.length = 0;
    mockData.medication.inventoryQuantity = 6;
    mockData.medication.inventoryLowThreshold = 5;
    mockData.medication.inventoryLastAlertState = null;

    await updateMedicationInventorySettings({
      patientId: "patient-1",
      medicationId: "med-1",
      update: { inventoryQuantity: 4 }
    });
    expect(mockData.alertEvents).toHaveLength(1);
    expect(mockData.alertEvents[0]?.type).toBe("LOW");

    await updateMedicationInventorySettings({
      patientId: "patient-1",
      medicationId: "med-1",
      update: { inventoryQuantity: 3 }
    });
    expect(mockData.alertEvents).toHaveLength(1);

    await updateMedicationInventorySettings({
      patientId: "patient-1",
      medicationId: "med-1",
      update: { inventoryQuantity: 0 }
    });
    expect(mockData.alertEvents).toHaveLength(2);
    expect(mockData.alertEvents[1]?.type).toBe("OUT");
  });
});
