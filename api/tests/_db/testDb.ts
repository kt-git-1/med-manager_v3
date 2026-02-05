import { prisma } from "../../src/repositories/prisma";

export async function setupTestDb() {
  await prisma.$connect();
}

export async function teardownTestDb() {
  await prisma.$disconnect();
}

export async function createCaregiverPatientLinkFixture(input: {
  caregiverId: string;
  patientId: string;
  displayName: string;
}) {
  await prisma.patient.upsert({
    where: { id: input.patientId },
    create: {
      id: input.patientId,
      caregiverId: input.caregiverId,
      displayName: input.displayName
    },
    update: {
      caregiverId: input.caregiverId,
      displayName: input.displayName
    }
  });

  await prisma.caregiverPatientLink.upsert({
    where: { patientId: input.patientId },
    create: {
      patientId: input.patientId,
      caregiverId: input.caregiverId,
      status: "ACTIVE"
    },
    update: {
      caregiverId: input.caregiverId,
      status: "ACTIVE",
      revokedAt: null
    }
  });
}
