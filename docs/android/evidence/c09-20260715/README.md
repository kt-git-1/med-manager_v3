# C09 UI-002 Patient Link State Matrix — 2026-07-15

## Baseline and environment

- iOS/API baseline: `main@1d9d19e5376752d2e224560a9f2c7e950645e22b`
- Android baseline: `android-dev@384d7b3`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi, font scale 1.0
- Locale/appearance: Japanese, light
- Surface: production `PatientLinkContent`; synthetic code `123456`

## Contract review

Current iOS `LinkCodeEntryView` and Android preserve the same state hierarchy:

- non-digits are removed and input is limited to six digits;
- submit is enabled only for six digits;
- submitting retains the code, replaces the button label with progress and prevents duplicate submission;
- changing the code clears the previous inline error;
- validation, expired/used, unauthorized/forbidden, network and fallback errors use the pinned iOS Japanese copy;
- a rate-limit response intentionally follows current iOS fallback copy (`連携に失敗しました`);
- successful public exchange stores the patient session and leaves the link screen through authenticated routing.

The public exchange sends no patient/caregiver authorization header and a failed exchange does not delete either existing session. Those behaviors remain protected in `SessionRepositoryTest`.

## Deterministic visual evidence

| Required state | Evidence |
|---|---|
| Valid six digits | [`android-ui-002-patient-link-valid-light.png`](android-ui-002-patient-link-valid-light.png) |
| Submitting | [`android-ui-002-patient-link-loading-light.png`](android-ui-002-patient-link-loading-light.png) |
| Validation | [`android-ui-002-patient-link-validation-light.png`](android-ui-002-patient-link-validation-light.png) |
| Expired / not found / already used | [`android-ui-002-patient-link-not-found-light.png`](android-ui-002-patient-link-not-found-light.png) |
| Unauthorized / forbidden | [`android-ui-002-patient-link-authorization-light.png`](android-ui-002-patient-link-authorization-light.png) |
| Network | [`android-ui-002-patient-link-network-light.png`](android-ui-002-patient-link-network-light.png) |
| Rate limit / current-iOS fallback | [`android-ui-002-patient-link-rate-limit-light.png`](android-ui-002-patient-link-rate-limit-light.png) |

All seven images are generated in one API-35 instrumentation test from the same production component and are 1080 x 2400 pixels. The test also asserts submit enabled/disabled behavior and exact error visibility before each capture.

## Disposition

No production UI repair was required. Empty, 200%-font network error, valid, submitting and all pinned failure categories now have deterministic Android evidence. Successful session persistence/routing is behavioral rather than a stable terminal link-screen image and is covered by repository tests.

Matched non-empty iOS captures and a live one-time code exchange remain C01/release-environment verification; Android's local state matrix is complete.
