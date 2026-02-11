# Research: Push Notification Foundation (012)

**Branch**: `012-push-foundation` | **Date**: 2026-02-11 | **Plan**: [plan.md](./plan.md)

## Overview

All unknowns from the Technical Context of the implementation plan resolved via codebase inspection and dependency spec review. Nine decisions documented below.

---

## Decision 1: FCM HTTP v1 vs firebase-admin SDK

**Decision**: Use FCM HTTP v1 REST API directly with Google service account JWT authentication.

**Rationale**: The existing backend uses a lightweight HTTP-based pattern for APNs (`api/src/services/apnsService.ts`) — raw Node.js `http2` + `crypto` with JWT caching. The `firebase-admin` SDK adds ~50MB to the bundle, which risks exceeding Vercel's function size limits on the free plan. FCM HTTP v1 REST API achieves the same functionality with a single HTTPS POST per message and a cached Google OAuth2 access token (RS256 JWT with `firebase.messaging` scope). This keeps the server-side dependency footprint minimal.

**Alternatives Considered**:
- `firebase-admin` SDK: Full-featured but heavy (~50MB). Adds unnecessary dependencies (Firestore, Auth, etc.) when only Messaging is needed. Rejected for bundle size.
- Legacy FCM HTTP API: Deprecated by Google; not recommended for new projects.

---

## Decision 2: APNs Coexistence Strategy

**Decision**: Keep `api/src/services/apnsService.ts` for existing non-TAKEN push paths. Introduce `fcmService.ts` as the new transport for TAKEN notifications.

**Rationale**: The inventory alert push from feature 006 (`notifyCaregiversOfInventoryAlert` in `pushNotificationService.ts`) currently uses APNs via the existing `apnsService.ts`. Migrating all push paths in one feature would be risky and out of scope for 012. By keeping APNs for inventory and introducing FCM for TAKEN, we avoid breaking existing flows while establishing the FCM foundation.

**Alternatives Considered**:
- Big-bang migration: Replace all APNs calls with FCM in one feature. Rejected because it requires coordinating with inventory alert flows and complicates testing.
- Adapter pattern (abstract transport): Over-engineered for 2 push types. Can be introduced when the third push type is added.

---

## Decision 3: PushDevice Model vs Existing DeviceToken

**Decision**: Create a new `PushDevice` model rather than modifying the existing `DeviceToken` model.

**Rationale**: The existing `DeviceToken` model (`api/prisma/schema.prisma`, lines 274-284) stores APNs tokens and lacks the fields needed for 012 (`isEnabled`, `environment`, `lastSeenAt`, `ownerType`). Altering it risks breaking the inventory alert push path that depends on the current schema and `deviceTokenRepo.ts` API. A new model provides a clean separation between legacy APNs tokens and FCM tokens.

**Alternatives Considered**:
- Add columns to `DeviceToken`: Requires migration of existing data, changes to `deviceTokenRepo.ts`, and risk of breaking `listDeviceTokensForCaregivers` which is used by inventory alerts. Rejected.
- Single unified table with `transportType` column: Clean long-term but adds complexity and migration risk to 012 MVP. Can be done as a future consolidation.

---

## Decision 4: Deduplication Strategy

**Decision**: New `PushDelivery` table with UNIQUE(eventKey, pushDeviceId). Insert-or-ignore before each FCM send.

**Rationale**: Vercel may retry failed function invocations. Without dedup, a patient recording TAKEN could result in duplicate pushes to the same caregiver device. A database-level unique constraint provides reliable dedup across serverless retries without in-memory state.

**eventKey construction**:
- Slot bulk: `doseTaken:{recordingGroupId}` (one push per bulk operation)
- Single dose: `doseTaken:{doseRecordEventId}` (one push per individual dose event)
- PRN dose: `doseTaken:prn:{prnDoseRecordId}` (one push per PRN record)

**Alternatives Considered**:
- In-memory dedup (Map/Set): Not reliable on serverless (no persistent memory between invocations). Rejected.
- Redis-based dedup: Adds infrastructure dependency. Overkill for MVP volume. Rejected.
- No dedup (rely on FCM collapse): FCM `collapseKey` only collapses pending-but-undelivered messages, not already-delivered ones. Rejected.

---

## Decision 5: Bulk Push Refactoring

**Decision**: Replace per-record `notifyCaregiversOfDoseRecord` loop in `slotBulkRecordService.ts` with a single call to a new `notifyCaregiversOfDoseTaken` function after all side effects.

**Rationale**: Spec FR-004 requires exactly one push per device per TAKEN action. The current code (lines ~195-205 of `slotBulkRecordService.ts`) calls `notifyCaregiversOfDoseRecord` inside a `for (const record of records)` loop, producing N pushes for N medications. The new function accepts aggregated context (date, slot, recordingGroupId, withinTime, displayName) and sends one FCM message per device.

**withinTime aggregation**: For bulk, use `true` if any recorded dose has withinTime=true. This gives the optimistic "時間内" message when the patient records within the window.

**Alternatives Considered**:
- Client-side dedup (iOS ignores duplicate pushes): Unreliable across background/foreground states. Rejected.
- Batch the per-record calls into one using a queue: Over-engineered for inline fire-and-forget. Rejected.

---

## Decision 6: iOS FCM Token Management

**Decision**: Use Firebase iOS SDK (`FirebaseMessaging`) for FCM registration token lifecycle. `DeviceTokenManager` switches from APNs hex tokens to FCM tokens.

**Rationale**: FCM registration tokens are different from APNs device tokens. The Firebase SDK handles the APNs-to-FCM token mapping internally and provides token refresh callbacks. `DeviceTokenManager` already manages token persistence (UserDefaults) and backend sync — only the token source changes.

**Key changes to `DeviceTokenManager`**:
- Replace `handleDeviceToken(_ deviceToken: Data)` (APNs hex encoding) with `handleFCMToken(_ token: String)`
- `Messaging.messaging().token` instead of `UIApplication.shared.registerForRemoteNotifications()`
- `AppDelegate` still receives APNs token and forwards it to FCM via `Messaging.messaging().apnsToken`
- `MessagingDelegate.messaging(_:didReceiveRegistrationToken:)` handles token refresh

**Alternatives Considered**:
- Manual APNs-to-FCM token mapping via FCM instance ID API: Complex, fragile, and deprecated. Rejected.
- Keep APNs tokens on iOS and convert server-side: Requires server-side import of the APNs token, which FCM doesn't directly support for HTTP v1. Rejected.

---

## Decision 7: Caregiver Settings Tab

**Decision**: Add a 5th tab `.settings` to `CaregiverHomeView` with `CaregiverSettingsView`.

**Rationale**: `CaregiverHomeView` currently has 4 tabs (medications, history, inventory, patients). Push notification settings need a dedicated location. A Settings tab also serves as the future home for billing settings (008-billing-foundation), notification preferences, and profile management. The `PaywallUITests` already reference a "Caregiver Settings" tab, confirming this is the intended direction.

**UI structure**:
- "見守りPush通知" section with ON/OFF toggle
- Toggle default: OFF
- Full-screen "更新中" overlay during register/unregister (reuses `SchedulingRefreshOverlay`)

**Alternatives Considered**:
- Embed push settings in PatientManagementView: Already crowded with patient management UI. Rejected.
- Use a modal/sheet from the tab bar: Non-standard navigation pattern. Rejected.

---

## Decision 8: Deep Link for Caregiver History

**Decision**: Extend `NotificationDeepLinkRouter` to parse remote push `userInfo` dict. `CaregiverHomeView` consumes the deep link target and navigates to History with scroll + highlight.

**Rationale**: The existing deep link system uses `NotificationDeepLinkParser.parse(identifier:)` which expects local notification identifier format `notif:{dateKey}:{slot}:{count}`. Remote push notifications deliver data in the `userInfo` dictionary instead. A new `routeFromRemotePush(userInfo:)` method parses the FCM `data` payload (`type`, `date`, `slot`) and produces the same `NotificationDeepLinkTarget` type, enabling reuse of the existing target propagation mechanism.

**Navigation flow for caregiver**:
1. `NotificationCoordinator.didReceive(response:)` detects remote push via `userInfo` keys
2. Calls `router.routeFromRemotePush(userInfo:)` instead of `router.route(identifier:)`
3. `CaregiverHomeView.onReceive(notificationRouter.$target)` → switches to `.history` tab
4. `HistoryMonthView` receives target → opens date detail → scrolls to slot → applies highlight

**Highlight pattern**: Reuses `TodaySlotHighlight` animation (opacity pulse for ~3 seconds) from `PatientTodayView`.

**Alternatives Considered**:
- Separate deep link handler for push: Redundant; same `NotificationDeepLinkTarget` type works for both local and remote. Rejected.
- URL-based deep links (Universal Links): Over-engineered for push-to-History navigation. Rejected.

---

## Decision 9: NotRegistered Error Handling

**Decision**: On FCM send failure with error code `UNREGISTERED` (or `messaging/registration-token-not-registered`), set `PushDevice.isEnabled = false`. No retry.

**Rationale**: Spec FR-008 requires disabling tokens that return "not registered" errors. Soft-disable (vs hard-delete) preserves the device record for debugging and allows the caregiver to re-enable by obtaining a new token via the Settings toggle. No retry is needed because the token is permanently invalid.

**FCM error codes that trigger disable**:
- `UNREGISTERED` (HTTP v1 error detail)
- `messaging/registration-token-not-registered` (legacy error code, may appear in responses)

**Alternatives Considered**:
- Hard-delete the PushDevice row: Loses audit trail. Re-registration requires a full new row. Rejected.
- Retry with exponential backoff: Pointless for permanent token invalidation. Rejected.
- Disable + send re-registration push to other devices: Over-engineered for MVP. Rejected.
