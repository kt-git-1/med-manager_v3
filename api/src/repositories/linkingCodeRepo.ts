import { prisma } from "./prisma";

export type LinkingCodeRecord = {
  id: string;
  patientId: string;
  codeHash: string;
  expiresAt: Date;
  usedAt: Date | null;
  issuedBy: string;
  createdAt: Date;
};

export async function invalidateActiveLinkingCodes(patientId: string, usedAt: Date) {
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

export async function markLinkingCodeUsed(id: string, usedAt: Date) {
  return prisma.linkingCode.update({
    where: { id },
    data: { usedAt }
  });
}

export async function createLinkingCodeRecord(input: {
  patientId: string;
  codeHash: string;
  expiresAt: Date;
  issuedBy: string;
}): Promise<LinkingCodeRecord> {
  return prisma.linkingCode.create({ data: input });
}
