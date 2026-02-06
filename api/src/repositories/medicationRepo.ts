import { prisma } from "./prisma";
import type { MedicationCreateInput, MedicationUpdateInput } from "../services/medicationService";

export async function createMedicationRecord(input: MedicationCreateInput) {
  return prisma.medication.create({ data: input });
}

export async function listMedicationRecords(patientId: string) {
  return prisma.medication.findMany({
    where: { patientId, isArchived: false },
    orderBy: { createdAt: "desc" }
  });
}

export async function getMedicationRecord(id: string) {
  return prisma.medication.findUnique({ where: { id } });
}

export async function getMedicationRecordForPatient(patientId: string, medicationId: string) {
  return prisma.medication.findFirst({
    where: { id: medicationId, patientId }
  });
}

export async function updateMedicationRecord(id: string, input: MedicationUpdateInput) {
  return prisma.medication.update({ where: { id }, data: input });
}

export async function archiveMedicationRecord(id: string) {
  return prisma.medication.update({
    where: { id },
    data: { isArchived: true, isActive: false }
  });
}
