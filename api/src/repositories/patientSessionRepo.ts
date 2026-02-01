import { prisma } from "./prisma";

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

export async function createPatientSessionRecord(input: {
  patientId: string;
  tokenHash: string;
  issuedAt: Date;
  lastRotatedAt?: Date | null;
  expiresAt?: Date | null;
}): Promise<PatientSessionRecord> {
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

export async function revokePatientSessionByTokenHash(tokenHash: string, revokedAt: Date) {
  return prisma.patientSession.updateMany({
    where: { tokenHash },
    data: { revokedAt }
  });
}

export async function revokePatientSessionsByPatientId(patientId: string, revokedAt: Date) {
  return prisma.patientSession.updateMany({
    where: { patientId, revokedAt: null },
    data: { revokedAt }
  });
}
