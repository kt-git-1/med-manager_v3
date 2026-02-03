-- CreateEnum
CREATE TYPE "RecordedByType" AS ENUM ('PATIENT', 'CAREGIVER');

-- CreateTable
CREATE TABLE "DoseRecord" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "takenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "recordedByType" "RecordedByType" NOT NULL,
    "recordedById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DoseRecord_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "DoseRecord_patientId_scheduledAt_idx" ON "DoseRecord"("patientId", "scheduledAt");

-- CreateIndex
CREATE UNIQUE INDEX "DoseRecord_patientId_medicationId_scheduledAt_key" ON "DoseRecord"("patientId", "medicationId", "scheduledAt");

-- AddForeignKey
ALTER TABLE "DoseRecord" ADD CONSTRAINT "DoseRecord_patientId_fkey" FOREIGN KEY ("patientId") REFERENCES "Patient"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DoseRecord" ADD CONSTRAINT "DoseRecord_medicationId_fkey" FOREIGN KEY ("medicationId") REFERENCES "Medication"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
