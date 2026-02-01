import { prisma } from "./prisma";
import type { Prisma } from "@prisma/client";

export type PatientSessionRecord = {
  id: string;
  patientId: string;
  tokenHash: string;
  issuedAt: Date;
  expiresAt: Date | null;
  lastRotatedAt: Date | null;
  revokedAt: Date | null;
};

export async function findActivePatientSessionByTokenHash(
  tokenHash: string
): Promise<PatientSessionRecord | null> {
  return prisma.patientSession.findFirst({
    where: {
      tokenHash,
      revokedAt: null
    }
  });
}

export function createPatientSessionRecord(input: {
  patientId: string;
  tokenHash: string;
  issuedAt: Date;
  lastRotatedAt?: Date | null;
  expiresAt?: Date | null;
}): Prisma.PrismaPromise<PatientSessionRecord> {
  return prisma.patientSession.create({
    data: {
      patientId: input.patientId,
      tokenHash: input.tokenHash,
      issuedAt: input.issuedAt,
      lastRotatedAt: input.lastRotatedAt ?? null,
      expiresAt: input.expiresAt ?? null
    }
  });
}

export function revokePatientSessionByTokenHash(
  tokenHash: string,
  revokedAt: Date
): Prisma.PrismaPromise<Prisma.BatchPayload> {
  return prisma.patientSession.updateMany({
    where: { tokenHash },
    data: { revokedAt }
  });
}

export function revokePatientSessionsByPatientId(
  patientId: string,
  revokedAt: Date
): Prisma.PrismaPromise<Prisma.BatchPayload> {
  return prisma.patientSession.updateMany({
    where: { patientId, revokedAt: null },
    data: { revokedAt }
  });
}
