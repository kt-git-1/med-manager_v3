import type { DoseRecord, RecordedByType } from "@prisma/client";
import {
  deleteDoseRecordByKey,
  getDoseRecordByKey,
  listDoseRecordsByPatientRange,
  upsertDoseRecord,
  type DoseRecordKey
} from "../repositories/doseRecordRepo";
import { assertCaregiverPatientScope } from "../middleware/auth";

export type DoseRecordCreateInput = DoseRecordKey & {
  recordedByType: RecordedByType;
  recordedById?: string | null;
};

export async function createDoseRecordIdempotent(
  input: DoseRecordCreateInput
): Promise<DoseRecord> {
  return upsertDoseRecord(input);
}

export async function deleteDoseRecord(
  key: DoseRecordKey
): Promise<DoseRecord | null> {
  const existing = await getDoseRecordByKey(key);
  if (!existing) {
    return null;
  }
  return deleteDoseRecordByKey(key);
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
