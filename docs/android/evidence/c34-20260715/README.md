# C34 Missed-dose Caregiver Push Routing

## Contract

Android now uses one pure payload parser before both notification display and caregiver History navigation.

Accepted event types are exactly:

- `DOSE_TAKEN`
- `DOSE_MISSED`

Both require a nonblank `patientId`, a real ISO `yyyy-MM-dd` date and one canonical lowercase slot: `morning`, `noon`, `evening`, `bedtime`. Unknown or case-shifted types, missing/blank patients, impossible dates and other slot names are rejected. The target model contains no medication name, dosage, free text or token.

The notification intent preserves the accepted event type and the same patient/date/slot. Both event types select the payload patient, open caregiver History, load the exact day and highlight the exact slot.

## Automated results

- Focused parser/repository JVM tests: passed for both allowed types and all malformed/unknown cases.
- API-35 `CaregiverHomeScreenTest` + `CaregiverHistoryScreenTest`: 27/27 passed, including `DOSE_MISSED` patient selection, History navigation, exact date and exact slot highlight.
- The C31 backend gate already passed missed-dose notification and push-trigger integration tests (4 files / 25 tests).

## Remaining release evidence

- Physical Firebase delivery, background/terminated tap and OEM process-death behavior.
- Live server cron observation with privacy review of the received data payload.
