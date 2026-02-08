import type { DoseRecordEvent } from "@prisma/client";
import { prisma } from "./prisma";

export type DoseRecordEventCreateInput = {
  patientId: string;
  scheduledAt: Date;
  takenAt: Date;
  withinTime: boolean;
  displayName: string;
  medicationName?: string | null;
  isPrn?: boolean;
};

export async function createDoseRecordEvent(
  input: DoseRecordEventCreateInput
): Promise<DoseRecordEvent> {
  return prisma.doseRecordEvent.create({
    data: input
  });
}

export async function listDoseRecordEventsByPatient(input: {
  patientId: string;
  limit?: number;
}): Promise<DoseRecordEvent[]> {
  return prisma.doseRecordEvent.findMany({
    where: { patientId: input.patientId },
    orderBy: { createdAt: "desc" },
    take: input.limit
  });
}
