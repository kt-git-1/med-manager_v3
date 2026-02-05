-- CreateTable
CREATE TABLE "DoseRecordEvent" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "takenAt" TIMESTAMP(3) NOT NULL,
    "withinTime" BOOLEAN NOT NULL,
    "displayName" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "DoseRecordEvent_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "DoseRecordEvent_patientId_createdAt_idx" ON "DoseRecordEvent"("patientId", "createdAt");

-- AddForeignKey
ALTER TABLE "DoseRecordEvent" ADD CONSTRAINT "DoseRecordEvent_patientId_fkey" FOREIGN KEY ("patientId") REFERENCES "Patient"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- Enable RLS
ALTER TABLE "DoseRecordEvent" ENABLE ROW LEVEL SECURITY;

-- Policies
CREATE POLICY "caregiver_can_read_dose_record_events"
ON "DoseRecordEvent"
FOR SELECT
USING (
    EXISTS (
        SELECT 1
        FROM "CaregiverPatientLink" AS link
        WHERE link."patientId" = "DoseRecordEvent"."patientId"
          AND link."caregiverId" = auth.uid()::text
          AND link."status" = 'ACTIVE'
    )
);

CREATE POLICY "service_role_can_insert_dose_record_events"
ON "DoseRecordEvent"
FOR INSERT
WITH CHECK (auth.role() = 'service_role');
