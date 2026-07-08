CREATE TABLE "ApiRateLimit" (
    "key" TEXT NOT NULL,
    "count" INTEGER NOT NULL DEFAULT 0,
    "resetAt" TIMESTAMP(3) NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ApiRateLimit_pkey" PRIMARY KEY ("key")
);

CREATE INDEX "ApiRateLimit_resetAt_idx" ON "ApiRateLimit"("resetAt");

ALTER TABLE "ApiRateLimit" ENABLE ROW LEVEL SECURITY;

UPDATE "PatientSession"
SET "expiresAt" = NOW() + INTERVAL '90 days'
WHERE "expiresAt" IS NULL
  AND "revokedAt" IS NULL;
