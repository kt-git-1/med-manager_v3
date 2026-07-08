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
import { DEFAULT_TIMEZONE, INTL_PARSE_LOCALE } from "../constants";
import { InsufficientInventoryError } from "../errors/insufficientInventoryError";

const DEFAULT_INVENTORY_LOW_THRESHOLD_DAYS = 3;

export type MedicationCreateInput = {
  patientId: string;
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
  notes?: string;
  isPrn?: boolean;
  prnInstructions?: string | null;
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
  const base = buildInventoryOverrides(input.inventoryCount);
  if (!base) {
    return createMedicationRecord(input);
  }
  return createMedicationRecord({ ...input, ...base });
}

export async function listMedications(patientId: string): Promise<Medication[]> {
  return listMedicationRecords(patientId);
}

export async function listActiveRegimens(patientId: string): Promise<Regimen[]> {
  return prisma.regimen.findMany({ where: { patientId, enabled: true } });
}

export async function getMedication(id: string): Promise<Medication | null> {
  return getMedicationRecord(id);
}

export async function updateMedication(id: string, input: MedicationUpdateInput) {
  const existing = await getMedicationRecord(id);
  if (!existing) {
    return updateMedicationRecord(id, input);
  }
  if (existing.inventoryEnabled) {
    return updateMedicationRecord(id, input);
  }
  const base = buildInventoryOverrides(input.inventoryCount);
  if (!base) {
    return updateMedicationRecord(id, input);
  }
  return updateMedicationRecord(id, { ...input, ...base });
}

export async function archiveMedication(id: string) {
  return archiveMedicationRecord(id);
}

export type InventoryItem = {
  medicationId: string;
  name: string;
  isPrn: boolean;
  doseCountPerIntake: number;
  inventoryEnabled: boolean;
  inventoryQuantity: number;
  inventoryLowThreshold: number;
  periodEnded: boolean;
  low: boolean;
  out: boolean;
  dailyPlannedUnits: number | null;
  nextSevenDaysPlannedUnits: number | null;
  daysRemaining: number | null;
  refillDueDate: string | null;
};

export type InventoryUpdateInput = {
  inventoryEnabled?: boolean;
  inventoryQuantity?: number;
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

function computeInventoryState(
  quantity: number,
  threshold: number,
  daysRemaining: number | null
): InventoryAlertState {
  if (daysRemaining !== null) {
    if (daysRemaining <= 0) {
      return "OUT";
    }
  } else if (quantity <= 0) {
    return "OUT";
  }
  if (threshold > 0) {
    if (daysRemaining !== null) {
      if (daysRemaining <= threshold) {
        return "LOW";
      }
    } else if (quantity < threshold) {
      return "LOW";
    }
  }
  return "NONE";
}

const INVENTORY_TZ = DEFAULT_TIMEZONE;

function inventoryDateKey(date: Date, tz: string) {
  const formatter = new Intl.DateTimeFormat(INTL_PARSE_LOCALE, {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });
  const parts = formatter.formatToParts(date);
  const values: Record<string, string> = {};
  for (const part of parts) {
    if (part.type !== "literal") {
      values[part.type] = part.value;
    }
  }
  return `${values.year}-${values.month}-${values.day}`;
}

function isPeriodEnded(endDate?: Date | null, now: Date = new Date()) {
  if (!endDate) {
    return false;
  }
  return inventoryDateKey(now, INVENTORY_TZ) >= inventoryDateKey(endDate, INVENTORY_TZ);
}

function buildInventoryItem(
  medication: Medication,
  plan?: {
    dailyPlannedUnits: number | null;
    nextSevenDaysPlannedUnits: number | null;
    daysRemaining: number | null;
    refillDueDate: string | null;
  }
): InventoryItem {
  const enabled = medication.inventoryEnabled;
  const quantity = medication.inventoryQuantity;
  const threshold = inventoryLowThresholdFor(enabled);
  const state = enabled
    ? computeInventoryState(quantity, threshold, plan?.daysRemaining ?? null)
    : "NONE";
  return {
    medicationId: medication.id,
    name: medication.name,
    isPrn: medication.isPrn,
    doseCountPerIntake: medication.doseCountPerIntake,
    inventoryEnabled: enabled,
    inventoryQuantity: quantity,
    inventoryLowThreshold: threshold,
    periodEnded: isPeriodEnded(medication.endDate),
    low: state === "LOW",
    out: state === "OUT",
    dailyPlannedUnits: plan?.dailyPlannedUnits ?? null,
    nextSevenDaysPlannedUnits: plan?.nextSevenDaysPlannedUnits ?? null,
    daysRemaining: plan?.daysRemaining ?? null,
    refillDueDate: plan?.refillDueDate ?? null
  };
}

function mapRegimensForPlan(regimens: Regimen[]) {
  return regimens.map((regimen) => ({
    startDate: regimen.startDate,
    endDate: regimen.endDate,
    times: regimen.times,
    daysOfWeek: regimen.daysOfWeek,
    enabled: regimen.enabled
  }));
}

function buildInventoryItemWithPlan(medication: Medication, regimens: Regimen[]): InventoryItem {
  const plan = computeRefillPlan({
    inventoryEnabled: medication.inventoryEnabled,
    inventoryQuantity: medication.inventoryQuantity,
    doseCountPerIntake: medication.doseCountPerIntake,
    regimens: mapRegimensForPlan(regimens)
  });
  return buildInventoryItem(medication, plan);
}

function buildInventoryOverrides(inventoryCount?: number | null) {
  if (inventoryCount === undefined || inventoryCount === null) {
    return null;
  }
  const clamped = Math.max(0, inventoryCount);
  return {
    inventoryEnabled: true,
    inventoryQuantity: clamped,
    inventoryLowThreshold: DEFAULT_INVENTORY_LOW_THRESHOLD_DAYS,
    inventoryUpdatedAt: new Date()
  };
}

function inventoryLowThresholdFor(enabled: boolean): number {
  return enabled ? DEFAULT_INVENTORY_LOW_THRESHOLD_DAYS : 0;
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
  const nextQuantity = Math.max(0, input.update.inventoryQuantity ?? medication.inventoryQuantity);
  const nextThreshold = inventoryLowThresholdFor(nextEnabled);
  const regimens = await prisma.regimen.findMany({ where: { medicationId: medication.id } });
  const plan = computeRefillPlan({
    inventoryEnabled: nextEnabled,
    inventoryQuantity: nextQuantity,
    doseCountPerIntake: medication.doseCountPerIntake,
    regimens: mapRegimensForPlan(regimens)
  });
  const nextState = nextEnabled
    ? computeInventoryState(nextQuantity, nextThreshold, plan.daysRemaining)
    : "NONE";
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
  const regimens = await prisma.regimen.findMany({ where: { medicationId: medication.id } });
  const now = new Date();

  const patient = await getPatientRecordById(medication.patientId);
  const updated = await prisma.$transaction(async (tx) => {
    let quantityUpdated;
    if (delta < 0) {
      quantityUpdated = await tx.medication.updateMany({
        where: {
          id: medication.id,
          patientId: input.patientId,
          inventoryQuantity: { gte: Math.abs(delta) }
        },
        data: {
          inventoryQuantity: { decrement: Math.abs(delta) },
          inventoryUpdatedAt: now
        }
      });
      if (quantityUpdated.count === 0) {
        throw new InsufficientInventoryError();
      }
    } else {
      await tx.medication.update({
        where: { id: medication.id },
        data: {
          inventoryQuantity:
            input.absoluteQuantity !== undefined
              ? Math.max(0, input.absoluteQuantity)
              : { increment: delta },
          inventoryUpdatedAt: now
        }
      });
    }

    const quantityMedication = await tx.medication.findFirst({
      where: { id: medication.id, patientId: input.patientId }
    });
    if (!quantityMedication) {
      throw new Error("Medication not found after inventory update");
    }

    const plan = computeRefillPlan({
      inventoryEnabled: quantityMedication.inventoryEnabled,
      inventoryQuantity: quantityMedication.inventoryQuantity,
      doseCountPerIntake: quantityMedication.doseCountPerIntake,
      regimens: mapRegimensForPlan(regimens)
    });
    const nextState = quantityMedication.inventoryEnabled
      ? computeInventoryState(
          quantityMedication.inventoryQuantity,
          inventoryLowThresholdFor(quantityMedication.inventoryEnabled),
          plan.daysRemaining
        )
      : "NONE";
    const previousState = quantityMedication.inventoryLastAlertState ?? "NONE";
    const shouldEmitAlert =
      quantityMedication.inventoryEnabled && nextState !== previousState && nextState !== "NONE";

    const updatedMedication = await tx.medication.update({
      where: { id: medication.id },
      data: {
        inventoryLowThreshold: inventoryLowThresholdFor(quantityMedication.inventoryEnabled),
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
          patientId: quantityMedication.patientId,
          medicationId: quantityMedication.id,
          type: nextState as InventoryAlertType,
          remaining: quantityMedication.inventoryQuantity,
          threshold: inventoryLowThresholdFor(quantityMedication.inventoryEnabled),
          patientDisplayName: patient?.displayName ?? null,
          medicationName: quantityMedication.name
        }
      });
    }
    return updatedMedication;
  });

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

export function assertInventoryAvailableForMedication(
  medication: Pick<Medication, "inventoryEnabled" | "inventoryQuantity">,
  requiredQuantity: number
): void {
  if (medication.inventoryEnabled && medication.inventoryQuantity < requiredQuantity) {
    throw new InsufficientInventoryError();
  }
}
