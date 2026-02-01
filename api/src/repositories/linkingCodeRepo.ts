import { prisma } from "./prisma";
import type { Prisma } from "@prisma/client";

export type LinkingCodeRecord = {
  id: string;
  patientId: string;
  codeHash: string;
  expiresAt: Date;
  usedAt: Date | null;
  issuedBy: string;
  createdAt: Date;
};

export function invalidateActiveLinkingCodes(
  patientId: string,
  usedAt: Date
): Prisma.PrismaPromise<Prisma.BatchPayload> {
  return prisma.linkingCode.updateMany({
    where: {
      patientId,
      usedAt: null
    },
    data: { usedAt }
  });
}

export async function findLinkingCodeByHash(codeHash: string) {
  return prisma.linkingCode.findFirst({
    where: { codeHash },
    orderBy: { createdAt: "desc" }
  });
}

export function markLinkingCodeUsed(
  id: string,
  usedAt: Date
): Prisma.PrismaPromise<LinkingCodeRecord> {
  return prisma.linkingCode.update({
    where: { id },
    data: { usedAt }
  });
}

export function createLinkingCodeRecord(input: {
  patientId: string;
  codeHash: string;
  expiresAt: Date;
  issuedBy: string;
}): Prisma.PrismaPromise<LinkingCodeRecord> {
  return prisma.linkingCode.create({ data: input });
}
