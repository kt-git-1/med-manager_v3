import { beforeEach, describe, expect, it, vi } from "vitest";

const queryRaw = vi.fn();
const updateMany = vi.fn(async () => ({ count: 1 }));
const adjustmentCreateMany = vi.fn(async () => ({ count: 1 }));
const alertCreateMany = vi.fn(async () => ({ count: 1 }));
const regimenFindMany = vi.fn(async () => []);
const transaction = vi.fn(async (callback: (tx: unknown) => Promise<void>) =>
  callback({
    $queryRaw: queryRaw,
    medication: { updateMany },
    medicationInventoryAdjustment: { createMany: adjustmentCreateMany },
    inventoryAlertEvent: { createMany: alertCreateMany }
  })
);

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    regimen: { findMany: regimenFindMany },
    $transaction: transaction
  }
}));

const { applyInventoryDeltasForDoseRecords } = await import("../../src/services/medicationService");

describe("applyInventoryDeltasForDoseRecords", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    regimenFindMany.mockResolvedValue([]);
  });

  it("does not access the database when no inventory delta exists", async () => {
    await applyInventoryDeltasForDoseRecords({
      patientId: "patient-1",
      patientDisplayName: "Test Patient",
      deltas: []
    });

    expect(regimenFindMany).not.toHaveBeenCalled();
    expect(transaction).not.toHaveBeenCalled();
  });

  it("aggregates duplicate medication deltas and writes one adjustment", async () => {
    queryRaw.mockResolvedValue([
      {
        id: "med-1",
        patientId: "patient-1",
        name: "Medication",
        doseCountPerIntake: 1,
        inventoryQuantity: 7,
        inventoryLastAlertState: "NONE"
      }
    ]);

    await applyInventoryDeltasForDoseRecords({
      patientId: "patient-1",
      patientDisplayName: "Test Patient",
      deltas: [
        { medicationId: "med-1", quantity: 1 },
        { medicationId: "med-1", quantity: 2 }
      ]
    });

    expect(transaction).toHaveBeenCalledTimes(1);
    expect(adjustmentCreateMany).toHaveBeenCalledWith({
      data: [
        expect.objectContaining({
          patientId: "patient-1",
          medicationId: "med-1",
          delta: -3,
          reason: "TAKEN_CREATE",
          actorType: "SYSTEM"
        })
      ]
    });
  });

  it("preserves LOW alert transitions in the batched path", async () => {
    queryRaw.mockResolvedValue([
      {
        id: "med-1",
        patientId: "patient-1",
        name: "Medication",
        doseCountPerIntake: 1,
        inventoryQuantity: 2,
        inventoryLastAlertState: "NONE"
      }
    ]);

    await applyInventoryDeltasForDoseRecords({
      patientId: "patient-1",
      patientDisplayName: "Test Patient",
      deltas: [{ medicationId: "med-1", quantity: 1 }]
    });

    expect(updateMany).toHaveBeenCalledWith({
      where: { id: { in: ["med-1"] }, patientId: "patient-1" },
      data: expect.objectContaining({ inventoryLastAlertState: "LOW" })
    });
    expect(alertCreateMany).toHaveBeenCalledWith({
      data: [
        expect.objectContaining({
          patientId: "patient-1",
          medicationId: "med-1",
          type: "LOW",
          remaining: 2,
          patientDisplayName: "Test Patient"
        })
      ]
    });
  });

  it("rolls back when an inventory row can no longer be decremented", async () => {
    queryRaw.mockResolvedValue([]);

    await expect(
      applyInventoryDeltasForDoseRecords({
        patientId: "patient-1",
        patientDisplayName: "Test Patient",
        deltas: [{ medicationId: "med-1", quantity: 1 }]
      })
    ).rejects.toMatchObject({ code: "insufficient_inventory", statusCode: 409 });

    expect(adjustmentCreateMany).not.toHaveBeenCalled();
    expect(alertCreateMany).not.toHaveBeenCalled();
  });
});
