# Android Current Gap Audit

**Audit date:** 2026-07-16
**Reference:** `main@1cf8aef`
**Android branch:** `android-dev`

## 1. Executive result

The Android project contains production Patient and Caregiver flows through Gate G plus the automated portion of privacy-first Analytics in Gate H. C31 formally rebased the contract from `main@1d9d19e` to `main@1cf8aef`; C32–C34 closed the three resulting Android parity gaps, C35/C36 passed the current regression matrices, C37–C41 closed fresh matched Patient History streak, Caregiver Today status and the complete entry/auth UI-002–005 matrix, C42/C43 closed the emulator-verifiable UI-101 exceptional states, and C44 closed the current-runtime UI-102 dose-detail matrix. Remaining gaps are explicitly live Firebase, other product-screen/adaptive visual residuals, physical-device evidence and Play release operations.

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
| Resolved C1–G | Caregiver product was not implemented | Five-tab caregiver shell, patient management, medication/regimen, Today, inventory, History, PDF, push and complete Settings now use production repositories and exact API contracts | Gate G automated acceptance passes; visual/physical release residuals remain Gate I |
| Resolved B03 | User-visible copy originated from UI, repositories, auth/network fallbacks and notification services | Local copy is resource-backed and typed presentation messages own dynamic parameters; safe backend validation detail remains explicit `Raw` data by contract | Extend typed presentation errors for future caregiver domains instead of reintroducing localized exception text |
| Resolved B01 | Patient feature parsing exposed `JSONObject` at endpoint boundaries | Kotlin serialization wire DTOs now map every patient endpoint into domain models; current/legacy history keys and optional/required-field behavior are fixture-tested | Reuse the same wire/domain boundary for caregiver endpoints |
| Resolved B02 | Session, caregiver selection and patient navigation ownership were mixed or composition-local | Caregiver selection has an independent repository; patient tab/detail/history navigation is saveable; notification preferences and feature data remain separate; UI contains no token or auth-policy access | Reuse these owners in the caregiver shell |
| Resolved B03 | Patient UI was one oversized screen file | Shell, navigation state, Today, History, Settings, Tutorial and shared components now live in separate files while retaining the A06 tab host | Production component capture fixtures and all prior interaction tests pass |
| Resolved C32 | Patient History lacked the server-defined recording streak | API-045 now has strict typed mapping and independent state; the current-iOS card/copy sits between progress and week | Contract/repository tests pass; C37 adds fresh matched light and dark/maximum-text runtime evidence; physical evidence remains |
| Resolved C33 | Caregiver Today rendered the removed next-action hero | Current Android now leads with status, then optional PRN and slot-colored timeline actions; stale next copy/resources are absent | C37 adds fresh current-iOS/Android light and dark/maximum-text runtime pairs after closing scheduled-time/icon drift; physical evidence remains |
| Resolved C34 | Caregiver notification parser rejected `DOSE_MISSED` | One shared strict parser now accepts only taken/missed and validates patient/real ISO date/canonical slot before display or navigation | Parser/repository JVM gates plus API-35 Home/History 27/27 prove exact patient/date/slot routing and unknown/malformed rejection |
| P1 | Existing patient screenshots predate current iOS behavior | Main added lazy tab lifetime, mutation/history refresh and UI adjustments | Recapture baseline and rerun visual acceptance |
| P2 | Analytics live verification is pending | Runtime Firebase transport, both-role consent/reset and a privacy-rejecting fixed schema are implemented; no local Android Firebase values are available | Supply four environment values and capture DebugView then Realtime/Events/Explore evidence |
| P2 | Production artifact ownership/configuration is pending | The Play task now fails closed on incomplete Firebase/runtime/signing inputs; the Release APK passes application-ID, SDK, forbidden-permission and 16 KB ZIP/ELF checks | Release owner supplies Firebase values and upload key, then verifies the exact signed AAB and Play scan |
| P2 | Full dark/large-text/TalkBack coverage is incomplete | Patient primary dark captures plus Caregiver primary dark-plus-200% captures/action paths are complete; matched variants and full traversal remain | Complete matched per-screen audit and physical-device traversal |
| P2 | Physical notification/Doze/process-death evidence is incomplete | Emulator tests do not prove delivery/tap on devices | Complete V1 device matrix |

## 4. Latest-main behavior that must be carried forward

- Link exchange never sends stale Authorization and cannot clear an existing session on public-request failure.
- Scheduled individual/bulk record success is visible before notification maintenance completes.
- Rebuilding reminders retains next-day/month-boundary entries.
- Individual, bulk, PRN and delete actions invalidate history.
- Loaded tabs remain alive; hidden tabs are non-interactive and accessibility-hidden.
- Caregiver Today is status-focused: no next-dose hero/top bulk action; eligible timeline rows own record actions.
- Caregiver proxy bulk can record older missed slots outside the patient recording window.
- Patient History shows the server-defined recording streak without coupling its failure to ordinary history.
- Both `DOSE_TAKEN` and `DOSE_MISSED` caregiver pushes route to exact History after strict validation.
- Successful caregiver mutation plus failed refresh preserves rendered data.
- Server owns transactional inventory and deletion cascades.
- Account deletion cleans server push devices before local session reset.

## 5. Conservative phase status

| Area | Status | Reason |
|---|---|---|
| Phase 0 build foundation | IMPLEMENTED | Build/test/lint workflow exists; release/device proof remains |
| Shared session/API | IMPLEMENTED | A01–A06 auth, installation safety, typed networking, mutation freshness and notification rebuild gates pass; physical OEM transfer remains release evidence |
| Entry/caregiver auth UI | IMPLEMENTED / live/physical verify | C38–C41 close UI-002–005 current-runtime empty/filled light, dark and adaptive evidence. Deterministic failures and callbacks are automated; live link/auth/email plus physical keyboard/OEM IME/TalkBack remain |
| Patient Today | IMPLEMENTED / physical verify | C42/C43 close the current-runtime light Today exceptional pairs; C44 closes same-data detail content, empty memo, loading, failure, dark and largest-text pairs. Post-record reminder/history/inventory revisions and failure preservation are covered; physical TalkBack/OEM/lifecycle evidence remains |
| Patient History/Settings | IMPLEMENTED / re-visualize | C37 closes matched streak light/dark/maximum-text evidence; other exceptional Settings/History states and physical evidence remain |
| Patient notification/tutorial | IMPLEMENTED / physical verify | Next-day rebuild, loaded-tab lifetime, routing and tutorial actions are covered; physical permission/tap/TalkBack remain |
| Caregiver mode | IMPLEMENTED / re-visualize | C37 closes fresh matched status-first Today light/dark/maximum-text evidence; other exceptional flows and physical FCM/TalkBack evidence remain |
| Analytics/privacy parity | PARTIAL | Code and automated privacy gates complete; Firebase Console evidence awaits environment configuration |
| Physical release verification | NOT_STARTED | Emulator evidence is not release proof |

## 6. Next execution order

1. Complete remaining emulator-verifiable fresh iOS/Android visual pairs outside the C42–C44 UI-101/UI-102 matrices; the C36 187-test API 26/33/35 matrix is complete at 561/561 and the expanded current API-35 suite passes 206/206 after C44.
2. H07 supply Android Firebase values and capture privacy-reviewed DebugView, Realtime, Events and Explore evidence.
3. I02 complete physical FCM/Doze/process-death, TalkBack/font/dark/rotation and browser/share checks.
4. Complete signed Play closed-test/release gates and perform the final main rebaseline.

Do not claim a rebaselined row complete until its new-baseline contract and evidence pass.
