import { prisma } from "./prisma";
import { Prisma, type PrnDoseRecord, type RecordedByType } from "@prisma/client";

export type PrnDoseRecordCreateInput = {
  patientId: string;
  medicationId: string;
  clientMutationId?: string;
  takenAt: Date;
  quantityTaken: number;
  actorType: RecordedByType;
};

export async function createPrnDoseRecord(input: PrnDoseRecordCreateInput): Promise<PrnDoseRecord> {
  return prisma.prnDoseRecord.create({
    data: {
      patientId: input.patientId,
      medicationId: input.medicationId,
      clientMutationId: input.clientMutationId ?? null,
      takenAt: input.takenAt,
      quantityTaken: input.quantityTaken,
      actorType: input.actorType
    }
  });
}

export async function createPrnDoseRecordIdempotent(
  input: PrnDoseRecordCreateInput
): Promise<{ record: PrnDoseRecord; created: boolean }> {
  if (!input.clientMutationId) {
    return { record: await createPrnDoseRecord(input), created: true };
  }

  const key = {
    patientId_clientMutationId: {
      patientId: input.patientId,
      clientMutationId: input.clientMutationId
    }
  };
  const existing = await prisma.prnDoseRecord.findUnique({ where: key });
  if (existing) {
    return { record: existing, created: false };
  }

  try {
    return { record: await createPrnDoseRecord(input), created: true };
  } catch (error) {
    if (!(error instanceof Prisma.PrismaClientKnownRequestError) || error.code !== "P2002") {
      throw error;
    }
    const raced = await prisma.prnDoseRecord.findUnique({ where: key });
    if (!raced) throw error;
    return { record: raced, created: false };
  }
}

export async function getPrnDoseRecordByClientMutationId(input: {
  patientId: string;
  clientMutationId: string;
}): Promise<PrnDoseRecord | null> {
  return prisma.prnDoseRecord.findUnique({
    where: {
      patientId_clientMutationId: {
        patientId: input.patientId,
        clientMutationId: input.clientMutationId
      }
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
