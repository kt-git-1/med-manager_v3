import type { PrnDoseRecord, RecordedByType } from "@prisma/client";
import {
  createPrnDoseRecord,
  deletePrnDoseRecordById,
  getPrnDoseRecordById,
  listPrnDoseRecordsByPatientRange
} from "../repositories/prnDoseRecordRepo";
import { createDoseRecordEvent } from "../repositories/doseRecordEventRepo";
import { getMedicationRecordForPatient } from "../repositories/medicationRepo";
import { getPatientRecordById } from "../repositories/patientRepo";
import { applyInventoryDeltaForDoseRecord } from "./medicationService";
import { notifyCaregiversOfDoseRecord } from "./pushNotificationService";
import { getLocalDateKey } from "./scheduleService";

export type PrnDoseRecordCreateInput = {
  patientId: string;
  medicationId: string;
  takenAt?: Date;
  quantityTaken?: number;
  actorType: RecordedByType;
};

export type PrnDoseRecordCreateResult =
  | { record: PrnDoseRecord }
  | { error: "not_found" | "not_prn" };

export async function createPrnRecord(
  input: PrnDoseRecordCreateInput
): Promise<PrnDoseRecordCreateResult | null> {
  const medication = await getMedicationRecordForPatient(input.patientId, input.medicationId);
  if (!medication) {
    return { error: "not_found" };
  }
  if (!medication.isPrn) {
    return { error: "not_prn" };
  }

  const quantityTaken = input.quantityTaken ?? medication.doseCountPerIntake;

  const record = await createPrnDoseRecord({
    patientId: input.patientId,
    medicationId: input.medicationId,
    takenAt: input.takenAt ?? new Date(),
    quantityTaken,
    actorType: input.actorType
  });

  const patient = await getPatientRecordById(record.patientId);
  if (patient) {
    await createDoseRecordEvent({
      patientId: record.patientId,
      scheduledAt: record.takenAt,
      takenAt: record.takenAt,
      withinTime: true,
      displayName: patient.displayName,
      medicationName: medication.name,
      isPrn: true
    });

    // Fire-and-forget: send push notifications to linked caregivers
    void notifyCaregiversOfDoseRecord({
      patientId: record.patientId,
      displayName: patient.displayName,
      medicationName: medication.name,
      isPrn: true,
      takenAt: record.takenAt,
    });
  }

  await applyInventoryDeltaForDoseRecord({
    patientId: input.patientId,
    medicationId: input.medicationId,
    delta: -quantityTaken,
    reason: "TAKEN_CREATE"
  });

  return { record };
}

export async function deletePrnRecord(input: {
  patientId: string;
  prnRecordId: string;
}): Promise<PrnDoseRecord | null> {
  const existing = await getPrnDoseRecordById({
    patientId: input.patientId,
    prnRecordId: input.prnRecordId
  });
  if (!existing) {
    return null;
  }
  const medication = await getMedicationRecordForPatient(
    existing.patientId,
    existing.medicationId
  );
  const deleted = await deletePrnDoseRecordById({ prnRecordId: input.prnRecordId });
  if (medication) {
    await applyInventoryDeltaForDoseRecord({
      patientId: existing.patientId,
      medicationId: existing.medicationId,
      delta: existing.quantityTaken,
      reason: "TAKEN_DELETE"
    });
  }
  return deleted;
}

export async function listPrnHistoryItemsByRange(input: {
  patientId: string;
  from: Date;
  to: Date;
  timeZone: string;
}) {
  const records = await listPrnDoseRecordsByPatientRange(input);
  const countByDay: Record<string, number> = {};
  const items = records.map((record) => {
    const dateKey = getLocalDateKey(record.takenAt, input.timeZone);
    countByDay[dateKey] = (countByDay[dateKey] ?? 0) + 1;
    return {
      medicationId: record.medicationId,
      medicationName: record.medication.name,
      takenAt: record.takenAt.toISOString(),
      quantityTaken: record.quantityTaken,
      actorType: record.actorType
    };
  });

  return {
    items,
    countByDay
  };
}
