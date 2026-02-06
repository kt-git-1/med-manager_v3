import {
  archiveMedicationRecord,
  createMedicationRecord,
  getMedicationRecord,
  getMedicationRecordForPatient,
  listMedicationRecords,
  updateMedicationRecord
} from "../repositories/medicationRepo";
import type {
  InventoryAlertState,
  InventoryAlertType,
  InventoryAdjustmentActorType,
  InventoryAdjustmentReason,
  Medication
} from "@prisma/client";
import { prisma } from "../repositories/prisma";
import { getPatientRecordById } from "../repositories/patientRepo";

export type MedicationCreateInput = {
  patientId: string;
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
  notes?: string;
  startDate: Date;
  endDate?: Date;
  inventoryCount?: number;
  inventoryUnit?: string;
};

export type MedicationUpdateInput = Partial<MedicationCreateInput> & {
  isActive?: boolean;
};

export async function createMedication(input: MedicationCreateInput) {
  return createMedicationRecord(input);
}

export async function listMedications(patientId: string): Promise<Medication[]> {
  return listMedicationRecords(patientId);
}

export async function getMedication(id: string): Promise<Medication | null> {
  return getMedicationRecord(id);
}

export async function updateMedication(id: string, input: MedicationUpdateInput) {
  return updateMedicationRecord(id, input);
}

export async function archiveMedication(id: string) {
  return archiveMedicationRecord(id);
}

export type InventoryItem = {
  medicationId: string;
  name: string;
  inventoryEnabled: boolean;
  inventoryQuantity: number;
  inventoryLowThreshold: number;
  low: boolean;
  out: boolean;
};

export type InventoryUpdateInput = {
  inventoryEnabled?: boolean;
  inventoryQuantity?: number;
  inventoryLowThreshold?: number;
};

export type InventoryAdjustInput = {
  patientId: string;
  medicationId: string;
  reason: InventoryAdjustmentReason;
  actorType: InventoryAdjustmentActorType;
  actorId?: string | null;
  delta?: number;
  absoluteQuantity?: number;
};

function computeInventoryState(quantity: number, threshold: number): InventoryAlertState {
  if (quantity === 0) {
    return "OUT";
  }
  if (quantity < threshold) {
    return "LOW";
  }
  return "NONE";
}

function buildInventoryItem(medication: Medication): InventoryItem {
  const enabled = medication.inventoryEnabled;
  const quantity = medication.inventoryQuantity;
  const threshold = medication.inventoryLowThreshold;
  const state = enabled ? computeInventoryState(quantity, threshold) : "NONE";
  return {
    medicationId: medication.id,
    name: medication.name,
    inventoryEnabled: enabled,
    inventoryQuantity: quantity,
    inventoryLowThreshold: threshold,
    low: state === "LOW",
    out: state === "OUT"
  };
}

export async function listMedicationInventory(patientId: string): Promise<InventoryItem[]> {
  const medications = await listMedicationRecords(patientId);
  return medications.map(buildInventoryItem);
}

export async function updateMedicationInventorySettings(input: {
  patientId: string;
  medicationId: string;
  update: InventoryUpdateInput;
}): Promise<InventoryItem | null> {
  const medication = await getMedicationRecordForPatient(input.patientId, input.medicationId);
  if (!medication) {
    return null;
  }
  const nextEnabled = input.update.inventoryEnabled ?? medication.inventoryEnabled;
  const nextQuantity = Math.max(
    0,
    input.update.inventoryQuantity ?? medication.inventoryQuantity
  );
  const nextThreshold = Math.max(
    0,
    input.update.inventoryLowThreshold ?? medication.inventoryLowThreshold
  );
  const nextState = nextEnabled ? computeInventoryState(nextQuantity, nextThreshold) : "NONE";
  const previousState = medication.inventoryLastAlertState ?? "NONE";
  const shouldEmitAlert = nextEnabled && nextState !== previousState && nextState !== "NONE";
  const now = new Date();

  const patient = await getPatientRecordById(medication.patientId);
  const updated = await prisma.$transaction(async (tx) => {
    const updatedMedication = await tx.medication.update({
      where: { id: medication.id },
      data: {
        inventoryEnabled: nextEnabled,
        inventoryQuantity: nextQuantity,
        inventoryLowThreshold: nextThreshold,
        inventoryUpdatedAt: now,
        inventoryLastAlertState: nextState
      }
    });
    if (shouldEmitAlert) {
      await tx.inventoryAlertEvent.create({
        data: {
          patientId: medication.patientId,
          medicationId: medication.id,
          type: nextState as InventoryAlertType,
          remaining: nextQuantity,
          threshold: nextThreshold,
          patientDisplayName: patient?.displayName ?? null,
          medicationName: medication.name
        }
      });
    }
    return updatedMedication;
  });

  return buildInventoryItem(updated);
}

export async function adjustMedicationInventory(
  input: InventoryAdjustInput
): Promise<InventoryItem | null> {
  const medication = await getMedicationRecordForPatient(input.patientId, input.medicationId);
  if (!medication) {
    return null;
  }
  const baseQuantity = medication.inventoryQuantity;
  const delta =
    input.absoluteQuantity !== undefined
      ? input.absoluteQuantity - baseQuantity
      : (input.delta ?? 0);
  const nextQuantity = Math.max(0, baseQuantity + delta);
  const nextState = medication.inventoryEnabled
    ? computeInventoryState(nextQuantity, medication.inventoryLowThreshold)
    : "NONE";
  const previousState = medication.inventoryLastAlertState ?? "NONE";
  const shouldEmitAlert =
    medication.inventoryEnabled && nextState !== previousState && nextState !== "NONE";
  const now = new Date();

  const patient = await getPatientRecordById(medication.patientId);
  const updated = await prisma.$transaction(async (tx) => {
    const updatedMedication = await tx.medication.update({
      where: { id: medication.id },
      data: {
        inventoryQuantity: nextQuantity,
        inventoryUpdatedAt: now,
        inventoryLastAlertState: nextState
      }
    });
    await tx.medicationInventoryAdjustment.create({
      data: {
        patientId: input.patientId,
        medicationId: input.medicationId,
        delta,
        reason: input.reason,
        actorType: input.actorType,
        actorId: input.actorId ?? null
      }
    });
    if (shouldEmitAlert) {
      await tx.inventoryAlertEvent.create({
        data: {
          patientId: medication.patientId,
          medicationId: medication.id,
          type: nextState as InventoryAlertType,
          remaining: nextQuantity,
          threshold: medication.inventoryLowThreshold,
          patientDisplayName: patient?.displayName ?? null,
          medicationName: medication.name
        }
      });
    }
    return updatedMedication;
  });

  return buildInventoryItem(updated);
}

export async function applyInventoryDeltaForDoseRecord(input: {
  patientId: string;
  medicationId: string;
  delta: number;
  reason: InventoryAdjustmentReason;
}): Promise<void> {
  const medication = await getMedicationRecordForPatient(input.patientId, input.medicationId);
  if (!medication || !medication.inventoryEnabled) {
    return;
  }
  await adjustMedicationInventory({
    patientId: input.patientId,
    medicationId: input.medicationId,
    delta: input.delta,
    reason: input.reason,
    actorType: "SYSTEM",
    actorId: null
  });
}
