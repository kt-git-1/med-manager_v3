-- CreateEnum
CREATE TYPE "EntitlementStatus" AS ENUM ('ACTIVE', 'REVOKED');

-- CreateTable
CREATE TABLE "CaregiverEntitlement" (
    "id" TEXT NOT NULL,
    "caregiverId" TEXT NOT NULL,
    "productId" TEXT NOT NULL,
    "status" "EntitlementStatus" NOT NULL DEFAULT 'ACTIVE',
    "originalTransactionId" TEXT NOT NULL,
    "transactionId" TEXT NOT NULL,
    "purchasedAt" TIMESTAMP(3) NOT NULL,
    "environment" TEXT NOT NULL DEFAULT 'Production',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CaregiverEntitlement_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "CaregiverEntitlement_originalTransactionId_key" ON "CaregiverEntitlement"("originalTransactionId");

-- CreateIndex
CREATE INDEX "CaregiverEntitlement_caregiverId_idx" ON "CaregiverEntitlement"("caregiverId");
