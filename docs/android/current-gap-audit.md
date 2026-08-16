# Android Current Gap Audit

**Audit date:** 2026-08-16
**Reference:** published iOS 1.0.6 Build 51, `main@432b34c`
**Android branch:** `android-dev`

## 1. Executive result

The Android project contains production Patient and Caregiver flows through Gate G plus the automated portion of privacy-first Analytics in Gate H. C61 safely merged published `main@432b34c` into `android-dev` and revalidated the 322-test API contract. C62-C64 corrected stale tutorial/UI assumptions and audited all 69 parity rows, leaving six external-only release rows. C65-C73 execute the physical gate on one SHARP A302SH non-Google OEM target at Android 15/API 35. C68 measures local notification delivery, task removal, Doze and cancellation; C69 adds legal-link/link-code I/O and denied/standby/force-stop/uninstall branches; C70 adds safe read-only Caregiver network recovery and corrects the initial patient-list Retry gap; C71 proves explicit role logout/isolation; C72 closes Patient-link IME guarding. C73 closes the remaining safe Caregiver form input slice with guarded Patient-create `Done`, explicit medication field-group traversal, non-saving terminal actions and system-Back editor closure. Its live production-account medication draft was discarded and the item count remained unchanged; Patient-create behavior used only physical synthetic fixtures because the retained QA account is already at its approved patient limit. Slot-only generic copy matches iOS and the latest full regression passes 278/278 UI plus 206/206 JVM. Lint, Debug/Release assembly, Release APK compatibility and Play assets pass. Live Firebase, complete spoken TalkBack, two remaining physical device classes, destructive/mutation-interruption paths and Play release operations remain separate gates.

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
| Resolved C33/C49 | Caregiver Today rendered the removed next-action hero and retained stale empty/error/PRN surfaces | Current Android leads with status, optional PRN and slot-colored timeline actions; empty onboarding, initial recovery and full-route PRN now match current iOS | C37 closes populated status/timeline and C49 closes loading/error/empty/PRN plus dark/maximum-text pairs; physical evidence remains |
| Resolved C34 | Caregiver notification parser rejected `DOSE_MISSED` | One shared strict parser now accepts only taken/missed and validates patient/real ISO date/canonical slot before display or navigation | Parser/repository JVM gates plus API-35 Home/History 27/27 prove exact patient/date/slot routing and unknown/malformed rejection |
| Resolved C01/C37–C48 | Existing patient screenshots predated current iOS behavior | Fresh current-runtime entry/auth and Patient UI-001–106 evidence now has explicit owners across C37–C48; C58 separately rechecks the later nonblocking post-record behavior | Keep physical/TalkBack variants in V1 instead of reopening emulator implementation rows |
| P2 | Analytics live verification is pending | Runtime Firebase transport, both-role consent/reset and a privacy-rejecting fixed schema are implemented; C59 adds the previously missing repeatable consent-off/on/reset, DebugView, Realtime, Events and Explore runbook; no local Android Firebase values are available | Supply four environment values and execute `firebase-analytics.md` on a physical device |
| P2 | Production artifact ownership/configuration is pending | The Play task now fails closed on incomplete Firebase/runtime/signing inputs; the Release APK passes application-ID, SDK, forbidden-permission and 16 KB ZIP/ELF checks | Release owner supplies Firebase values and upload key, then verifies the exact signed AAB and Play scan |
| P2 | Full dark/large-text/TalkBack coverage is incomplete | C67 passes all authenticated Patient/Caregiver primary tabs with real system dark plus 200% font on A302SH. TalkBack 15 was enabled and visible focus explored, but ADB injection cannot substitute for spoken order and real-finger double-tap operation | Complete the assisted spoken physical traversal and remaining IME matrix |
| P2 | Cross-device and release-artifact notification evidence is incomplete | C68 proves local background primary, foreground secondary, task-removed cold tap, forced Doze and wait-through-target cancellation. C69 adds denied, force-stop and uninstall paths and records local App Standby as deferred once until exit on the same A302SH Debug target | Complete FCM including its separate App Standby row, exact signed-Play install/update, API 26–28 and Google/reference devices |

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
- Successful Patient and Caregiver mutation feedback remains interactive while the authoritative same-screen reconciliation runs; partial caregiver bulk inventory results retain visible refresh feedback.
- Deleting a caregiver dose restores `MISSED` after the one-hour grace period and `PENDING` before it, pending authoritative reconciliation.
- Server owns transactional inventory and deletion cascades.
- Account deletion cleans server push devices before local session reset.

## 5. Conservative phase status

| Area | Status | Reason |
|---|---|---|
| Phase 0 build foundation | IMPLEMENTED | Build/test/lint workflow exists; release/device proof remains |
| Shared session/API | IMPLEMENTED | A01–A06 auth, installation safety, typed networking, mutation freshness and notification rebuild gates pass; physical OEM transfer remains release evidence |
| Entry/caregiver auth UI | IMPLEMENTED / live/physical verify | C38–C41 close UI-002–005 current-runtime empty/filled light, dark and adaptive evidence. Deterministic failures and callbacks are automated; live link/auth/email plus physical keyboard/OEM IME/TalkBack remain |
| Patient Today | IMPLEMENTED / physical verify | C42/C43 close the current-runtime light Today exceptional pairs; C44 closes dose detail and C45 closes patient PRN across same-data content, loading, failure, insufficient, dark and largest-text pairs. Post-record reminder/history/inventory revisions and success/failure preservation are covered; physical TalkBack/OEM/lifecycle evidence remains |
| Patient History/Settings | IMPLEMENTED / physical verify | C37 closes streak, C46 closes UI-104, C47 removes stale UI-105 reachability, and C48 closes UI-106 top/lower/permission/confirmation light plus dark/maximum-text pairs. Server-first logout intentionally remains stricter than current iOS; physical permission/browser/session/TalkBack evidence remains |
| Patient notification/tutorial | IMPLEMENTED / physical verify | Next-day rebuild, loaded-tab lifetime and routing are covered. C67-C68 add one-device permission, primary/secondary, task-removed tap, Doze and cancellation evidence. The four Patient and ten Caregiver steps render dedicated published sample screens with canonical fixed data/copy, role-correct compact overlays and no live account content in the active accessibility tree. Cross-device/signed-Play/Firebase delivery and spoken TalkBack remain |
| Caregiver mode | IMPLEMENTED / physical verify | C37/C49 close Caregiver Today, C50–C53 close Medication/Inventory, C54 closes History, C55 closes PDF, and C56 closes Settings creation/selection/post-create/patient-scoped state plus the refreshed UI-208 adaptive matrix. Physical FCM/TalkBack/share/browser/destructive evidence remains |
| Analytics/privacy parity | PARTIAL | Code, automated privacy gates and the C59 live-verification procedure are complete; physical-device/Firebase Console evidence awaits environment configuration |
| Physical release verification | PARTIAL | One current non-Google OEM Debug target has authenticated role, appearance, lifecycle, permission, local alarm/Doze/standby/tap/cancellation, legal-link/link-code I/O, read-only cached/cold offline recovery and same-installation role isolation evidence; signed Play plus old/reference devices, destructive/mutation-interruption paths, spoken TalkBack and Firebase remain |

## 6. Next execution order

1. H07 supply Android Firebase values and capture privacy-reviewed DebugView, Realtime, Events and Explore evidence.
2. I02 execute `physical-device-matrix.md` across old-supported, current-reference and non-Google OEM targets, including FCM/Doze/process-state, TalkBack/font/dark/rotation and browser/share rows.
3. Complete signed Play closed-test/release gates and perform the final main rebaseline.

Do not claim a rebaselined row complete until its new-baseline contract and evidence pass.
