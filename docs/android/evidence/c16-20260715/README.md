# C16 UI-206 Caregiver History Day Contract — 2026-07-15

Baseline: `android-dev@8cbb532`, compared with the current iOS `HistoryMonthView` / `HistoryDayDetailView` source contract. All images are deterministic production-Compose captures from the API 35 emulator at 1080 × 2400; they contain no real account, patient, medication or token data.

| State | Android evidence |
|---|---|
| Timestamp-sorted scheduled/PRN timeline | [`android-ui-206-caregiver-history-day-timeline-light.png`](android-ui-206-caregiver-history-day-timeline-light.png) |
| Message-bearing loading | [`android-ui-206-caregiver-history-day-loading-light.png`](android-ui-206-caregiver-history-day-loading-light.png) |
| Empty day | [`android-ui-206-caregiver-history-day-empty-light.png`](android-ui-206-caregiver-history-day-empty-light.png) |
| Retryable failure | [`android-ui-206-caregiver-history-day-error-light.png`](android-ui-206-caregiver-history-day-error-light.png) |

## Source comparison and repairs

- The caregiver detail now reuses the current-iOS timestamp/name ordering contract instead of rendering scheduled and PRN records in separate Android-only sections.
- The detail heading uses `M月d日 (E)`. Loading includes exact `読み込み中...`, and the empty state uses the exact `予定がありません` / `この日の服用予定はありません` pair.
- Scheduled rows retain caregiver attribution, semantic status/slot treatment and a full-width teal confirmation entry for missed-dose backfill. PRN rows retain their actor label and badge in the same timeline.
- Generic day failure remains inline with privacy-safe guidance and retries the exact selected patient/date request. Existing content remains visible for refresh failure.
- Selecting the already-selected date is now idempotent. It cannot clear a loaded detail or failure without changing the effect key and leave the production screen blank.
- Exact notification slot highlighting and confirmation-protected caregiver backfill remain unchanged and covered.

`CaregiverHistoryScreenTest` passes 10/10 on API 35 and emits all four fixtures from the asserted production component. Matched iOS captures, dark/200%-font variants and physical notification/TalkBack verification remain C01/Gate I work.
