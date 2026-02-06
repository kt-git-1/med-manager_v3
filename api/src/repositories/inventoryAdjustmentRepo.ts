import type {
  InventoryAdjustmentActorType,
  InventoryAdjustmentReason,
  MedicationInventoryAdjustment
} from "@prisma/client";
import { prisma } from "./prisma";

export type InventoryAdjustmentCreateInput = {
  patientId: string;
  medicationId: string;
  delta: number;
  reason: InventoryAdjustmentReason;
  actorType: InventoryAdjustmentActorType;
  actorId?: string | null;
};

export async function createInventoryAdjustment(
  input: InventoryAdjustmentCreateInput
): Promise<MedicationInventoryAdjustment> {
  return prisma.medicationInventoryAdjustment.create({
    data: {
      patientId: input.patientId,
      medicationId: input.medicationId,
      delta: input.delta,
      reason: input.reason,
      actorType: input.actorType,
      actorId: input.actorId ?? null
    }
  });
}
