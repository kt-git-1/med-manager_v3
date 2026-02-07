-- AlterTable
ALTER TABLE "Medication" ADD COLUMN "isPrn" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "Medication" ADD COLUMN "prnInstructions" TEXT;

-- CreateTable
CREATE TABLE "prn_dose_records" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "takenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "quantityTaken" INTEGER NOT NULL,
    "actorType" "RecordedByType" NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "prn_dose_records_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "prn_dose_records_patientId_takenAt_idx" ON "prn_dose_records"("patientId", "takenAt" DESC);

-- CreateIndex
CREATE INDEX "prn_dose_records_patientId_medicationId_takenAt_idx" ON "prn_dose_records"("patientId", "medicationId", "takenAt" DESC);

-- AddForeignKey
ALTER TABLE "prn_dose_records" ADD CONSTRAINT "prn_dose_records_patientId_fkey" FOREIGN KEY ("patientId") REFERENCES "Patient"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prn_dose_records" ADD CONSTRAINT "prn_dose_records_medicationId_fkey" FOREIGN KEY ("medicationId") REFERENCES "Medication"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
