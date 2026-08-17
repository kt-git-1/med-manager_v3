# C109 physical UI regression evidence

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

**Android input:** `android-dev@84cc03c` plus the C109 evidence-preservation runner change

**Target:** SHARP A302SH, Android 15 / API 35, awake and unlocked

## Result

The complete connected Compose UI suite passed on the retained non-Google OEM target as four bounded shards:

| Shard | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| 1/4 | 66 | 0 | 0 | 0 |
| 2/4 | 59 | 0 | 0 | 0 |
| 3/4 | 79 | 0 | 0 | 0 |
| 4/4 | 76 | 0 | 0 | 0 |
| **Total** | **280** | **0** | **0** | **0** |

The runner preserved one instrumentation XML per shard under `android/app/build/reports/connected-ui-shards/`, emitted the aggregate `results.tsv`, and removed both the application and test packages on exit. Package-manager absence was confirmed after the final run.

The retained local result hashes were:

| File | SHA-256 |
|---|---|
| `results.tsv` | `42a63912dfb0db2398d23d8e8038afbb84603d071ce56f062dceca96f237ed3e` |
| `shard-1-of-4.xml` | `73e1aae7ab99b6c1f3121d941fbbe3dc92b83813ba354a254d64c389ac1f43c5` |
| `shard-2-of-4.xml` | `3e8369702364e0ce4664cfcaa651eb5d263928573a53aa79f120b810cec73490` |
| `shard-3-of-4.xml` | `0dfa65381cd5e4b725ad74b7d9bd237761013d093e3b52c3ae226d8ffbe5878f` |
| `shard-4-of-4.xml` | `23976067d954745dccfb3776686f2af0b24ac643085722f3adb3e5da9f5a2b88` |

The committed summary is [results.tsv](./results.tsv). Raw AndroidJUnitRunner XML remains local build evidence because it includes transient host metadata and generated test output.

## Evidence-integrity correction

An initial complete run passed 280/280, but Gradle retained only the most recently executed shard report. C109 therefore makes the runner fail closed unless every successful shard produces exactly one parseable XML summary with at least one test and zero failures, errors and skips. It copies each report before the next shard can overwrite it and emits a deterministic aggregate table. The synthetic contract covers awake success, Doze/keyguard/unreadable rejection, Gradle-failure cleanup, missing XML, skipped XML and multi-shard preservation.

## Native platform incident

The first evidence-enabled attempt stopped during shard 1 when the app process received `SIGSEGV` on RenderThread in Android platform `/system/lib64/libhwui.so`, at `android::uirenderer::BaseRenderNodeAnimator::pushStaging`. AndroidJUnitRunner reported the instrumentation process crash at `CaregiverMedicationScreenTest#regularScheduleSupportsWeeklyDaysAndPrnHidesSchedule`; there was no Kotlin or Java application exception stack.

The exact test then passed 10/10 from clean installations. A subsequent complete evidence-enabled run passed all four shards, 280/280. This supports treating the event as a currently non-reproducible OEM/platform HWUI incident, but does not prove that the crash cannot recur. Closed-test Play crash/ANR monitoring remains mandatory.

## Boundaries

This closes the pending fresh unlocked A302SH automated rerun and the runner's durable per-shard evidence gap. It does **not** close assisted spoken TalkBack/real-finger operation, FCM delivery, old API 26-28 physical hardware, a current Google/reference target, production mutation interruption, release-owner signing, Play-generated/install surfaces, or closed-test crash/ANR review. `XP-008` and all applicable `RG-*` gates remain open.
