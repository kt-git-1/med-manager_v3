import { prisma } from "./prisma";
import { Prisma, type DoseRecord, type RecordedByType } from "@prisma/client";

export type DoseRecordKey = {
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
};

export type DoseRecordCreateInput = DoseRecordKey & {
  recordedByType: RecordedByType;
  recordedById?: string | null;
};

export async function createDoseRecordIfAbsent(
  input: DoseRecordCreateInput
): Promise<{ record: DoseRecord; created: boolean }> {
  const { patientId, medicationId, scheduledAt, recordedByType, recordedById } = input;
  try {
    const record = await prisma.doseRecord.create({
      data: {
        patientId,
        medicationId,
        scheduledAt,
        recordedByType,
        recordedById: recordedById ?? null
      }
    });
    return { record, created: true };
  } catch (error) {
    if (!(error instanceof Prisma.PrismaClientKnownRequestError) || error.code !== "P2002") {
      throw error;
    }
    const record = await getDoseRecordByKey({ patientId, medicationId, scheduledAt });
    if (!record) throw error;
    return { record, created: false };
  }
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
      scheduledAt: {
        gte: input.from,
        lt: input.to
      }
    }
  });
}
