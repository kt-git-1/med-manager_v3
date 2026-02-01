-- CreateEnum
CREATE TYPE "DayOfWeek" AS ENUM ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN');

-- CreateTable
CREATE TABLE "Medication" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "dosageText" TEXT NOT NULL,
    "doseCountPerIntake" INTEGER NOT NULL,
    "dosageStrengthValue" DOUBLE PRECISION NOT NULL,
    "dosageStrengthUnit" TEXT NOT NULL,
    "notes" TEXT,
    "startDate" DATE NOT NULL,
    "endDate" DATE,
    "inventoryCount" INTEGER,
    "inventoryUnit" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "isArchived" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Medication_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Regimen" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "timezone" TEXT NOT NULL,
    "startDate" DATE NOT NULL,
    "endDate" DATE,
    "times" TEXT[],
    "daysOfWeek" "DayOfWeek"[],
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Regimen_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Medication_patientId_isActive_idx" ON "Medication"("patientId", "isActive");

-- CreateIndex
CREATE INDEX "Medication_patientId_startDate_endDate_idx" ON "Medication"("patientId", "startDate", "endDate");

-- CreateIndex
CREATE INDEX "Regimen_patientId_medicationId_enabled_idx" ON "Regimen"("patientId", "medicationId", "enabled");

-- CreateIndex
CREATE INDEX "Regimen_patientId_startDate_endDate_idx" ON "Regimen"("patientId", "startDate", "endDate");

-- AddForeignKey
ALTER TABLE "Regimen" ADD CONSTRAINT "Regimen_medicationId_fkey" FOREIGN KEY ("medicationId") REFERENCES "Medication"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
