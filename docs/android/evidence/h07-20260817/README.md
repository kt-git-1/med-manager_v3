# H07 Firebase Analytics live evidence

**Date:** 2026-08-17
**Branch:** `android-dev`
**Package:** `com.afterlifearchive.medmanager`
**Device class:** SHARP A302SH, Android 15/API 35, Debug APK
**Data boundary:** dedicated QA identities and an ephemeral patient-link code only; no medication, dose, inventory or destructive data mutation

## Configuration result

- The Android Firebase app is registered in the existing project for the exact production package.
- `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID` and `FIREBASE_SENDER_ID` are stored as GitHub Actions secrets and were injected locally only through process environment variables. No Firebase values, `google-services.json`, populated `local.properties`, FCM token or Console export were committed.
- `verifyFirebaseRuntime` fails closed when the four values are absent and passes only when the app ID, API key, project ID and sender ID are structurally valid and the app-ID project number matches the sender ID.
- The complete local `verifyProductionRuntime` passed with production API/Supabase/Firebase/redirect/billing-off inputs supplied ephemerally.

The first configured physical run exposed `Missing google_app_id`: manual `FirebaseOptions` initialization was sufficient for FirebaseApp/FCM but not Analytics. C76 generates the standard Android `google_app_id`, API key, sender and project resources from the same Git-ignored inputs. After the fix, the missing-ID count is zero while manifest collection and FCM auto-init remain disabled by default.

## Physical consent and transport matrix

| Row | Observed | Result |
|---|---|---|
| Fresh pre-decision | Manifest-disabled signal present; zero custom events | PASS |
| Choose `今はしない` | Persisted `decided=true`, `enabled=false`; zero custom events | PASS |
| Relaunch after refusal | Decision remains OFF; dialog does not reappear; zero custom events | PASS |
| Choose `許可する` | Persisted ON; Firebase initializes without missing-ID/upload-disabled errors | PASS |
| Safe mode/tab/tutorial navigation | Only fixed `screen_name`, `mode`, `tab_name` and numeric `step` custom parameters | PASS |
| Network transport | GMS Analytics uploads receive HTTP 204 | PASS |
| Caregiver disable/reset | Toggle OFF invokes reset; subsequent tab navigation adds no custom event | PASS |
| Caregiver re-enable | Subsequent safe tab event resumes and uploads | PASS |
| Patient shared state | Patient Settings sees the Caregiver-disabled state; enabling emits a safe Patient History tab event | PASS |
| Patient final disable | Shared state returns OFF before logout | PASS |
| Privacy scan | Zero forbidden custom identity/health/free-text parameter keys; Firebase user ID remains unset; non-personalized-ads property is 1 | PASS |
| End state | Caregiver and Patient sessions logged out; Analytics OFF; Firebase debug property reset | PASS |

The production publishable-key screen was checked without revealing or using any secret/service-role key. The current client/auth endpoint preflight succeeded with the existing legacy anon client contract; migration to a newer publishable-key contract is not assumed by this evidence.

## Console evidence

- Firebase DebugView received the Android `caregiver_tab_viewed` event. Expanding it showed only standard Firebase parameters plus `tab_name`; expanding `tab_name` showed the fixed value `history`.
- The observed DebugView control window contained four events total after the final enabled navigation (`caregiver_tab_viewed` twice plus automatic `first_open` and `session_start`). A later disabled navigation left that total unchanged.
- Google Analytics Realtime showed one active Android test user and `caregiver_tab_viewed` count 2. The active custom user-property table was empty.
- Device logs independently showed `screen_viewed`, both role-mode selections, all five Caregiver tab enums, one Patient History tab enum and tutorial fixed enums, with successful HTTP 204 uploads.

## C76 regression gates

| Gate | Result |
|---|---|
| Missing Firebase configuration | PASS — `verifyFirebaseRuntime` fails closed and identifies all four missing values |
| Complete Firebase configuration | PASS — `verifyFirebaseRuntime` and the complete `verifyProductionRuntime` both pass with ephemeral inputs |
| Debug JVM | PASS — 216/216, 0 failed/skipped |
| Release JVM | PASS — 213/213, 0 failed/skipped |
| Lint / Debug APK / Release APK | PASS |
| Release APK compatibility | PASS — package/minSdk 26/targetSdk 35, forbidden advertising/attribution permissions absent, 16 KB ZIP/native alignment valid; SHA-256 `04474eaf0149f738fb98689d47c7614fbb5070bea8dbea7a3f1ea376a3c3a550` |
| Play store assets | PASS |
| A302SH synthetic UI regression | PASS — 278/278, 0 failed/skipped; test and app packages removed afterward |
| GitHub Android CI | PASS — run `31959149756` on implementation commit `e914989`; Firebase runtime, Unit tests, Debug build, Lint, Release compatibility and Play listing assets all completed successfully |

The synthetic UI suite uses local fixtures and performs no production API request or health/destructive mutation.

## Remaining H07 evidence

- Recheck the processed Events report after aggregation delay.
- The 2026-08-17 first recheck showed the fixed safe event names in the property-wide Events report, but a temporary Platform comparison offered only iOS. Therefore those aggregate counts cannot be attributed to Android yet. The unsaved comparison was discarded without applying or persisting it.
- Create and remove the temporary privacy-reviewed Explore using event name plus fixed enum only; this is a Console state change and remains separately controlled.
- Preserve redacted Console screenshots outside Git if required by the release owner.

Therefore `XP-004` remains `PARTIAL`: live consent, transport, DebugView and Realtime are verified, while processed Events and Explore are not yet complete.
