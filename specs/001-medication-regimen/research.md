# Research Summary: Medication Regimen (001)

## Decision 1: Prisma v7.3 configuration

- Decision: Use `prisma.config.ts` at repository root with `env("DATABASE_URL")`.
- Rationale: Prisma 7 moves datasource URL out of `schema.prisma` and into config.
- Alternatives considered: Keep `url` in `schema.prisma` (deprecated for v7+).

## Decision 2: daysOfWeek / times storage

- Decision: `daysOfWeek` as enum array, `times` as `HH:mm` string array.
- Rationale: Clear validation, direct mapping to iOS UI, simple schedule generation.
- Alternatives considered: Bitmask or join table for days, time column arrays for times.

## Decision 3: patientSessionToken handling (001)

- Decision: Verify `patientSessionToken` via a stubbed verifier behind an interface.
- Rationale: Enable 001 to ship while keeping a clean swap-in point for real verification.
- Alternatives considered: Delay patient mode or implement full link-code exchange now.

## Decision 4: schedule generation

- Decision: Generate scheduled doses on demand from Medication + Regimen without DB storage.
- Rationale: Avoid redundant persistence and keep future adherence merge straightforward.
- Alternatives considered: Persist precomputed schedules (risk: stale data, heavy writes).

## Decision 5: serverless + Prisma on Vercel

- Decision: Use pooled connection string and minimize connections per request.
- Rationale: Avoid connection exhaustion and reduce cold-start overhead.
- Alternatives considered: Long-lived connections (not suitable for serverless).
