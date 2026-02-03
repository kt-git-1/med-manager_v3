import { prisma } from "./prisma";
import type { DayOfWeek } from "@prisma/client";
import type { RegimenCreateInput, RegimenUpdateInput } from "../services/regimenService";

function toPrismaDays(days: string[] | undefined): DayOfWeek[] | undefined {
  if (!days) {
    return undefined;
  }
  return days as DayOfWeek[];
}

export async function createRegimenRecord(input: RegimenCreateInput) {
  return prisma.regimen.create({
    data: {
      ...input,
      daysOfWeek: toPrismaDays(input.daysOfWeek) ?? []
    }
  });
}

export async function updateRegimenRecord(id: string, input: RegimenUpdateInput) {
  return prisma.regimen.update({
    where: { id },
    data: {
      ...input,
      daysOfWeek: toPrismaDays(input.daysOfWeek)
    }
  });
}

export async function listRegimensByMedicationId(medicationId: string) {
  return prisma.regimen.findMany({
    where: { medicationId },
    orderBy: { createdAt: "desc" }
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
