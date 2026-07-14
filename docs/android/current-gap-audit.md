# Android Current Gap Audit

**Audit date:** 2026-07-14
**Reference:** `main@1d9d19e`
**Android branch:** `android-dev`

## 1. Executive result

The existing Android project is a useful patient-mode implementation, but it is not yet a high-fidelity port of the current product. Patient functionality has substantial automated coverage; caregiver product UI is still a placeholder. The new main baseline changes several session and mutation contracts, so affected rows are reset to `RECHECK_REQUIRED` before Phase C1 begins.

## 2. What is reusable

- Kotlin/Compose Gradle project, application identity and build workflow
- Semantic light/dark theme foundation and copied role assets
- Supabase caregiver auth and Android App Link callback foundation
- Keystore AES-GCM secret storage with Android backup disabled
- Centralized patient refresh/retry/error mapping foundation
- Patient Today, history, settings, notification and tutorial implementation
- Deterministic reminder plan builder and notification target parsing
- JVM and Compose/instrumentation test suites

Reusable means “candidate for re-verification,” not “accepted unchanged.”

## 3. New-baseline regressions and architectural gaps

| Priority | Gap | Evidence | Required correction |
|---|---|---|---|
| Resolved A01 | Public link exchange previously had no explicit no-auth request policy | Current `RequestAuthPolicy.PUBLIC` never reads token providers or mutates sessions | Unit/repository tests cover public 401/403/404/422/429 and stored patient/caregiver tokens |
| Resolved A02 | Patient-link copy/error states and captures predated the pinned baseline | Typed failure mapping and canonical resources now match current `LinkCodeEntryView.swift`/`Localizable.strings` | JVM/Compose/instrumentation tests plus current iOS/Android light, dark and large-text captures |
| Resolved A03 | Reinstall session safety was assumed from `allowBackup=false` | Explicit legacy/current cloud and device-transfer exclusions plus a no-backup installation marker now fail closed | API 35 force-stop, backup refusal, marker-loss and uninstall/reinstall evidence; physical OEM transfer remains a V1 check |
| Resolved A04/A05 | Mutations refreshed Today only and reminder rebuilding was not record-triggered | Shared revisions now preserve cross-screen staleness; a distinct notification-plan revision excludes PRN/zero-update paths | Duplicate-safe cursor/coordinator tests, failed-refresh preservation and Tokyo tomorrow/month/year notification-plan coverage |
| Resolved A06 | Patient `when(tab)` destroyed inactive Compose trees and routed older local reminder dates to History | Visited tabs now remain keyed in one host; hidden tabs cannot receive input/accessibility; all patient local reminders route to Today/exact slot | Lazy creation, local-state, scroll, hidden-semantics/interaction and pure routing tests |
| P0 | Caregiver product is not implemented | Authenticated caregiver renders `SessionReadyScreen` | Build phases C1–C4 before parity can be claimed |
| Resolved B03 | User-visible copy originated from UI, repositories, auth/network fallbacks and notification services | Local copy is resource-backed and typed presentation messages own dynamic parameters; safe backend validation detail remains explicit `Raw` data by contract | Extend typed presentation errors for future caregiver domains instead of reintroducing localized exception text |
| Resolved B01 | Patient feature parsing exposed `JSONObject` at endpoint boundaries | Kotlin serialization wire DTOs now map every patient endpoint into domain models; current/legacy history keys and optional/required-field behavior are fixture-tested | Reuse the same wire/domain boundary for caregiver endpoints |
| Resolved B02 | Session, caregiver selection and patient navigation ownership were mixed or composition-local | Caregiver selection has an independent repository; patient tab/detail/history navigation is saveable; notification preferences and feature data remain separate; UI contains no token or auth-policy access | Reuse these owners in the caregiver shell |
| Resolved B03 | Patient UI was one oversized screen file | Shell, navigation state, Today, History, Settings, Tutorial and shared components now live in separate files while retaining the A06 tab host | Production component capture fixtures and all prior interaction tests pass |
| P1 | Existing patient screenshots predate current iOS behavior | Main added lazy tab lifetime, mutation/history refresh and UI adjustments | Recapture baseline and rerun visual acceptance |
| P2 | Analytics transport parity is absent | Patient off-by-default consent UI/persistence exists, but no Android Firebase wrapper/events or caregiver consent yet | Complete privacy-first Gate H after core caregiver flows |
| P2 | Full dark/large-text/TalkBack coverage is incomplete | High-risk patient surfaces only | Complete per-screen audit, then caregiver and physical device |
| P2 | Physical notification/Doze/process-death evidence is incomplete | Emulator tests do not prove delivery/tap on devices | Complete V1 device matrix |

## 4. Latest-main behavior that must be carried forward

- Link exchange never sends stale Authorization and cannot clear an existing session on public-request failure.
- Scheduled individual/bulk record success is visible before notification maintenance completes.
- Rebuilding reminders retains next-day/month-boundary entries.
- Individual, bulk, PRN and delete actions invalidate history.
- Loaded tabs remain alive; hidden tabs are non-interactive and accessibility-hidden.
- Caregiver proxy bulk can record older missed slots outside the patient recording window.
- Successful caregiver mutation plus failed refresh preserves rendered data.
- Server owns transactional inventory and deletion cascades.
- Account deletion cleans server push devices before local session reset.

## 5. Conservative phase status

| Area | Status | Reason |
|---|---|---|
| Phase 0 build foundation | IMPLEMENTED | Build/test/lint workflow exists; release/device proof remains |
| Shared session/API | RECHECK_REQUIRED | A01/A02 are repaired; installation safety and mutation/freshness contracts remain in A03–A06 |
| Entry/caregiver auth UI | IMPLEMENTED / re-visualize | Main copy/analytics paths and current captures need recheck |
| Patient Today | RECHECK_REQUIRED | Post-record reminder and history invalidation changed |
| Patient History/Settings | IMPLEMENTED / re-visualize | Freshness/lazy-tab behavior changed |
| Patient notification/tutorial | RECHECK_REQUIRED | Next-day rebuild and loaded-tab lifetime need current evidence |
| Caregiver mode | NOT_STARTED | Only auth exists; authenticated product shell is a placeholder |
| Analytics/privacy parity | NOT_STARTED | Planned in X1 |
| Physical release verification | NOT_STARTED | Emulator evidence is not release proof |

## 6. Next execution order

1. R0 public-auth, notification rebuild, history freshness and reinstall regression tests.
2. R1 typed networking, resource copy, state ownership and patient UI decomposition.
3. P1 current-iOS patient re-verification and visual repair.
4. C1 caregiver shell/patient management/tutorial.
5. C2 medication/regimen.
6. C3 caregiver Today/inventory.
7. C4 history/PDF/push/settings/account deletion.
8. X1 analytics/privacy and cross-platform hardening.
9. V1 physical/release verification and final rebaseline.

Do not begin broad caregiver UI before R0 exits. Do not claim a phase complete until its matrix rows are `VERIFIED`.
