CREATE TABLE "PatientSlotTimeRevision" (
  "id" TEXT NOT NULL,
  "patientId" TEXT NOT NULL,
  "effectiveFrom" TIMESTAMP(3) NOT NULL,
  "morningTime" TEXT NOT NULL,
  "noonTime" TEXT NOT NULL,
  "eveningTime" TEXT NOT NULL,
  "bedtimeTime" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT "PatientSlotTimeRevision_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "PatientSlotTimeRevision_patientId_effectiveFrom_idx"
  ON "PatientSlotTimeRevision"("patientId", "effectiveFrom");

ALTER TABLE "PatientSlotTimeRevision"
  ADD CONSTRAINT "PatientSlotTimeRevision_patientId_fkey"
  FOREIGN KEY ("patientId") REFERENCES "Patient"("id")
  ON DELETE RESTRICT ON UPDATE CASCADE;
