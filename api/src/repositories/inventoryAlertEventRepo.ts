import type { InventoryAlertEvent, InventoryAlertType } from "@prisma/client";
import { prisma } from "./prisma";

export type InventoryAlertEventCreateInput = {
  patientId: string;
  medicationId: string;
  type: InventoryAlertType;
  remaining: number;
  threshold: number;
};

export async function createInventoryAlertEvent(
  input: InventoryAlertEventCreateInput
): Promise<InventoryAlertEvent> {
  return prisma.inventoryAlertEvent.create({
    data: {
      patientId: input.patientId,
      medicationId: input.medicationId,
      type: input.type,
      remaining: input.remaining,
      threshold: input.threshold
    }
  });
}
