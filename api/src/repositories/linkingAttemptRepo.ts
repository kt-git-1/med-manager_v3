import { prisma } from "./prisma";

export type LinkingAttemptRecord = {
  id: string;
  patientId: string;
  attemptCount: number;
  lockedUntil: Date | null;
  updatedAt: Date;
};

export async function getLinkingAttempt(patientId: string): Promise<LinkingAttemptRecord | null> {
  return prisma.linkingAttempt.findUnique({ where: { patientId } });
}

export async function upsertLinkingAttempt(input: {
  patientId: string;
  attemptCount: number;
  lockedUntil: Date | null;
}): Promise<LinkingAttemptRecord> {
  return prisma.linkingAttempt.upsert({
    where: { patientId: input.patientId },
    update: {
      attemptCount: input.attemptCount,
      lockedUntil: input.lockedUntil
    },
    create: {
      patientId: input.patientId,
      attemptCount: input.attemptCount,
      lockedUntil: input.lockedUntil
    }
  });
}

export async function resetLinkingAttempt(patientId: string) {
  return prisma.linkingAttempt.updateMany({
    where: { patientId },
    data: { attemptCount: 0, lockedUntil: null }
  });
}
