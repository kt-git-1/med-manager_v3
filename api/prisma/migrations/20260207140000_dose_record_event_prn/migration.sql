-- AlterTable
ALTER TABLE "DoseRecordEvent" ADD COLUMN "medicationName" TEXT;
ALTER TABLE "DoseRecordEvent" ADD COLUMN "isPrn" BOOLEAN NOT NULL DEFAULT false;
