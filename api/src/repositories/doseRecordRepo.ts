import { prisma } from "./prisma";
import type { DoseRecord, RecordedByType } from "@prisma/client";

export type DoseRecordKey = {
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
};

export type DoseRecordCreateInput = DoseRecordKey & {
  recordedByType: RecordedByType;
  recordedById?: string | null;
  consumedQuantity: number;
};

export async function upsertDoseRecord(input: DoseRecordCreateInput): Promise<DoseRecord> {
  const { patientId, medicationId, scheduledAt, recordedByType, recordedById, consumedQuantity } =
    input;
  return prisma.doseRecord.upsert({
    where: {
      patientId_medicationId_scheduledAt: { patientId, medicationId, scheduledAt }
    },
    create: {
      patientId,
      medicationId,
      scheduledAt,
      recordedByType,
      recordedById: recordedById ?? null,
      consumedQuantity
    },
    update: {
      takenAt: new Date(),
      recordedByType,
      recordedById: recordedById ?? null,
      consumedQuantity,
      cancelledAt: null,
      cancelledByType: null,
      cancelledById: null,
      inventoryRestoredAt: null
    }
  });
}

export async function getDoseRecordByKey(key: DoseRecordKey): Promise<DoseRecord | null> {
  return prisma.doseRecord.findUnique({
    where: {
      patientId_medicationId_scheduledAt: key
    }
  });
}

export async function deleteDoseRecordByKey(key: DoseRecordKey): Promise<DoseRecord> {
  return prisma.doseRecord.delete({
    where: {
      patientId_medicationId_scheduledAt: key
    }
  });
}

export async function listDoseRecordsByPatientRange(input: {
  patientId: string;
  from: Date;
  to: Date;
}): Promise<DoseRecord[]> {
  return prisma.doseRecord.findMany({
    where: {
      patientId: input.patientId,
      cancelledAt: null,
      scheduledAt: {
        gte: input.from,
        lt: input.to
      }
    }
  });
}

export async function listCancelledDoseRecordsByPatientRange(input: {
  patientId: string;
  from: Date;
  to: Date;
}): Promise<DoseRecord[]> {
  return prisma.doseRecord.findMany({
    where: {
      patientId: input.patientId,
      cancelledAt: { not: null },
      scheduledAt: {
        gte: input.from,
        lt: input.to
      }
    }
  });
}
