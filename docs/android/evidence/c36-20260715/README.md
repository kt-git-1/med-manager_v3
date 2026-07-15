# C36 — expanded Android API compatibility matrix

Date: 2026-07-15

Branch: `android-dev`

Baseline: C35 at `4cacd54`, with `origin/main@1cf8aef` already merged by C31

## Outcome

The current 187-test Android instrumentation suite passes on every declared compatibility AVD:

| AVD | API | Result |
| --- | ---: | ---: |
| `MedicationApp_API_26` | 26 | 187/187 passed, 0 failed/skipped |
| `MedicationApp_API_33` | 33 | 187/187 passed, 0 failed/skipped |
| `MedicationApp_API_35` | 35 | 187/187 passed, 0 failed/skipped (C35) |
| Total | — | 561/561 passed |

Each run used `./gradlew connectedDebugAndroidTest` against a cold-booted ARM AVD. API 26 completed in 2m10s and API 33 in 5m16s. The normal API-35 AVD was restored after the matrix.

## API 26 test-harness corrections

The first expanded API-26 pass exposed compatibility assumptions in instrumentation code, not production behavior:

- Compose dialog-node `captureToImage` is unsupported below API 28, so PRN dialog fixtures use the device screenshot helper only on API 26.
- Compact and enlarged-text layouts legitimately keep deep lazy-list items uncomposed until scrolled into view.
- Inventory correction actions live in the tagged detail `LazyColumn`, not its non-scrollable outer container.
- Async mutation assertions now wait for repository state and then scroll to retry/action content.

The corrected tests exercise the actual production scroll containers and assert that the target action is displayed before interaction. This strengthens compact-screen and large-text reachability coverage without changing production UI or API behavior.

## Remaining external gates

This closes the current API 26/33 rerun residual. It does not replace physical notification/Doze/process-death, TalkBack/OEM lifecycle, live Firebase Console, signed Play release or fresh matched iOS runtime visual evidence.
