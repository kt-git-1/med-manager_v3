# C81 automated TalkBack boundary and physical regression

**Observed:** 2026-08-17 03:18 JST
**Branch baseline:** `android-dev@5f31bde`
**Device class:** SHARP A302SH, Android 15/API 35, Debug test artifact
**Data boundary:** local preview/fake repositories only; no production API, account, patient, medication or dose operation

## Implemented contracts

- Patient bottom navigation has stable diagnostic tags and both role tab bars prove the exact visible order, Japanese merged label, `Role.Tab` and selected/unselected state.
- Patient and Caregiver tutorials prove their pane title at the first step.
- While either tutorial is active, the production bottom navigation tags are absent from the merged accessibility tree; the live shell cannot steal TalkBack focus behind the sample/guide surface.
- The inventory correction failure/retry test now waits for its confirmation dialog semantics before injecting the confirm tap, matching the existing successful-correction test's synchronization.

No visual geometry, business rule, API call or production accessibility copy changed.

## Verification

| Gate | Result |
|---|---|
| Targeted Patient/Caregiver accessibility and tutorial set | PASS — 23/23 |
| Debug JVM | PASS — 216/216, 0 failed/skipped |
| Release JVM | PASS — 213/213, 0 failed/skipped |
| Lint / Release assembly | PASS |
| Physical UI shard 0 | PASS — 66/66 |
| Physical UI shard 1 | PASS — 59/59 |
| Physical UI shard 2 | PASS — 79/79 |
| Physical UI shard 3 after synchronization repair | PASS — 76/76 |
| Physical UI total | PASS — 280/280 |
| Device end state | PASS — app and test packages uninstalled |

## Runner diagnosis

The first monolithic 280-test attempt ended after 129 tests because the OEM app process aborted in native Scudo allocation after roughly three minutes. It did not report a Kotlin/Compose assertion for the named IME test, and that test passed 1/1 immediately when isolated. Four bounded AndroidJUnitRunner shards avoid the long single-process window and are the reproducible command for this device.

One shard then exposed a deterministic test synchronization gap: the inventory failure test attempted to tap its confirmation before the dialog semantics existed. Adding the same bounded readiness wait already used by the successful correction path made the test pass 1/1 and the complete final shard pass 76/76.

## Remaining physical acceptance

C81 does not claim spoken TalkBack completion. A person must still use one-finger next/previous navigation, hear and judge the full Japanese announcement order, activate controls by double tap, scroll lazy content with two fingers and verify focus return/dismissal across every production surface. Those rows remain V01 and `XP-005 PARTIAL`.
