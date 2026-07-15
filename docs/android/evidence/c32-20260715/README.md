# C32 Patient Recording Streak

## Implemented contract

- Patient-authenticated `GET /api/patient/history/streak`.
- Strict response mapping for nonnegative `currentStreakDays`, `isAtLeast` and exactly `complete`, `inProgress`, `missed`, `noSchedule`.
- Supplementary repository state: a streak failure clears only the streak and never replaces usable History or publishes a generic History error.
- Current iOS card order: today progress, `連続記録`, current week, recent records.
- Exact current iOS start/day/day-at-least, milestone/continuation and today-status next-step copy.
- 56-unit teal calendar circle, 18-unit card, achievement row and teal 12%-surface next-step treatment.

## Automated results

- `testDebugUnitTest`: passed, including endpoint/auth, strict status, success state and supplementary-failure isolation.
- `compileDebugAndroidTestKotlin`: passed.
- API 35 `PatientHistoryContentTest`: 14/14 passed, including the seven-day in-progress card and absent-streak ordinary-history fallback.

## Remaining release evidence

- Matched current-iOS dark and maximum-text captures.
- Physical-device TalkBack and font-scale verification.
