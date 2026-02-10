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

export async function createMultiPatientFixture(
  caregiverId: string,
  count: number
): Promise<void> {
  for (let i = 0; i < count; i++) {
    const patientId = `patient-fixture-${caregiverId}-${i}`;
    await createCaregiverPatientLinkFixture({
      caregiverId,
      patientId,
      displayName: `Fixture Patient ${i + 1}`
    });
  }
}

export async function createRevokedPatientFixture(input: {
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
      status: "REVOKED",
      revokedAt: new Date()
    },
    update: {
      caregiverId: input.caregiverId,
      status: "REVOKED",
      revokedAt: new Date()
    }
  });
}

export async function createEntitlementFixture(input: {
  caregiverId: string;
  productId: string;
  originalTransactionId: string;
  transactionId?: string;
  environment?: string;
}) {
  return prisma.caregiverEntitlement.upsert({
    where: { originalTransactionId: input.originalTransactionId },
    create: {
      caregiverId: input.caregiverId,
      productId: input.productId,
      status: "ACTIVE",
      originalTransactionId: input.originalTransactionId,
      transactionId: input.transactionId ?? input.originalTransactionId,
      purchasedAt: new Date(),
      environment: input.environment ?? "Sandbox"
    },
    update: {
      caregiverId: input.caregiverId,
      productId: input.productId,
      status: "ACTIVE",
      transactionId: input.transactionId ?? input.originalTransactionId
    }
  });
}
