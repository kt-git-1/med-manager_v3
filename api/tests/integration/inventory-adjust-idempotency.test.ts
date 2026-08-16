import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
  medication: {
    id: "med-1",
    patientId: "patient-1",
    name: "Medication A",
    dosageText: "1 tablet",
    doseCountPerIntake: 1,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    notes: null,
    isPrn: false,
    prnInstructions: null,
    startDate: new Date("2026-01-01T00:00:00.000Z"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: false,
    inventoryQuantity: 3,
    inventoryLowThreshold: 0,
    inventoryUpdatedAt: null as Date | null,
    inventoryLastAlertState: null as null | "NONE" | "LOW" | "OUT",
    isActive: true,
    isArchived: false,
    createdAt: new Date("2026-01-01T00:00:00.000Z"),
    updatedAt: new Date("2026-01-01T00:00:00.000Z")
  },
  adjustments: new Map<string, { id: string; patientId: string; clientMutationId: string }>()
}));

function applyMedicationUpdate(data: Record<string, unknown>) {
  const quantity = data.inventoryQuantity;
  if (typeof quantity === "number") {
    state.medication.inventoryQuantity = quantity;
  } else if (quantity && typeof quantity === "object") {
    const operation = quantity as { increment?: number; decrement?: number };
    state.medication.inventoryQuantity += operation.increment ?? 0;
    state.medication.inventoryQuantity -= operation.decrement ?? 0;
  }
  if (data.inventoryLowThreshold !== undefined) {
    state.medication.inventoryLowThreshold = data.inventoryLowThreshold as number;
  }
  if (data.inventoryLastAlertState !== undefined) {
    state.medication.inventoryLastAlertState = data.inventoryLastAlertState as
      "NONE" | "LOW" | "OUT";
  }
  return state.medication;
}

const prismaMock = vi.hoisted(() => {
  const client = {
    regimen: { findMany: vi.fn(async () => []) },
    medication: {
      findFirst: vi.fn(async () => state.medication),
      updateMany: vi.fn(async () => ({ count: 1 })),
      update: vi.fn(async ({ data }: { data: Record<string, unknown> }) =>
        applyMedicationUpdate(data)
      )
    },
    medicationInventoryAdjustment: {
      findUnique: vi.fn(
        async ({
          where
        }: {
          where: { patientId_clientMutationId: { clientMutationId: string } };
        }) => state.adjustments.get(where.patientId_clientMutationId.clientMutationId) ?? null
      ),
      create: vi.fn(async ({ data }: { data: { patientId: string; clientMutationId: string } }) => {
        const created = {
          id: `adjustment-${state.adjustments.size + 1}`,
          patientId: data.patientId,
          clientMutationId: data.clientMutationId
        };
        state.adjustments.set(data.clientMutationId, created);
        return created;
      })
    },
    inventoryAlertEvent: { create: vi.fn() },
    $transaction: vi.fn(async (callback: (tx: unknown) => Promise<unknown>) => callback(client))
  };
  return client;
});

vi.mock("../../src/repositories/prisma", () => ({ prisma: prismaMock }));
vi.mock("../../src/repositories/medicationRepo", () => ({
  archiveMedicationRecord: vi.fn(),
  createMedicationRecord: vi.fn(),
  getMedicationRecord: vi.fn(),
  getMedicationRecordForPatient: vi.fn(async () => state.medication),
  listMedicationRecords: vi.fn(),
  updateMedicationRecord: vi.fn()
}));
vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordById: vi.fn(async () => null)
}));

import { adjustMedicationInventory } from "../../src/services/medicationService";

describe("inventory adjustment idempotency", () => {
  beforeEach(() => {
    state.medication.inventoryQuantity = 3;
    state.medication.inventoryLowThreshold = 0;
    state.medication.inventoryLastAlertState = null;
    state.adjustments.clear();
    vi.clearAllMocks();
  });

  it("applies one delta for a replayed client mutation and applies a distinct operation", async () => {
    const base = {
      patientId: "patient-1",
      medicationId: "med-1",
      reason: "REFILL" as const,
      actorType: "CAREGIVER" as const,
      actorId: "caregiver-1",
      delta: 5
    };

    const first = await adjustMedicationInventory({
      ...base,
      clientMutationId: "11111111-1111-4111-8111-111111111111"
    });
    const replay = await adjustMedicationInventory({
      ...base,
      clientMutationId: "11111111-1111-4111-8111-111111111111"
    });
    const distinct = await adjustMedicationInventory({
      ...base,
      clientMutationId: "22222222-2222-4222-8222-222222222222"
    });

    expect(first?.inventoryQuantity).toBe(8);
    expect(replay?.inventoryQuantity).toBe(8);
    expect(distinct?.inventoryQuantity).toBe(13);
    expect(state.adjustments.size).toBe(2);
    expect(prismaMock.medicationInventoryAdjustment.create).toHaveBeenCalledTimes(2);
  });
});
