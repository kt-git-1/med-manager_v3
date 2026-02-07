import { describe, expect, it, vi } from "vitest";
import { createDoseRecordIdempotent, deleteDoseRecord } from "../../src/services/doseRecordService";

type DoseRecord = {
  id: string;
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
  takenAt: Date;
  recordedByType: "PATIENT" | "CAREGIVER";
  recordedById: string | null;
  createdAt: Date;
  updatedAt: Date;
};

const store = new Map<string, DoseRecord>();

const mockData = vi.hoisted(() => ({
  medication: {
    id: "med-1",
    patientId: "patient-1",
    name: "Medication A",
    dosageText: "1 tablet",
    doseCountPerIntake: 2,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    notes: null,
    startDate: new Date("2026-02-01T00:00:00.000Z"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: true,
    inventoryQuantity: 5,
    inventoryLowThreshold: 2,
    inventoryUpdatedAt: null,
    inventoryLastAlertState: null,
    isActive: true,
    isArchived: false,
    createdAt: new Date("2026-02-01T00:00:00.000Z"),
    updatedAt: new Date("2026-02-01T00:00:00.000Z")
  },
  adjustments: [] as Array<{ delta: number }>
}));

function buildKey(input: { patientId: string; medicationId: string; scheduledAt: Date }) {
  return `${input.patientId}:${input.medicationId}:${input.scheduledAt.toISOString()}`;
}

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
      create: vi.fn(async (input: { data: { delta: number } }) => {
        mockData.adjustments.push({ delta: input.data.delta });
        return { id: `adj-${mockData.adjustments.length}`, ...input.data };
      })
    },
    regimen: {
      findMany: vi.fn(async () => [])
    },
    inventoryAlertEvent: {
      create: vi.fn(async () => ({
        id: "alert-1"
      }))
    },
    $transaction: async (callback: (tx: typeof prisma) => unknown) => callback(prisma)
  };
  return { prisma };
});

vi.mock("../../src/repositories/doseRecordRepo", () => ({
  upsertDoseRecord: async (input: {
    patientId: string;
    medicationId: string;
    scheduledAt: Date;
    recordedByType: "PATIENT" | "CAREGIVER";
    recordedById?: string | null;
  }) => {
    const key = buildKey(input);
    const existing = store.get(key);
    if (existing) {
      return existing;
    }
    const now = new Date();
    const record: DoseRecord = {
      id: `dose-${store.size + 1}`,
      patientId: input.patientId,
      medicationId: input.medicationId,
      scheduledAt: input.scheduledAt,
      takenAt: now,
      recordedByType: input.recordedByType,
      recordedById: input.recordedById ?? null,
      createdAt: now,
      updatedAt: now
    };
    store.set(key, record);
    return record;
  },
  getDoseRecordByKey: async (key: { patientId: string; medicationId: string; scheduledAt: Date }) => {
    return store.get(buildKey(key)) ?? null;
  },
  deleteDoseRecordByKey: async (key: { patientId: string; medicationId: string; scheduledAt: Date }) => {
    const record = store.get(buildKey(key));
    if (!record) {
      throw new Error("Dose record not found");
    }
    store.delete(buildKey(key));
    return record;
  }
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordById: async () => ({
    id: "patient-1",
    caregiverId: "caregiver-1",
    displayName: "Test Patient",
    createdAt: new Date(),
    updatedAt: new Date()
  })
}));

vi.mock("../../src/repositories/doseRecordEventRepo", () => ({
  createDoseRecordEvent: async () => ({
    id: "event-1",
    patientId: "patient-1",
    scheduledAt: new Date(),
    takenAt: new Date(),
    withinTime: true,
    displayName: "Test Patient",
    createdAt: new Date()
  })
}));

describe("inventory adjustments on TAKEN delete", () => {
  it("increments inventory when a TAKEN record is deleted", async () => {
    store.clear();
    mockData.medication.inventoryQuantity = 5;
    mockData.adjustments.length = 0;
    const scheduledAt = new Date("2026-02-02T08:00:00.000Z");

    await createDoseRecordIdempotent({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt,
      recordedByType: "CAREGIVER",
      recordedById: "caregiver-1"
    });
    expect(mockData.medication.inventoryQuantity).toBe(3);

    await deleteDoseRecord({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt
    });
    expect(mockData.medication.inventoryQuantity).toBe(5);
    expect(mockData.adjustments).toHaveLength(2);
  });
});
