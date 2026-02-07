import { prisma } from "./prisma";
import type { PrnDoseRecord, RecordedByType } from "@prisma/client";

export type PrnDoseRecordCreateInput = {
  patientId: string;
  medicationId: string;
  takenAt: Date;
  quantityTaken: number;
  actorType: RecordedByType;
};

export async function createPrnDoseRecord(
  input: PrnDoseRecordCreateInput
): Promise<PrnDoseRecord> {
  return prisma.prnDoseRecord.create({
    data: {
      patientId: input.patientId,
      medicationId: input.medicationId,
      takenAt: input.takenAt,
      quantityTaken: input.quantityTaken,
      actorType: input.actorType
    }
  });
}

export async function getPrnDoseRecordById(input: {
  patientId: string;
  prnRecordId: string;
}): Promise<PrnDoseRecord | null> {
  return prisma.prnDoseRecord.findFirst({
    where: { id: input.prnRecordId, patientId: input.patientId }
  });
}

export async function deletePrnDoseRecordById(input: {
  prnRecordId: string;
}): Promise<PrnDoseRecord> {
  return prisma.prnDoseRecord.delete({
    where: { id: input.prnRecordId }
  });
}

export async function listPrnDoseRecordsByPatientRange(input: {
  patientId: string;
  from: Date;
  to: Date;
}): Promise<(PrnDoseRecord & { medication: { name: string } })[]> {
  return prisma.prnDoseRecord.findMany({
    where: {
      patientId: input.patientId,
      takenAt: {
        gte: input.from,
        lt: input.to
      }
    },
    include: {
      medication: {
        select: { name: true }
      }
    },
    orderBy: { takenAt: "asc" }
  });
}
