-- CreateTable
CREATE TABLE "LinkingAttempt" (
    "id" TEXT NOT NULL,
    "patientId" TEXT NOT NULL,
    "attemptCount" INTEGER NOT NULL DEFAULT 0,
    "lockedUntil" TIMESTAMP(3),
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LinkingAttempt_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "LinkingAttempt_patientId_key" ON "LinkingAttempt"("patientId");

-- CreateIndex
CREATE INDEX "LinkingAttempt_lockedUntil_idx" ON "LinkingAttempt"("lockedUntil");

-- AddForeignKey
ALTER TABLE "LinkingAttempt" ADD CONSTRAINT "LinkingAttempt_patientId_fkey" FOREIGN KEY ("patientId") REFERENCES "Patient"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
