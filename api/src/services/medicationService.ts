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
  Medication,
  Regimen
} from "@prisma/client";
import { prisma } from "../repositories/prisma";
import { getPatientRecordById } from "../repositories/patientRepo";
import { computeRefillPlan } from "../lib/computeRefillPlan";

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
  inventoryEnabled?: boolean;
  inventoryQuantity?: number;
  inventoryUpdatedAt?: Date;
};

export type MedicationUpdateInput = Partial<MedicationCreateInput> & {
  isActive?: boolean;
};

export async function createMedication(input: MedicationCreateInput) {
  const inventoryOverrides = buildInventoryOverrides(input.inventoryCount);
  return createMedicationRecord({ ...input, ...inventoryOverrides });
}

export async function listMedications(patientId: string): Promise<Medication[]> {
  return listMedicationRecords(patientId);
}

export async function getMedication(id: string): Promise<Medication | null> {
  return getMedicationRecord(id);
}

export async function updateMedication(id: string, input: MedicationUpdateInput) {
  const existing = await getMedicationRecord(id);
  const inventoryOverrides =
    existing && !existing.inventoryEnabled
      ? buildInventoryOverrides(input.inventoryCount)
      : {};
  return updateMedicationRecord(id, { ...input, ...inventoryOverrides });
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
  dailyPlannedUnits: number | null;
  daysRemaining: number | null;
  refillDueDate: string | null;
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

function buildInventoryItem(
  medication: Medication,
  plan?: { dailyPlannedUnits: number | null; daysRemaining: number | null; refillDueDate: string | null }
): InventoryItem {
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
    out: state === "OUT",
    dailyPlannedUnits: plan?.dailyPlannedUnits ?? null,
    daysRemaining: plan?.daysRemaining ?? null,
    refillDueDate: plan?.refillDueDate ?? null
  };
}

function buildInventoryItemWithPlan(medication: Medication, regimens: Regimen[]): InventoryItem {
  const plan = computeRefillPlan({
    inventoryEnabled: medication.inventoryEnabled,
    inventoryQuantity: medication.inventoryQuantity,
    doseCountPerIntake: medication.doseCountPerIntake,
    regimens: regimens.map((regimen) => ({
      startDate: regimen.startDate,
      endDate: regimen.endDate,
      times: regimen.times,
      daysOfWeek: regimen.daysOfWeek,
      enabled: regimen.enabled
    }))
  });
  return buildInventoryItem(medication, plan);
}

function buildInventoryOverrides(inventoryCount?: number | null) {
  if (inventoryCount === undefined || inventoryCount === null) {
    return {};
  }
  const clamped = Math.max(0, inventoryCount);
  return {
    inventoryEnabled: true,
    inventoryQuantity: clamped,
    inventoryUpdatedAt: new Date()
  };
}

export async function listMedicationInventory(patientId: string): Promise<InventoryItem[]> {
  const [medications, regimens] = await Promise.all([
    listMedicationRecords(patientId),
    prisma.regimen.findMany({ where: { patientId } })
  ]);
  const regimenMap = new Map<string, Regimen[]>();
  for (const regimen of regimens) {
    const existing = regimenMap.get(regimen.medicationId) ?? [];
    existing.push(regimen);
    regimenMap.set(regimen.medicationId, existing);
  }
  return medications.map((medication) =>
    buildInventoryItemWithPlan(medication, regimenMap.get(medication.id) ?? [])
  );
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

  const regimens = await prisma.regimen.findMany({ where: { medicationId: updated.id } });
  return buildInventoryItemWithPlan(updated, regimens);
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

  const regimens = await prisma.regimen.findMany({ where: { medicationId: updated.id } });
  return buildInventoryItemWithPlan(updated, regimens);
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
