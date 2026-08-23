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

### Deployed Staging boundary E2E

The deployed Staging API and `stagingDebug` Android package were also exercised with the retained synthetic QA caregiver/patient on 2026-08-23 JST. The APK SHA-256 was `5a6706169ce6a66d632cc5c5b5a39f44deb20bbaff788b7e38324b5fb51201d1` from Android commit `d4d09aefe785620999ac74d8870d1a6b1955de38`.

1. Set two still-future slots for the current day and register one synthetic scheduled medication for both slots.
2. Use a Patient session to record only the later slot early. The actual time was 10:13 JST and its scheduled time was 10:36 JST.
3. Delete/archive the medication before 10:36 JST.
4. Query both deployed Patient and Caregiver day-history endpoints, then inspect both corresponding Android History surfaces.

Both roles retained exactly one `taken` item with the medication snapshot, scheduled time, actual time and Patient recorder attribution. The unrecorded sibling slot was absent, and the archived medication was absent from the active medication list. The Caregiver Android detail showed `1/1種類 記録済み`, `予定 10:36`, `実際 10:13` and `本人が記録`; the Patient Android detail showed the same medication as `記録済み` with `予定 10:36` and `服用 10:13`.

One initial Caregiver patient-list load required the existing retry action, and one later read-only HTTPS request was reset once; bounded retries succeeded and the final endpoint/UI results were consistent. No real user or patient was opened, and no credential, token, link code or database identifier was retained.

This is a post-Phase 1 compatibility follow-up. It does not reopen the frozen UI/Analytics RC and does not authorize Play Console, signing, Production deploy or real-user operations.
