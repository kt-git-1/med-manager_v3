import { prisma } from "./prisma";

export type CaregiverPatientLinkRecord = {
  id: string;
  caregiverId: string;
  patientId: string;
  status: "ACTIVE" | "REVOKED";
  revokedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
};

export async function createCaregiverPatientLink(input: {
  caregiverId: string;
  patientId: string;
}): Promise<CaregiverPatientLinkRecord> {
  return prisma.caregiverPatientLink.create({
    data: {
      caregiverId: input.caregiverId,
      patientId: input.patientId,
      status: "ACTIVE"
    }
  });
}

export async function getActiveLinkForCaregiverPatient(
  caregiverId: string,
  patientId: string
): Promise<CaregiverPatientLinkRecord | null> {
  return prisma.caregiverPatientLink.findFirst({
    where: {
      caregiverId,
      patientId,
      status: "ACTIVE",
      revokedAt: null
    }
  });
}
