import { prisma } from "./prisma";

export type EntitlementRecord = {
  id: string;
  caregiverId: string;
  productId: string;
  status: string;
  originalTransactionId: string;
  transactionId: string;
  purchasedAt: Date;
  environment: string;
  createdAt: Date;
  updatedAt: Date;
};

export async function upsertEntitlement(input: {
  caregiverId: string;
  productId: string;
  originalTransactionId: string;
  transactionId: string;
  purchasedAt: Date;
  environment: string;
}): Promise<EntitlementRecord> {
  return prisma.caregiverEntitlement.upsert({
    where: { originalTransactionId: input.originalTransactionId },
    create: {
      caregiverId: input.caregiverId,
      productId: input.productId,
      status: "ACTIVE",
      originalTransactionId: input.originalTransactionId,
      transactionId: input.transactionId,
      purchasedAt: input.purchasedAt,
      environment: input.environment
    },
    update: {
      caregiverId: input.caregiverId,
      productId: input.productId,
      status: "ACTIVE",
      transactionId: input.transactionId
    }
  });
}

export async function findEntitlementsByCaregiverId(
  caregiverId: string
): Promise<EntitlementRecord[]> {
  return prisma.caregiverEntitlement.findMany({
    where: { caregiverId },
    orderBy: { purchasedAt: "desc" }
  });
}
