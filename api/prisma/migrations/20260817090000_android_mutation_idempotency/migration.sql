-- Optional client mutation identifiers keep legacy iOS requests backward compatible while
-- allowing Android retries of the same logical operation to return without duplicate effects.
ALTER TABLE "prn_dose_records"
ADD COLUMN "clientMutationId" TEXT;

ALTER TABLE "MedicationInventoryAdjustment"
ADD COLUMN "clientMutationId" TEXT;

CREATE UNIQUE INDEX "prn_dose_records_patientId_clientMutationId_key"
ON "prn_dose_records"("patientId", "clientMutationId");

CREATE UNIQUE INDEX "MedicationInventoryAdjustment_patientId_clientMutationId_key"
ON "MedicationInventoryAdjustment"("patientId", "clientMutationId");
