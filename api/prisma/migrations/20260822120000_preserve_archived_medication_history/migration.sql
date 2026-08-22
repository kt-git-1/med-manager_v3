-- Preserve schedules and dose history that occurred before a medication was archived.
ALTER TABLE "Medication" ADD COLUMN "archivedAt" TIMESTAMP(3);

-- Existing archived rows used updatedAt as the archive operation timestamp.
UPDATE "Medication"
SET "archivedAt" = "updatedAt"
WHERE "isArchived" = true
  AND "archivedAt" IS NULL;
