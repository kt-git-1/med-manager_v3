ALTER TABLE "DoseRecord"
  ADD COLUMN "consumedQuantity" DOUBLE PRECISION,
  ADD COLUMN "cancelledAt" TIMESTAMP(3),
  ADD COLUMN "cancelledByType" "RecordedByType",
  ADD COLUMN "cancelledById" TEXT,
  ADD COLUMN "inventoryRestoredAt" TIMESTAMP(3);

CREATE INDEX "DoseRecord_patientId_cancelledAt_scheduledAt_idx"
  ON "DoseRecord"("patientId", "cancelledAt", "scheduledAt");
