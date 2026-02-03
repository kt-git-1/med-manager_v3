import type { DoseRecord, RecordedByType } from "@prisma/client";
import {
  deleteDoseRecordByKey,
  getDoseRecordByKey,
  listDoseRecordsByPatientRange,
  upsertDoseRecord,
  type DoseRecordKey
} from "../repositories/doseRecordRepo";

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

export async function listDoseRecordsForPatientRange(input: {
  patientId: string;
  from: Date;
  to: Date;
}): Promise<DoseRecord[]> {
  return listDoseRecordsByPatientRange(input);
}
