import { prisma } from "./prisma";

export type PatientRecord = {
  id: string;
  caregiverId: string;
  displayName: string;
  createdAt: Date;
  updatedAt: Date;
};

export async function createPatientRecord(input: {
  caregiverId: string;
  displayName: string;
}): Promise<PatientRecord> {
  return prisma.patient.create({ data: input });
}

export async function listPatientRecordsByCaregiver(
  caregiverId: string
): Promise<PatientRecord[]> {
  return prisma.patient.findMany({
    where: { caregiverId },
    orderBy: { createdAt: "desc" }
  });
}

export async function getPatientRecordById(patientId: string): Promise<PatientRecord | null> {
  return prisma.patient.findUnique({ where: { id: patientId } });
}

export async function getPatientRecordForCaregiver(
  patientId: string,
  caregiverId: string
): Promise<PatientRecord | null> {
  return prisma.patient.findFirst({
    where: { id: patientId, caregiverId }
  });
}
