import { prisma } from "./prisma";
import type { RegimenCreateInput, RegimenUpdateInput } from "../services/regimenService";

export async function createRegimenRecord(input: RegimenCreateInput) {
  return prisma.regimen.create({
    data: {
      ...input,
      daysOfWeek: input.daysOfWeek ?? []
    }
  });
}

export async function updateRegimenRecord(id: string, input: RegimenUpdateInput) {
  return prisma.regimen.update({
    where: { id },
    data: {
      ...input,
      daysOfWeek: input.daysOfWeek
    }
  });
}

export async function stopRegimenRecord(id: string) {
  return prisma.regimen.update({
    where: { id },
    data: { enabled: false }
  });
}

export async function getRegimenRecord(id: string) {
  return prisma.regimen.findUnique({ where: { id } });
}
