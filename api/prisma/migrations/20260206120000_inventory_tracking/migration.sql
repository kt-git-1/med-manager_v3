-- CreateEnum
CREATE TYPE "InventoryAlertState" AS ENUM ('NONE', 'LOW', 'OUT');

-- CreateEnum
CREATE TYPE "InventoryAlertType" AS ENUM ('LOW', 'OUT');

-- CreateEnum
CREATE TYPE "InventoryAdjustmentReason" AS ENUM ('REFILL', 'SET', 'CORRECTION', 'TAKEN_CREATE', 'TAKEN_DELETE');

-- CreateEnum
CREATE TYPE "InventoryAdjustmentActorType" AS ENUM ('CAREGIVER', 'SYSTEM');

-- AlterTable
ALTER TABLE "Medication" ADD COLUMN "inventoryEnabled" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "Medication" ADD COLUMN "inventoryQuantity" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "Medication" ADD COLUMN "inventoryLowThreshold" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "Medication" ADD COLUMN "inventoryUpdatedAt" TIMESTAMP(3);
ALTER TABLE "Medication" ADD COLUMN "inventoryLastAlertState" "InventoryAlertState";

-- CreateTable
CREATE TABLE "MedicationInventoryAdjustment" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "delta" INTEGER NOT NULL,
    "reason" "InventoryAdjustmentReason" NOT NULL,
    "actorType" "InventoryAdjustmentActorType" NOT NULL,
    "actorId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MedicationInventoryAdjustment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "InventoryAlertEvent" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "type" "InventoryAlertType" NOT NULL,
    "remaining" INTEGER NOT NULL,
    "threshold" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "InventoryAlertEvent_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "MedicationInventoryAdjustment_patientId_createdAt_idx" ON "MedicationInventoryAdjustment"("patientId", "createdAt");

-- CreateIndex
CREATE INDEX "MedicationInventoryAdjustment_medicationId_createdAt_idx" ON "MedicationInventoryAdjustment"("medicationId", "createdAt");

-- CreateIndex
CREATE INDEX "InventoryAlertEvent_patientId_createdAt_idx" ON "InventoryAlertEvent"("patientId", "createdAt");

-- CreateIndex
CREATE INDEX "InventoryAlertEvent_medicationId_createdAt_idx" ON "InventoryAlertEvent"("medicationId", "createdAt");

-- AddForeignKey
ALTER TABLE "MedicationInventoryAdjustment" ADD CONSTRAINT "MedicationInventoryAdjustment_patientId_fkey" FOREIGN KEY ("patientId") REFERENCES "Patient"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "MedicationInventoryAdjustment" ADD CONSTRAINT "MedicationInventoryAdjustment_medicationId_fkey" FOREIGN KEY ("medicationId") REFERENCES "Medication"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "InventoryAlertEvent" ADD CONSTRAINT "InventoryAlertEvent_patientId_fkey" FOREIGN KEY ("patientId") REFERENCES "Patient"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "InventoryAlertEvent" ADD CONSTRAINT "InventoryAlertEvent_medicationId_fkey" FOREIGN KEY ("medicationId") REFERENCES "Medication"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- Enable RLS
ALTER TABLE "InventoryAlertEvent" ENABLE ROW LEVEL SECURITY;

-- Policies
CREATE POLICY "caregiver_can_read_inventory_alert_events"
ON "InventoryAlertEvent"
FOR SELECT
USING (
    EXISTS (
        SELECT 1
        FROM "CaregiverPatientLink" AS link
        WHERE link."patientId" = "InventoryAlertEvent"."patientId"
          AND link."caregiverId" = auth.uid()::text
          AND link."status" = 'ACTIVE'
    )
);

CREATE POLICY "service_role_can_insert_inventory_alert_events"
ON "InventoryAlertEvent"
FOR INSERT
WITH CHECK (auth.role() = 'service_role');
