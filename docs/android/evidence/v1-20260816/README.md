# V1 physical device verification — 2026-08-16

## Scope and artifact

- Published behavior baseline: iOS 1.0.6 Build 51.
- Android branch: `android-dev`; source base before this evidence change: `484458b`.
- Device class: SHARP A302SH, Android 15 / API 35, security patch 2025-02-05.
- Display: 720 x 1520, 280 dpi; Japanese / Asia-Tokyo; gesture navigation; default font scale 1.0; light system theme.
- Artifact: adb-installed Debug APK, `versionCode=1`, `versionName=1.0.6`, SHA-256 `67c5615390a2d470e5d00295c25a4b7359a12e232521cc8dc88a6f962845df82`.
- Package was absent before installation. No existing app or app data was overwritten.
- Device serial, accounts, tokens, linking codes, patient data and network identifiers are not recorded.

This run represents one current non-Google OEM target. It does not represent the required API 26-28 physical target or a Google/reference device. Debug installation cannot close Play Internal, release signing, App Link, production FCM or upgrade gates.

## Executed evidence

| ID | Device/build | Preconditions | Expected | Observed | Result | Evidence |
|---|---|---|---|---|---|---|
| DV-001 | A302SH / API 35 / Debug 1.0.6 (1) | USB debugging authorized; package absent | Build installs without replacing user data | Debug APK installed successfully; package reported 1.0.6 (1) | PASS | Local ADB/Gradle log; identifiers excluded |
| DV-002 | Same | Fresh debug installation | Launcher starts without crash and shows published initial role flow | Splash and role-selection flow rendered; Analytics consent dialog appeared before collection choice; no fatal runtime log | PASS | Redacted visual inspection; screenshot retained outside Git |
| DV-003 | Same | Test APK and synthetic fixtures | Complete Compose UI regression passes on physical API 35 | 272/272 passed after stabilizing the signup reachability assertion | PASS | `:app:connectedDebugAndroidTest`, 6m53s final run |
| DV-004 | Local build | Source change applied | JVM, lint, both APK variants and secret-free Play preflight remain healthy | JVM 202/202; Lint passed; Debug and Release assembly passed; Release compatibility and Play assets passed; local Release APK SHA-256 `8f591e8f7ecbb587a652615e4fd3ab63dfa2c8c76fac043742c06e460e8cc5ad` | PASS | Gradle and compatibility-script reports generated locally |
| DV-005 | Same | Caregiver signup, 720 x 1520 display | Filled submit action remains reachable after IME dismissal in light and dark fixtures | Explicit lazy-list scroll reaches and displays the action; caregiver auth class 20/20 and full suite passed | PASS | `CaregiverAuthFlowScreenTest` and full device run |
| DV-006 | Same | TalkBack installed but disabled | Manual spoken focus order and action operation | Not performed; automated semantics coverage is not substituted for spoken navigation | NOT_RUN | Requires assisted manual pass |
| DV-007 | Same | Default font/light system settings | Manual 200% font, increased display size and dark-system visual pass | Automated dark/200% fixtures passed, but system-setting/manual visual pass was not performed | NOT_RUN | Requires settings changes with restore procedure |

The first full run passed 270/272. One signup test asserted visibility immediately after IME dismissal without scrolling; one PRN test had a non-repeating Compose startup error. Both passed individually. A second full run reproduced only the signup assertion. The test was corrected to verify actual scroll reachability, the caregiver-auth class then passed 20/20, and the final complete run passed 272/272. No production screen dimensions or visual tokens were changed to hide the device-specific test issue.

## Exact physical matrix status

`PASS` below means the exact documented procedure ran. Automated fixture coverage alone is not promoted to a Play, notification-delivery, TalkBack or production-service pass.

| ID | Device/build | Preconditions | Expected | Observed | Result | Evidence |
|---|---|---|---|---|---|---|
| PD-001 | A302SH / Debug | Play Internal install and permission decisions | Fresh Play consent/permission behavior | Debug first launch only; exact Play and deny steps not run | NOT_RUN | Signed Internal AAB required |
| PD-002 | A302SH / Debug | Disposable patient link/session | Session restoration across lifecycle states | No disposable server session configured | NOT_RUN | Test account and server runtime required |
| PD-003 | A302SH / Debug | Verified HTTPS auth callback | App Link opens exactly once | Production App Link not exercised | BLOCKED | Signed Play artifact/domain verification required |
| PD-004 | A302SH / Debug | Prior Play version with both roles saved | In-place upgrade preserves supported state | No prior Play artifact exists on device | BLOCKED | Two Play versionCodes required |
| PD-005 | A302SH / Debug | Authenticated disposable state | Uninstall/reinstall does not restore secrets | Destructive reinstall sequence not run | NOT_RUN | Disposable authenticated setup required |
| PD-006 | A302SH / Debug | OEM backup/device transfer | Secrets fail closed after restore | OEM transfer not run | NOT_RUN | Second/transfer target required |
| PD-007 | A302SH / Debug | Both disposable roles | Tokens never cross roles | Automated repository/UI coverage passed; exact physical server flow not run | NOT_RUN | Disposable production-shaped runtime required |
| PN-001 | A302SH / Debug | API 33+ undecided permission | Contextual permission grant works once | Actual alarm delivery flow not configured | NOT_RUN | Synthetic linked plan required |
| PN-002 | A302SH / Debug | Denied notification permission | Recovery through Settings reconciles state | Not exercised | NOT_RUN | Manual permission flow required |
| PN-003 | N/A | API 26-32 physical device | No runtime permission; channel delivers | No old physical device attached | BLOCKED | API 26-28 physical target required |
| PN-004 | A302SH / Debug | Scheduled synthetic reminders | Foreground/background delivery is single and timely | Not exercised | NOT_RUN | Synthetic linked plan required |
| PN-005 | A302SH / Debug | Scheduled reminder and Doze | Timing matches platform contract | Doze not forced on user's device | NOT_RUN | Controlled reminder window required |
| PN-006 | A302SH / Debug | Scheduled reminder | Cold notification tap reconstructs exact slot | Not exercised | NOT_RUN | Controlled reminder window required |
| PN-007 | A302SH / Debug | Safe old-date diagnostic payload | Opens Today/exact slot | Automated route regression passed; physical notification tap not run | NOT_RUN | Diagnostic notification trigger required |
| PN-008 | A302SH / Debug | Future alarms plus recording | Valid future alarms rebuild correctly | Not exercised | NOT_RUN | Synthetic linked plan required |
| PN-009 | A302SH / Debug | PRN/zero/failure paths | Existing alarms remain unchanged | UI regression passed; alarm inventory not inspected | NOT_RUN | Synthetic linked plan required |
| PN-010 | A302SH / Debug | Pending reminder then disable/logout | Pending display is cancelled or ignored | Not exercised | NOT_RUN | Synthetic linked plan required |
| FC-001 | A302SH / Debug | Production-shaped Firebase and consent | Android token registers once | Firebase runtime values unavailable | BLOCKED | Firebase configuration required |
| FC-002 | A302SH / Debug | Registered token and update/rotation | New token replaces old | Firebase runtime values unavailable | BLOCKED | Firebase configuration required |
| FC-003 | A302SH / Debug | Synthetic FCM messages | Generic display and exact route work | No FCM delivery performed | BLOCKED | Firebase/server delivery required |
| FC-004 | A302SH / Debug | FCM plus Doze/standby | Delivery timing and tap work | No FCM delivery performed | BLOCKED | Firebase/server delivery required |
| FC-005 | A302SH / Debug | Duplicate FCM ID/target | Persistent dedup prevents duplicate | No FCM delivery performed | BLOCKED | Firebase/server delivery required |
| FC-006 | A302SH / Debug | Invalid/privacy payloads | No display/navigation or sensitive logs | No FCM delivery performed | BLOCKED | Firebase/server delivery required |
| FC-007 | A302SH / Debug | Two disposable caregivers | Acting caregiver excluded | No disposable server pair configured | BLOCKED | Firebase/server accounts required |
| FC-008 | A302SH / Debug | Registered token, offline disable | Local stop and retry work | No registered FCM token | BLOCKED | Firebase/server delivery required |
| FC-009 | A302SH / Debug | Disposable account/session | Logout/delete unregister correctly | No registered FCM token/account | BLOCKED | Firebase/server account required |
| FC-010 | A302SH / Debug | Enabled push then OS revoke | UI reconciles permission | No registered FCM token | BLOCKED | Firebase setup and manual pass required |
| IO-001 | A302SH / Debug | Applicable role surfaces | Exact HTTPS destinations and Back work | Automated URL contracts passed; physical browser round-trip not run | NOT_RUN | Manual browser pass required |
| IO-002 | A302SH / Debug | Disposable linking code | Clipboard/share contains only intended text | Not exercised | NOT_RUN | Disposable server code required |
| IO-003 | A302SH / Debug | Billing-enabled diagnostic build | Private `content://` PDF share works | Current release contract is billing-disabled | NOT_RUN | Approved diagnostic build required |
| IO-004 | A302SH / Debug | Diagnostic PDF flow | Cancel/failure recovers and cleans cache | Current release contract is billing-disabled | NOT_RUN | Approved diagnostic build required |
| IO-005 | A302SH / Debug | Billing-disabled Play build | Unsupported PDF entries absent | Automated billing-disabled UI coverage passed; exact Play build not installed | NOT_RUN | Signed Play artifact required |

## Gate impact

- `XP-005` remains `PARTIAL`: physical automated semantics passed, but manual TalkBack, system 200% font, increased display size and IME traversal are still required.
- `XP-006` remains `PARTIAL`: dark-theme automated fixtures passed, but the manual physical visual pass is still required.
- `XP-008` remains `PARTIAL`: one current non-Google OEM/API 35 target is now evidenced; old-supported and Google/reference physical targets remain.
- `XP-010` remains `PARTIAL`: Debug install/build evidence passed, but owner signing and Play Internal/Closed installation are still required.
- Firebase Analytics/FCM production evidence, Play declarations and signed release verification remain external release gates.

No production account, real patient, real medication or health record was used in this run.
