import type { DoseRecord, RecordedByType } from "@prisma/client";
import {
  deleteDoseRecordByKey,
  getDoseRecordByKey,
  listDoseRecordsByPatientRange,
  upsertDoseRecord,
  type DoseRecordKey
} from "../repositories/doseRecordRepo";
import { createDoseRecordEvent } from "../repositories/doseRecordEventRepo";
import { getMedicationRecordForPatient } from "../repositories/medicationRepo";
import { getPatientRecordById } from "../repositories/patientRepo";
import { assertCaregiverPatientScope } from "../middleware/auth";
import {
  applyInventoryDeltaForDoseRecord,
  assertInventoryAvailableForMedication
} from "./medicationService";
import { notifyCaregiversOfDoseTaken } from "./pushNotificationService";
import { resolveSlot } from "./scheduleResponse";
import { getLocalDateKey } from "./scheduleService";
import { DEFAULT_TIMEZONE, DOSE_MISSED_WINDOW_MS } from "../constants";

export type DoseRecordCreateInput = DoseRecordKey & {
  recordedByType: RecordedByType;
  recordedById?: string | null;
};

export async function createDoseRecordIdempotent(
  input: DoseRecordCreateInput
): Promise<DoseRecord> {
  const existing = await getDoseRecordByKey({
    patientId: input.patientId,
    medicationId: input.medicationId,
    scheduledAt: input.scheduledAt
  });
  if (existing) {
    return existing;
  }

  const medication = await getMedicationRecordForPatient(input.patientId, input.medicationId);
  if (medication) {
    assertInventoryAvailableForMedication(medication, medication.doseCountPerIntake);
  }

  const record = await upsertDoseRecord(input);
  const patient = await getPatientRecordById(record.patientId);
  if (!patient) {
    return record;
  }

  const withinTime =
    record.takenAt.getTime() <= record.scheduledAt.getTime() + DOSE_MISSED_WINDOW_MS;

  const dateKey = getLocalDateKey(record.scheduledAt, DEFAULT_TIMEZONE);
  const slot = resolveSlot(record.scheduledAt.toISOString(), DEFAULT_TIMEZONE);
  const [doseEvent] = await Promise.all([
    createDoseRecordEvent({
      patientId: record.patientId,
      scheduledAt: record.scheduledAt,
      takenAt: record.takenAt,
      withinTime,
      displayName: patient.displayName,
      medicationName: medication?.name,
      isPrn: medication?.isPrn ?? false
    }),
    medication
      ? applyInventoryDeltaForDoseRecord({
          patientId: record.patientId,
          medicationId: record.medicationId,
          delta: -medication.doseCountPerIntake,
          reason: "TAKEN_CREATE"
        })
      : Promise.resolve()
  ]);

  // Preserve the existing guarantee that push is attempted only after the inventory update.
  await notifyCaregiversOfDoseTaken({
    patientId: record.patientId,
    displayName: patient.displayName,
    date: dateKey,
    slot: slot ?? "morning",
    doseEventId: doseEvent.id,
    excludeCaregiverId:
      input.recordedByType === "CAREGIVER" ? (input.recordedById ?? undefined) : undefined,
    withinTime,
    isPrn: medication?.isPrn ?? false
  });

  return record;
}

export async function deleteDoseRecord(key: DoseRecordKey): Promise<DoseRecord | null> {
  const [existing, medication] = await Promise.all([
    getDoseRecordByKey(key),
    getMedicationRecordForPatient(key.patientId, key.medicationId)
  ]);
  if (!existing) {
    return null;
  }
  const deleted = await deleteDoseRecordByKey(key);
  if (medication) {
    await applyInventoryDeltaForDoseRecord({
      patientId: existing.patientId,
      medicationId: existing.medicationId,
      delta: medication.doseCountPerIntake,
      reason: "TAKEN_DELETE"
    });
  }
  return deleted;
}

export async function createCaregiverDoseRecord(input: {
  caregiverUserId: string;
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
}): Promise<DoseRecord> {
  await assertCaregiverPatientScope(input.caregiverUserId, input.patientId);
  return createDoseRecordIdempotent({
    patientId: input.patientId,
    medicationId: input.medicationId,
    scheduledAt: input.scheduledAt,
    recordedByType: "CAREGIVER",
    recordedById: input.caregiverUserId
  });
}

export async function deleteCaregiverDoseRecord(input: {
  caregiverUserId: string;
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
}): Promise<DoseRecord | null> {
  await assertCaregiverPatientScope(input.caregiverUserId, input.patientId);
  return deleteDoseRecord({
    patientId: input.patientId,
    medicationId: input.medicationId,
    scheduledAt: input.scheduledAt
  });
}

export async function listDoseRecordsForPatientRange(input: {
  patientId: string;
  from: Date;
  to: Date;
}): Promise<DoseRecord[]> {
  return listDoseRecordsByPatientRange(input);
}
