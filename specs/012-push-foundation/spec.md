# Feature Specification: Push Notification Foundation

**Feature Branch**: `012-push-foundation`  
**Created**: 2026-02-11  
**Status**: Draft  
**Input**: User description: "Push通知の土台を作り、家族モードでPush権限取得・デバイス登録/解除、患者のTAKEN記録時に家族へリアルタイムPush送信、Pushタップで履歴の該当日・該当スロットへ遷移＋ハイライトを実現する。"

**Dependencies**:

- 003-dose-recording: 服用記録（TAKEN/MISSED/PENDING）と withinTime（scheduledAt+60m以内）判定
- 004-history-schedule-view: 家族モード履歴表示（カレンダー→日詳細→スロットスクロール＋ハイライト）
- 005-medication-notifications: 通知権限要求・ローカル通知・バナー表示の既存実装
- 008-billing-foundation: 課金基盤（012のTAKEN Push通知は無料提供、高度Push機能は将来有料）
- 0115-slot-bulk-dose-recording: スロット一括TAKEN記録、recordingGroupId

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Caregiver enables and disables push notifications (Priority: P1)

As a caregiver (family member), I can enable push notifications in the Settings tab so that I receive real-time alerts when my family member takes their medication. I can also disable push notifications at any time to stop receiving alerts.

In the caregiver Settings tab, a "見守りPush通知" section provides an ON/OFF toggle (default OFF). When I toggle ON, the app requests OS notification permission, obtains a push token, and registers the device with the server. A full-screen "更新中" overlay blocks all interaction until the registration completes. When I toggle OFF, the app calls the server to disable the device, again showing the "更新中" overlay until complete.

**Why this priority**: Device registration is the prerequisite for all push delivery. Without a registered device, no push notifications can be received. This story establishes the foundation for all subsequent push functionality.

**Independent Test**: Can be fully tested by opening caregiver Settings, toggling push ON (verifying permission prompt, overlay, and enabled state), then toggling OFF (verifying overlay and disabled state), and confirming server-side device state in each case.

**Acceptance Scenarios**:

1. **Given** a caregiver has not enabled push, **When** they navigate to the Settings tab, **Then** the "見守りPush通知" section shows a toggle in the OFF position.
2. **Given** the toggle is OFF, **When** the caregiver taps to enable, **Then** the OS notification permission prompt appears. If granted, a push token is obtained and sent to the server. A full-screen "更新中" overlay is shown during registration. On success, the toggle shows ON.
3. **Given** the toggle is ON, **When** the caregiver taps to disable, **Then** the server is called to disable the device token. A full-screen "更新中" overlay is shown. On success, the toggle shows OFF.
4. **Given** the caregiver denies OS notification permission, **When** the toggle attempts to enable, **Then** the toggle remains OFF and an explanation message is shown guiding the user to enable permissions in system settings.
5. **Given** a network error occurs during register or unregister, **When** the overlay is shown, **Then** an error message is displayed and the user can retry.

---

### User Story 2 - Patient TAKEN recording triggers push to caregiver (Priority: P2)

As a caregiver, I receive exactly one push notification on my device when my family member (patient) records their medication as TAKEN, so that I am immediately aware of their medication adherence.

When the patient records a dose as TAKEN (either individually or via slot-bulk recording), the system sends a push notification to all linked caregivers who have push enabled. The notification body says "{displayName}さんが薬を服用しました". If the dose was recorded within the scheduled time window (withinTime=true), the body may say "{displayName}さんが時間内に薬を服用しました！". Bulk recording of multiple medications in one slot produces exactly one push per device (not one per medication). The system prevents duplicate pushes on retries using a deduplication mechanism.

**Why this priority**: Real-time TAKEN notification is the core value proposition of the push foundation. It provides caregivers with immediate peace of mind about medication adherence without needing to manually check the app.

**Independent Test**: Can be fully tested by having a patient record a dose and verifying that the linked caregiver's device receives exactly one push notification with the correct content.

**Acceptance Scenarios**:

1. **Given** a caregiver has push enabled and is linked to a patient, **When** the patient records a single dose as TAKEN, **Then** the caregiver receives exactly one push notification with body containing the patient's display name and a medication taken message.
2. **Given** a caregiver has push enabled, **When** the patient bulk-records 3 medications in a morning slot (producing a recordingGroupId), **Then** the caregiver receives exactly one push notification (not 3).
3. **Given** the patient records TAKEN but updatedCount is 0 (already recorded / no state change), **When** the system evaluates push delivery, **Then** no push notification is sent.
4. **Given** the same bulk record triggers a retry (e.g., network retry on Vercel), **When** the system processes the duplicate trigger, **Then** no additional push is sent for the same event (deduplication).
5. **Given** a patient has no linked caregiver with push enabled, **When** the patient records TAKEN, **Then** no push is sent and the patient experiences no error or delay.
6. **Given** multiple caregivers are linked and have push enabled, **When** the patient records TAKEN, **Then** each caregiver's enabled devices receive exactly one push.

---

### User Story 3 - Push tap navigates caregiver to History detail (Priority: P3)

As a caregiver, when I tap a push notification about a patient's medication, the app opens to the History tab showing the specific date and scrolls to the relevant time slot with a visual highlight, so that I can immediately see the details of the recording.

**Why this priority**: Deep linking from push to the relevant History detail closes the notification-to-context loop. Without it, caregivers would need to manually navigate to find the relevant information, reducing the value of push notifications.

**Independent Test**: Can be tested by simulating a push tap with a deep link payload and verifying the app navigates to the correct History date/slot with highlight.

**Acceptance Scenarios**:

1. **Given** the caregiver app is in the background, **When** the caregiver taps a TAKEN push notification, **Then** the app opens to the History tab, displays the date detail for the date specified in the push payload, scrolls to the relevant slot section, and applies a brief visual highlight (pulse or glow) for a few seconds.
2. **Given** the caregiver app is completely closed, **When** the caregiver taps a TAKEN push notification, **Then** the app launches and navigates to the same History date/slot detail with highlight.
3. **Given** the push payload contains date "2026-02-11" and slot "morning", **When** the caregiver taps the push, **Then** the History view opens to February 11, 2026 and scrolls to the morning slot section.
4. **Given** the caregiver app is in the foreground, **When** a TAKEN push arrives, **Then** an in-app banner is displayed (reusing existing banner mechanism). Tapping the banner navigates to the History date/slot detail.

---

### User Story 4 - Device token lifecycle management (Priority: P4)

As a system, device tokens are automatically maintained so that push notifications are reliably delivered to active devices and stale tokens do not waste resources.

When a push delivery fails with a "token not registered" error, the system disables that device token to prevent future failed deliveries. When the caregiver app launches or returns to the foreground, the app checks if the push token has changed and re-registers if needed.

**Why this priority**: Token lifecycle management is important for long-term reliability but is less visible to users than the core push flow. It prevents silent push failures from accumulating over time.

**Independent Test**: Can be tested by simulating a "not registered" push response and verifying the token is disabled, and by simulating a token refresh on app launch and verifying re-registration.

**Acceptance Scenarios**:

1. **Given** a push is sent to a device token, **When** the push transport returns a "not registered" or equivalent error, **Then** the system marks that device token as disabled (isEnabled=false) so no future pushes are attempted to it.
2. **Given** the caregiver app has push enabled, **When** the app launches or returns to the foreground and the push token has changed, **Then** the app sends the new token to the server, updating the device registration.
3. **Given** a device token has been disabled due to an error, **When** the caregiver re-enables push in Settings, **Then** a new token is obtained and registered, replacing the disabled one.

---

### Edge Cases

- **OS permission denied**: Toggle remains OFF, user sees a message explaining how to enable in system settings. No server call is made.
- **No linked caregiver**: Push is silently skipped when the patient records TAKEN. No error is shown to the patient, and no delay is introduced.
- **App foregrounded when push arrives**: An in-app banner is shown using the existing banner mechanism instead of (or in addition to) the system push notification.
- **Network failure during register/unregister**: The overlay shows an error message. The user can retry. The toggle state is not changed until the operation succeeds.
- **Multiple caregivers linked to one patient**: All caregivers with push enabled receive the notification. The system does not hardcode a 1:1 relationship.
- **Duplicate API retries (Vercel/network)**: A deduplication mechanism prevents the same event from producing multiple push notifications to the same device.
- **Patient has no scheduled medications but records PRN**: PRN dose recording also triggers a TAKEN push if the existing flow results in a TAKEN state change.
- **Caregiver has multiple devices**: Each enabled device receives exactly one push per event.
- **Push payload does not contain PHI**: No medication names, dosage information, or health data appears in the push notification body or payload. Only the patient's display name and generic text are included.
- **Environment mismatch (dev/prod)**: Development and production push tokens are kept separate so test pushes do not reach production devices and vice versa.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Caregiver MUST be able to enable push notifications via a toggle in the Settings tab under a "見守りPush通知" section.
- **FR-002**: Enabling push MUST trigger the OS notification permission request, obtain a push token from the push transport, and register the device with the server. The toggle defaults to OFF.
- **FR-003**: Disabling push MUST call the server to mark the device as disabled (soft-disable, not hard-delete), and the toggle MUST reflect the OFF state on success.
- **FR-004**: System MUST send exactly one push notification per device when a patient records TAKEN (whether via individual or slot-bulk recording).
- **FR-005**: When a recordingGroupId is present (slot-bulk), the system MUST use it as a deduplication key to ensure one push per group per device. When absent, the system MUST derive a dedup key from the event context to prevent duplicate pushes on retries.
- **FR-006**: The push payload MUST include: event type identifier, patientId, date in YYYY-MM-DD format (Tokyo timezone), time slot (morning/noon/evening/bedtime), and recordingGroupId (if available).
- **FR-007**: Tapping a push notification MUST navigate the caregiver app to the History tab, open the date detail for the specified date, scroll to the specified slot section, and apply a visual highlight (pulse or glow) for several seconds.
- **FR-008**: System MUST disable device tokens that return "not registered" or equivalent transport errors, setting them to isEnabled=false so no future pushes are attempted.
- **FR-009**: A full-screen "更新中" overlay MUST block all user interaction during device register and unregister operations.
- **FR-010**: Push notifications MUST only be sent to caregiver devices that are linked to the patient who recorded TAKEN. No cross-patient push delivery is permitted.
- **FR-011**: Push notification body MUST NOT contain medication names, dosage information, or other protected health information beyond the patient's display name and a generic medication-taken message.
- **FR-012**: TAKEN push notifications MUST be provided free of charge (no billing gate). Advanced push features (escalation, inventory alerts, quiet hours, frequency settings) are explicitly excluded from this feature and reserved for future paid tiers.

### Non-Functional Requirements

- **NFR-001 (Performance)**: Push notification delivery to the transport service MUST complete within 15 seconds of the TAKEN recording under normal conditions. The push send operation MUST NOT block the TAKEN API response to the patient.
- **NFR-002 (Security/Privacy)**: Device register and unregister endpoints MUST require caregiver authentication. Push payloads MUST contain only identifiers (patientId, date, slot) and generic localized text. No PHI beyond display name is permitted.
- **NFR-003 (Reliability/Infrastructure)**: Push delivery MUST NOT depend on cron jobs, background workers, or persistent queues. Push MUST be triggered inline as a side effect of the TAKEN recording. The feature MUST work within serverless hosting constraints (e.g., Vercel free plan).
- **NFR-004 (UX/Accessibility)**: The push toggle, overlay, error messages, and in-app banners MUST support VoiceOver with descriptive labels. All user-facing strings MUST use localized string resources.

### Key Entities

- **Push Device**: A registered device belonging to a caregiver. Key attributes: owner identifier, push token, platform (iOS), environment (dev/prod), enabled state, and last-seen timestamp. Uniquely identified by (owner, token). Used to determine which devices should receive push notifications.
- **Push Delivery** (recommended): A deduplication record linking an event key to a device. Key attributes: event key (derived from recordingGroupId or event context), device reference, and creation timestamp. Uniquely identified by (eventKey, deviceId). Prevents duplicate push notifications when the same event is triggered multiple times (e.g., API retries).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Caregiver can enable and disable push notifications within 5 seconds (from toggle tap to overlay dismiss and state reflected).
- **SC-002**: When a patient records TAKEN, the linked caregiver receives exactly 1 push notification per device within 15 seconds.
- **SC-003**: 100% deduplication: repeated TAKEN triggers for the same recording group or event produce no additional pushes beyond the first per device.
- **SC-004**: Tapping a push notification opens the History view at the correct date and slot in under 2 seconds from tap.
- **SC-005**: All required tests pass: backend integration tests (device register, unregister, push send trigger, dedup, auth, linkage) and iOS unit/UI smoke tests (toggle, overlay, deep link navigation).

## Assumptions

- FCM (Firebase Cloud Messaging) is used as the push transport, replacing the existing APNs direct integration. The backend will migrate from APNs HTTP/2 to the FCM Admin SDK for push delivery.
- MVP supports iOS only. The backend design (push device model, send service) supports future Android/web expansion without schema changes.
- 1 patient : 1 caregiver is the common case, but the design supports multiple caregivers per patient. Each caregiver's enabled devices receive push independently.
- withinTime is computed server-side as `takenAt <= scheduledAt + 60 minutes` (Asia/Tokyo timezone) and included in the push trigger context for message body variation.
- Patient mode has no push settings UI in this feature. Only caregivers configure push preferences.
- The "更新中" full-screen overlay pattern already exists in the application (per Global UX convention) and is reused for push register/unregister operations.
- The existing deep link and banner mechanisms from 005-medication-notifications can be extended for remote push deep linking without fundamental architecture changes.
- The push notification for TAKEN events is free (no billing gate). Future push features (escalation after X minutes of non-recording, inventory low alerts, quiet hours, notification frequency) will be gated by paid tiers.
- No server-side cron or persistent worker is required. Push is sent inline (fire-and-forget) as part of the API request that records TAKEN.
