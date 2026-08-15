# C61 — Published iOS 1.0.6 Android rebaseline

## Baseline

- Published product: iOS 1.0.6 Build 51
- Source: `main@432b34c`
- Android merge checkpoint: `36a6d4d`
- Development branch: `android-dev`

## Completed checkpoints

- `36a6d4d`: merged the complete published main source without editing the parallel iOS worktree.
- `89ecbfa`: added `takenAt`, the Tokyo recording policy and next-slot late-dose selection contract.
- `3e724c3`, `e849a42`, `972b511`: matched Patient Today, Patient History and Caregiver Today actual-time/late behavior.
- `2b36337`: enforced strict patient slot-time ordering.
- `d9696f7`: added the published medication supply calculator.
- `f5811fa`: matched the published action-first inventory editor.
- `6a1d1f2`: replaced Patient History summary-only cards with the published expandable inline detail.
- `9f6b1c6`: matched the published compact Patient Today and exact Caregiver Today summaries plus grouped Caregiver History.
- `74c3d1f`: matched the published medication registration flow.
- C62 working checkpoint: completed the direct source audit with published medication defaults and inventory detail styling/reachability.
- C63 working checkpoint: corrected the tutorial contract after direct comparison with the published private Swift sample views. Android now has dedicated Patient/Caregiver fixture screens for all 14 steps, exact fixed copy/data, compact role-correct overlays and dark 200% action reachability. The light screenshot fixtures were visually inspected on API 35.
- C64 working checkpoint: audited all 69 parity rows, retained six external-only `PARTIAL` rows, and corrected medication-form validation/calculator text to cross the data/UI boundary as typed values rather than localized strings. The affected 25-test UI class passes on API 26/33/35 and the complete 272-test suite passes again on API 35.

## Implemented contract

- optional actual dose time (`takenAt`) and the exact 60-minute late threshold;
- patient recording window from 30 minutes before schedule until the following Tokyo day at 04:00, exclusive;
- late recordable doses retained in today's status while the later upcoming slot owns the next-action card;
- actual time, delay and late-state presentation in Patient Today, Patient History and Caregiver Today;
- strict morning < noon < evening < bedtime patient slot-time validation;
- scheduled-medication supply calculator with editable calculated initial inventory;
- action-first inventory editor with a suggested 14-day refill, 7/14/21-day presets, before/after values and isolated correction flow;
- caregiver push selection of the payload patient before exact History date/slot navigation.

## Automated evidence

| Gate | Result |
|---|---|
| API contract and typecheck | 322 tests passed at the C61 baseline |
| Android JVM unit tests | 202/202 passed |
| Android Lint | passed |
| API 35 instrumentation | 272/272 passed (cold rerun after one isolated startup flake) |
| API 33 instrumentation | 272/272 passed |
| API 26 instrumentation | 272/272 passed |
| C64 medication-form slice, API 26/33/35 | 25/25 on each API |
| C64 API 35 full rerun | 272/272 passed |
| Debug and Release assembly | passed |
| Release APK compatibility | passed: app ID/SDK, 16 KB ZIP/ELF alignment, advertising/attribution permission exclusion |
| Play listing asset validation | passed |

The post-C61 run also exposed and removed API 26 timing assumptions around lazy medication schedules, keyboard-covered inventory confirmation and PDF validation. The tests now wait for authoritative Compose state and explicitly dismiss the IME where appropriate; production inventory detail also applies IME padding so actions remain reachable.

All fixtures use synthetic patients, medication names and timestamps. The tutorial screenshots are test-runner artifacts and are not checked into Git. No identity, token or production medical data is stored in this evidence.

## Remaining release gates

C61 closes emulator-verifiable parity only. H07 live privacy-reviewed Firebase evidence, V01 physical TalkBack/OEM/notification/process-state testing, release-owner signing and Play Console validation remain mandatory before store release.
