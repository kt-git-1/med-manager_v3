# Android Firebase Analytics verification runbook

**Status:** implementation and automated privacy gates complete; live Firebase/physical-device evidence pending
**Package:** `com.afterlifearchive.medmanager`
**Source baseline:** `main@3e52fb2`
**Owner gate:** H07 / `XP-004`

This is the canonical procedure for validating the Android Analytics transport. It does not authorize adding Firebase values to Git, enabling collection without consent, or logging health/identity data. Console screenshots are evidence only when they show the exact build and event contract below.

## 1. Required external inputs

The release owner must create/select the Android Firebase app for `com.afterlifearchive.medmanager`, enable Google Analytics for that Firebase project, and supply these four values through Git-ignored `android/local.properties` or CI secrets:

```properties
FIREBASE_APP_ID=...
FIREBASE_API_KEY=...
FIREBASE_PROJECT_ID=...
FIREBASE_SENDER_ID=...
```

Do not commit `google-services.json`, populated `local.properties`, Console exports or screenshots containing unrelated user/device data. This project initializes Firebase from the four runtime values and intentionally keeps `firebase_analytics_collection_enabled=false` in the manifest.

Preflight from `android/`:

```bash
./gradlew :app:verifyProductionRuntime
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Record the tested commit, application version/build, Firebase Android app/package, physical-device model and Android version without recording the advertising ID, FCM token, patient/caregiver identifiers or email.

## 2. Fresh consent-off control

Use a dedicated test Firebase property/device and a non-production test account. Install the configured Debug APK, clear prior app consent, and enable Firebase debug mode:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.afterlifearchive.medmanager
adb shell setprop debug.firebase.analytics.app com.afterlifearchive.medmanager
adb shell monkey -p com.afterlifearchive.medmanager -c android.intent.category.LAUNCHER 1
```

In Firebase Console open **Analytics > DebugView** and select this device. On the first-decision dialog choose `許可しない`, then navigate through mode selection. Confirm that no custom event from the allowlist in section 4 appears during the recorded control window. Automatic/debug transport noise is not proof of a custom event; inspect event names before recording the result.

If DebugView remains empty after consent is enabled, use the official SDK log channel before changing app code:

```bash
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE
adb logcat -v time -s FA FA-SVC
```

Also verify from both Patient and Caregiver Settings that the Analytics toggle is OFF after the same persisted decision.

## 3. Consent-on positive test

Clear app data again, relaunch, choose `許可する`, and perform only synthetic, non-health actions:

1. Remain on mode select long enough to observe `screen_viewed` with `screen_name=mode_select`.
2. Select Patient mode and observe `app_mode_selected` with `mode=patient`.
3. If a safe test patient session exists, switch among Today/History/Settings and observe `patient_tab_viewed` with only `tab_name`.
4. Return to mode selection, enter Caregiver mode with a non-production test account, and switch tabs to observe `caregiver_tab_viewed` with only `tab_name`.
5. Exercise tutorial navigation if required and confirm `tutorial_started`, `tutorial_step_viewed`, and either `tutorial_completed` or `tutorial_skipped` contain only the documented mode/step values.

For every selected DebugView event, expand its parameters. Reject the build if any custom event contains a patient/caregiver ID, name, email, medication, dosage, dose status/time/date, inventory quantity, notification content, linking code, token, URL, arbitrary/free text or Firebase user ID. Do not use real medication or dose actions merely to create Analytics evidence.

## 4. Custom-event allowlist

The executable source of truth is `AnalyticsEventSchema` in `AnalyticsService.kt`. At this baseline, accepted custom events and exact parameter keys are:

| Event family | Exact keys |
|---|---|
| `screen_viewed` | `screen_name` |
| `app_mode_selected`, `tutorial_started`, `tutorial_completed`, `tutorial_skipped` | `mode` |
| `caregiver_tab_viewed`, `patient_tab_viewed` | `tab_name` |
| `tutorial_step_viewed` | `mode`, `step` (`1..20`) |
| `core_action_completed` | `action_name` |
| premium/paywall/purchase-interest events | fixed `feature`, `surface`, and where applicable fixed `result` |
| `premium_activated` | `source` |
| restore events | fixed `surface`, and where applicable fixed `result` |
| signup/login events | fixed `auth_method`, and failure events also fixed `reason` |
| patient-link events | fixed `surface`, and failure also fixed `reason` |

Unknown event names, missing/extra keys and values outside the enums must never reach Firebase. `user_id` must remain unset and ad-personalization signals must remain disabled.

## 5. Disable/reset test

With consent ON and positive events visible, turn Analytics OFF from Patient Settings. Reopen mode selection and navigate for a recorded control window; no new custom events may appear. Re-enable from Caregiver Settings and confirm only subsequent safe events resume. Turn it OFF again before ending the test.

This checks the shared consent store, `setAnalyticsCollectionEnabled(false)` and `resetAnalyticsData()` behavior across both roles. A Console screenshot by itself is insufficient; record the action sequence and observation timestamps.

Disable device debug mode when finished:

```bash
adb shell setprop debug.firebase.analytics.app .none.
```

## 6. Realtime, Events and Explore

DebugView is the immediate instrumentation gate. Aggregated reports are asynchronous, so verify the same test property later without generating extra sensitive traffic:

- **Realtime:** confirm the Android app/device activity and allowlisted event names appear; inspect that no user/health dimension was introduced.
- **Events:** after processing, confirm the selected safe event names and counts. Use DebugView for immediate diagnosis rather than treating report delay as a client defect.
- **Explore:** create a temporary free-form exploration using only event name plus safe fixed enum parameters such as mode/tab. Do not add user ID, device advertising identifiers or health-related custom dimensions. Confirm expected synthetic rows, then delete or archive the temporary exploration according to the release owner's policy.
- **Custom definitions:** do not register a parameter merely because it exists. Register only a privacy-reviewed fixed enum that the approved product report actually needs.

## 7. Required evidence artifact

Create `docs/android/evidence/h07-YYYYMMDD/README.md` and record:

- exact `android-dev` commit and version/build;
- Firebase Android app/package and test property name, without credentials;
- physical device/Android version;
- consent-OFF negative window and result;
- consent-ON DebugView event/parameter matrix;
- disable/reset negative window and cross-role result;
- Realtime, Events and Explore observation times/results;
- confirmation that no forbidden health/identity/free-text fields or Firebase user ID appeared;
- links or redacted filenames for screenshots retained outside Git when Console/project metadata should not be published.

Only after all rows pass may `XP-004` become `VERIFIED`. DebugView alone does not close Realtime/Events/Explore, and emulator-only evidence does not close the physical-device portion of Gate I.

## 8. Official references

- Firebase: `https://firebase.google.com/docs/analytics/debugview`
- Firebase Analytics overview: `https://firebase.google.com/docs/analytics`
- Firebase Android event reporting: `https://firebase.google.com/docs/analytics/android/events`

The official Android debug command is `adb shell setprop debug.firebase.analytics.app PACKAGE_NAME`; it persists until explicitly reset with `.none.`. Recheck the official pages at execution time because Console navigation and processing behavior can change.
