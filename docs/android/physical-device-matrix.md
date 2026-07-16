# Android physical-device verification matrix

**Status:** procedure complete; execution pending attached physical devices and release configuration
**Package:** `com.afterlifearchive.medmanager`
**Owner gate:** V1 / Gate I / `XP-005`, `XP-006`, `XP-008`, `XP-010`

This matrix is required before Android is merged to `main`. Emulator and Compose evidence remain useful regression gates but cannot be copied into a physical result. Every checked row needs the exact build, device, action and observed outcome.

## 1. Required coverage

Use the exact signed Internal-test AAB for the final pass. A locally installed Debug/Release APK may be used for diagnosis, but it does not close Play-installed, signing, App Link, FCM or upgrade rows.

| Target | Minimum purpose | Acceptance |
|---|---|---|
| Old supported physical device | API 26–28, smaller/older hardware if available | Legacy permission, alarm, backup/session, rendering and lifecycle behavior |
| Current Google/reference device | API 35 or current Play-supported Android | Runtime notification permission, Play install/update, App Links, FCM, Doze, process recreation, TalkBack |
| Current non-Google OEM | API 33–35 from a second manufacturer | OEM background limits, permission/settings routing, launcher, browser/Sharesheet, TalkBack/IME |

If one device covers multiple columns, record it once but do not claim cross-OEM coverage. Missing minimum targets leave `XP-008` `PARTIAL`; they are not waived by API 26/33/35 emulators.

For each target record:

- manufacturer/model, Android/API, build number and security patch;
- display size/density, navigation mode, locale/time zone, system font/display scale;
- TalkBack, Google Play services and Play Store versions where relevant;
- artifact commit, `versionCode`, `versionName`, SHA-256 and install source (`Play Internal`, `adb debug`, or `adb release`);
- network type and battery-optimization state;
- disposable test account/patient labels that reveal no real identity or health data.

## 2. Setup and recovery commands

Use the attached target serial explicitly when more than one target is present:

```bash
adb devices -l
adb -s SERIAL shell getprop ro.product.manufacturer
adb -s SERIAL shell getprop ro.product.model
adb -s SERIAL shell getprop ro.build.version.release
adb -s SERIAL shell getprop ro.build.version.sdk
adb -s SERIAL shell dumpsys package com.afterlifearchive.medmanager | grep -E 'versionCode|versionName|installerPackageName'
```

Doze/App Standby diagnosis follows the Android platform procedure:

```bash
adb -s SERIAL shell dumpsys battery unplug
adb -s SERIAL shell dumpsys deviceidle force-idle
# execute the scheduled local-reminder or server-driven FCM case
adb -s SERIAL shell dumpsys deviceidle unforce
adb -s SERIAL shell dumpsys battery reset

adb -s SERIAL shell am set-inactive com.afterlifearchive.medmanager true
# execute the standby case
adb -s SERIAL shell am set-inactive com.afterlifearchive.medmanager false
adb -s SERIAL shell am get-inactive com.afterlifearchive.medmanager
```

Always run the reset commands even after a failed case. Do not request battery-optimization exemption merely to make the test pass.

Process-state cases have different expected contracts:

- **Background:** press Home; notifications and safe navigation must work.
- **Task removed:** swipe the task away; background delivery and next launch must recover without lost session state.
- **Process reclaimed:** with the app backgrounded, use `adb shell am kill com.afterlifearchive.medmanager`; reopen or tap the notification and verify state reconstruction.
- **Force-stopped:** `adb shell am force-stop ...` intentionally places the package in the stopped state. Do not expect alarms/FCM to launch it until the user explicitly opens the app; verify correct recovery after that launch instead of treating platform behavior as an app pass/fail.

## 3. Install, update, session and data isolation

| ID | Procedure | Pass condition |
|---|---|---|
| PD-001 | Fresh Play Internal install, first launch, deny Analytics and notification permission | No collection before consent; app remains usable; permission denial has guidance rather than a loop |
| PD-002 | Patient link with disposable six-digit code; background, task removal and process reclamation | Patient mode/session restores only for the same installation; no relink or wrong-role screen |
| PD-003 | Caregiver email auth/callback via verified HTTPS App Link | Play-installed app opens the intended auth destination exactly once; browser fallback remains valid when unverified |
| PD-004 | Upgrade the same Play track from prior `versionCode` while both roles have saved state | Supported session/preferences survive; migrations do not duplicate tutorial, notifications or selected patient |
| PD-005 | Uninstall then reinstall from Play | Patient/caregiver token, selected patient, mode and consent do not resurrect from Android backup/device transfer |
| PD-006 | OEM backup/restore or device-transfer flow where available | Excluded secrets/installation marker fail closed; restored non-secret UI state cannot authenticate |
| PD-007 | Switch Patient/Caregiver modes repeatedly and sign out | Role tokens never cross requests/screens; server-first logout failure preserves the active session for retry |

Capture only redacted screens and pass/fail timestamps. Never archive tokens, callback query values, email, linking codes or real names.

## 4. Patient local notification matrix

Use a synthetic patient with slot times safely a few minutes ahead. Record expected Tokyo time, actual notification time and device power/process state.

| ID | State | Procedure | Pass condition |
|---|---|---|---|
| PN-001 | API 33+ permission undecided | Enable reminder setting and allow permission | One contextual permission request; setting becomes enabled only after grant |
| PN-002 | API 33+ denied | Deny, retry from Settings, then grant through system settings | No notification while denied; exact guidance; returning refreshes enabled state without duplicate prompts |
| PN-003 | API 26–32 | Enable reminders | No nonexistent runtime-permission step; channel and notification work |
| PN-004 | Foreground/background | Wait for primary and configured secondary reminder | One generic notification per scheduled target; timing/date/slot match the current plan |
| PN-005 | Device idle/Doze | Force idle before the target, then restore device | Behavior matches the scheduler/platform contract and is documented with actual timing; no duplicate after maintenance/exit |
| PN-006 | Task removed/process reclaimed | Trigger reminder, tap from cold state | App reconstructs Patient Today and highlights the exact slot once |
| PN-007 | Older-date payload regression | Exercise the tested notification target through a safe diagnostic build | Patient still opens Today/exact slot, never the removed Patient history-day UI |
| PN-008 | Record individual/positive bulk | Inspect future alarms before and after record | Reminder plan rebuild retains valid next-day/month-boundary entries and removes no unrelated target |
| PN-009 | PRN/zero-update/failure | Perform each path | No scheduled reminder rebuild is emitted; existing future alarms remain |
| PN-010 | Notification disabled/logout/uninstall | Disable or end session before target | Pending patient notifications are cancelled or ignored; no health detail is left in the tray |

The displayed title/body must not include patient identity, medication, dosage, inventory or record result. Notification target data may contain only the validated date/slot fields needed locally.

## 5. Caregiver FCM matrix

Requires production-shaped Firebase values, a disposable caregiver/patient pair and server delivery logs that do not expose message tokens in the evidence artifact.

| ID | State | Procedure | Pass condition |
|---|---|---|---|
| FC-001 | Fresh enable | Grant notification permission and enable caregiver push | FCM initializes only after consent; Android token registers once with `platform=android` and correct environment |
| FC-002 | Token refresh/app update | Rotate/refresh token or reinstall/update through approved test method | New token replaces/registers; old disabled token is not delivered |
| FC-003 | Background/task removed/process reclaimed | Send synthetic `DOSE_TAKEN` and `DOSE_MISSED` | One generic notification; tap selects linked patient and exact History day/slot after strict validation |
| FC-004 | Doze/App Standby | Force each state, send a user-visible high-priority event, then reset | Delivery behavior/timing is recorded; tap works; no follow-up network dependency is required to display generic content |
| FC-005 | Duplicate delivery | Redeliver same FCM message ID and same route target | Persistent message-ID dedup prevents duplicate display; route notification updates rather than stacks |
| FC-006 | Invalid/privacy payload | Send unknown type, missing patient/date/slot and malformed date/slot cases | No notification and no navigation; logs/evidence contain no sensitive rejected value |
| FC-007 | Acting caregiver exclusion | Record through caregiver A while caregiver A/B are linked | A receives no self-notification; eligible other caregiver behavior matches server policy |
| FC-008 | Disable/unregister offline | Disable while offline, reconnect/relaunch | Local display stops immediately; pending unregister retries without silently re-enabling |
| FC-009 | Logout/account deletion | Logout, then delete a disposable account in separate cases | Logout soft-disables/unregisters; successful account deletion clears server device before local reset; failure preserves session/data |
| FC-010 | Permission revoked in system settings | Revoke while app setting remains enabled, resume Settings | UI reconciles actual permission and provides recovery; no notification bypasses OS denial |

## 6. TalkBack, font, dark theme and input

Enable TalkBack from the device Accessibility settings. Navigate using one-finger next/previous focus, double-tap activation and two-finger scrolling; do not validate by sight alone.

Run every reachable `UI-001–106` and `UI-200–208` production surface at default font. Repeat the primary Patient and Caregiver flows at 200% system font and dark theme; repeat one complete flow with display size increased. Record the TalkBack version.

For each screen confirm:

- first focus, heading order and bottom-tab order are logical;
- decorative images/icons are silent; meaningful controls announce role, state, label and medication name only where needed for the current visible action;
- hidden retained tabs, blocking-overlay background content and dismissed sheets/dialogs are absent from accessibility focus;
- status/progress/calendar meaning is announced in text and never color-only;
- switches, menus, date fields, filters, confirmations, Retry, Back and destructive actions are operable without touch exploration traps;
- lazy lists expose every action through two-finger scrolling; focus does not jump to disposed items;
- Japanese labels are not clipped at 200%; keyboard Next/Done order works for link, login, signup, patient creation and medication form;
- orientation/configuration change preserves the selected tab and safe draft state without replaying a mutation or tutorial event.

Any unreachable action, duplicate/blank announcement, sensitive notification speech, focus escape behind a modal or clipped mandatory text is a release blocker.

## 7. Browser, clipboard, share and file boundaries

| ID | Procedure | Pass condition |
|---|---|---|
| IO-001 | Open privacy, terms, support and account-deletion support URL from both applicable roles | HTTPS browser destination is exact; Back returns to intact app/session |
| IO-002 | Issue disposable linking code, copy and share | Clipboard/share contains only the intended ephemeral code text; expiry is clear; dismiss clears in-app ephemeral state |
| IO-003 | Billing-enabled diagnostic PDF with synthetic data | File is generated in private cache, Sharesheet uses `content://`, receiving app can read during grant, no raw path/public residue |
| IO-004 | Cancel/fail PDF target app | Picker remains recoverable and private cache cleanup follows the documented lifecycle |
| IO-005 | Verify billing-disabled Play build | Caregiver PDF entry and every Patient PDF entry are absent; no unsupported purchase claim is reachable |

## 8. Destructive and network-transition flows

Use disposable records only. Capture before/after server state without placing identity/health payloads in Git.

- Toggle airplane mode during initial load and cached refresh; last-known in-memory content, stale warning, Retry and no offline write queue must match `XP-007`.
- Interrupt individual, bulk, PRN, inventory, patient-delete, logout and account-delete requests. A successful write must remain visible through failed reconciliation; an uncertain/failed write must not be replayed automatically.
- Double-tap/high-latency tests must remain single-flight and server-idempotent.
- Revoke patient access and permanently delete a different disposable patient; verify the former preserves data and the latter clears dependent selections/screens only after server success.
- Account-delete failure preserves caregiver session, patients and FCM state for retry; success removes server devices before local cleanup.

## 9. Evidence and acceptance

Create `docs/android/evidence/v1-YYYYMMDD/README.md` with one row per ID and these columns:

| ID | Device/build | Preconditions | Expected | Observed | Result | Evidence |
|---|---|---|---|---|---|---|

Use `PASS`, `FAIL`, `BLOCKED` or `NOT_RUN`; never leave an ambiguous blank. Record defect commit/fix/retest links. Console screenshots, tokens, account details and device identifiers that should not be public stay in approved private storage; the Markdown artifact records a redacted reference and result.

Gate I closes only when:

- every required matrix row passes on its required target;
- H07 Analytics evidence also passes `firebase-analytics.md`;
- the exact Play Internal AAB passes signing/App Link/FCM/install/update checks;
- no open P0/P1 defect remains;
- Closed-test feedback and crash/ANR review meet the release threshold;
- the final rebaseline against then-current `main` has no unresolved API/iOS drift.

## 10. Official references

- Android Doze/App Standby testing: `https://developer.android.com/training/monitoring-device-state/doze-standby`
- Android notification runtime permission: `https://developer.android.com/develop/ui/compose/notifications/notification-permission`
- Android TalkBack navigation: `https://support.google.com/accessibility/android/answer/6006598`

Recheck platform and Play guidance at execution time. OEM setting labels and background policies can differ, which is why the second-manufacturer target is mandatory rather than inferred from a reference device.
