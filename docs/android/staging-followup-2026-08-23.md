# Android Staging API follow-up — 2026-08-23

## Source delta

- Previous Android API authority: `staging@e9ec0d3c6b10`
- New Android API authority: `staging@94038748ce0a`
- Staging change: `fix(api): preserve early dose history after medication archive`
- Android merge checkpoint: `8a71df3`
- Android compatibility-test commit: `6742990`
- Production remains unchanged at `main@432b34c064d7`.

The Staging delta changes only `scheduleService.ts` and its API unit test. It keeps a recorded scheduled dose when its scheduled time is later than the same-day medication archive timestamp, while continuing to exclude unrecorded post-archive schedules. There is no DTO, route, authentication, database-schema, notification, copy or UI-layout change.

## Android impact and coverage

Both Patient and Caregiver day-history endpoints decode the same history wire contract. Android now has an explicit fixture proving that a same-day archived medication dose returned by Staging remains visible through both role-specific endpoints with its medication snapshot, schedule time, actual time, `TAKEN` state and patient recorder attribution. The API generator test separately proves that the unrecorded sibling schedule is not restored.

No Android production source change was required.

## Verification

- API schedule generator: 17/17 passed.
- API complete suite: 352/352 across 76 files passed.
- API typecheck, ESLint and Next production build passed.
- Android Staging/Production Debug/Release JVM suites passed.
- Android Staging Debug and Production Release lint/build passed.
- Android release-gate ledger passed: 10 gates, 2 ready, 7 blocked, 1 verified, 5 partial requirements.
- API 35 Patient and Caregiver History Compose suites: 43/43 passed with zero failures or skips.

This is a post-Phase 1 compatibility follow-up. It does not reopen the frozen UI/Analytics RC and does not authorize Play Console, signing, Production deploy or real-user operations.

