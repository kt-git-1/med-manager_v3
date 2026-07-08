UPDATE "PatientSession"
SET "expiresAt" = GREATEST(
  COALESCE("expiresAt", NOW()),
  NOW() + INTERVAL '365 days'
)
WHERE "revokedAt" IS NULL;
