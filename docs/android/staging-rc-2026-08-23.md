# Android Staging RC rebaseline — 2026-08-23

## Scope

- Android branch before rebaseline: `android-dev@2d4cb4fe67a7`
- iOS/API authority: `origin/staging@e9ec0d3c6b10`
- Published Production reference: `origin/main@432b34c064d7`
- Rebaseline merge checkpoint: `b75dedf`
- Phase 1 implementation RC: `9ae1fc0`
- Public version remains `1.0.0` / `versionCode=1`
- No Play Console, Production deploy, signing-key or real-user operation is authorized by this record.

The complete `main@432b34c..staging@e9ec0d3` history was merged into `android-dev`; no iOS file was edited to manufacture Android parity. Android-specific API hardening remains additive to the Staging source.

## Phase 0 delta audit

| Staging change | Authority | Android result |
|---|---|---|
| Privacy-safe Analytics additions | `bdce258`, `AnalyticsService.swift` | Event names, exact parameter keys and fixed enum values match `AnalyticsEventSchema`; Patient/Caregiver call sites and rejection tests are present. |
| Android FCM platform registration | `696237f`, push validator and route tests | Exact `platform=android`, `environment=DEV/PROD` contract retained. A stale pre-rebaseline test that still sent `web` was corrected; unsupported `web` remains a 422 case. |
| Atomic partial slot progress | `d97b537`, schedule response and bulk service tests | Android DTOs expose per-slot medication totals/recorded/remaining/insufficient counts and Patient/Caregiver UI distinguishes partial from unrecorded. Staging API atomicity is now in the branch baseline. |
| Medication dosing-period exclusion | `dbd6b4b`, schedule tests | Ended/not-started medication is excluded from Today and future schedules; historical records remain a separate history contract. |
| Medication archive history retention | `470e2a2`, migration/routes/services/tests | `archivedAt` bounds historical scheduled reconstruction, archived medication cannot create new scheduled/PRN records, and scheduled plus PRN history remains readable. Android removal copy states that history is retained. |
| Guided caregiver setup | `7d20db5`, `CaregiverHomeView.swift` | Existing Android ten-step flow already performs patient creation, linking-code issue/share and medication registration at steps 7–9 with explicit mutations and retry boundaries. |
| Grouped caregiver PRN history | `e0f4998`, `HistoryDayDetailView.swift` | Existing Android day detail has one expandable PRN group with individual record time, quantity and recorder attribution, matching the current iOS hierarchy. |
| Build-only iOS changes | `15b7051`, `e9ec0d3` | No Android runtime contract change; Android remains initial `1.0.0` / `versionCode=1`. |

No unexplained screen, copy, DTO, notification, history, tutorial or Analytics-schema delta remains for the Android initial-release scope.

## Analytics schema parity

The three latest parity events are exact:

| Event | Exact Android/iOS parameters |
|---|---|
| `core_action_failed` | `action_name` plus `reason`, both fixed enums |
| `patient_link_code_share_tapped` | `surface=patient_management` |
| `notification_permission_result` | fixed `result` plus fixed `surface` |

Both clients reject arbitrary identifiers, names, medication/dose data, dates, notification contents, linking codes, tokens and free text. Collection remains off until explicit consent; disabling collection resets Analytics data.

## Automated RC gates

Results recorded after the rebaseline correction:

- API: typecheck, 351/351 tests across 76 files, ESLint and Next production build all passed.
- Android: Staging/Production Debug/Release JVM tests, Staging Debug and Production Release lint/build all passed.
- Compose UI: the complete Staging Debug API 35 suite passed in four shards, 299/299 with zero failures/errors/skips.
- Release contracts: Staging runtime isolation, credential safety, SDK/manifest/AAB/16 KB policy, release-gate ledger, Play listing/assets, universal install surface and API 26/API 33/A302SH-shaped split surfaces all passed.
- Production Release APK SHA-256: `d4ab8f1db71d90c854399161e5a63abcb007da236bc8ce6087473699c89a1a17`.
- Production Release AAB SHA-256: `9de97e94cca51061eb24c8f9f4499f2d428acb7ff2bab1baa66997107ce741af`.

## Phase 1 live acceptance boundary

The prior C76/C80 and 2026-08-22 Staging records prove explicit consent OFF/ON/OFF, Android Firebase upload, DebugView, Realtime, processed Events and archive/history cross-role E2E. This RC additionally passed a fresh refusal control, then emitted and expanded the exact three new fixed-enum events in DebugView. Firebase accepted the batch with HTTP 204. A temporary Explore now proves `イベント名` × `イベント数` under `プラットフォーム` exactly matching `Android`; its processed 28-day window contained 38 Android events. See `evidence/h07-20260823/README.md`.

No P0/P1 Android runtime defect remains from this rebaseline. The release owner approved removal of the temporary Explore; deletion and absence from the exploration list were confirmed. `RG-001` and `XP-004` are verified, so Phase 1 is complete. Play ownership/signing and every Production operation remain later phases.
