# Quickstart: Push Notification Foundation (012)

**Branch**: `012-push-foundation` | **Date**: 2026-02-11 | **Plan**: [plan.md](./plan.md)

## Prerequisites

- Node.js >= 20
- Xcode (with iOS 26 SDK)
- PostgreSQL (local or Supabase)
- Firebase project (for FCM)

---

## 1. Firebase / FCM Setup

### 1a. Create Firebase Project (if not exists)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a project or select existing
3. Enable Cloud Messaging (FCM)

### 1b. Generate Service Account Key (Backend)

1. In Firebase Console → Project Settings → Service Accounts
2. Click "Generate new private key"
3. Download the JSON file
4. Base64-encode it for the environment variable:

```bash
# macOS
cat firebase-service-account.json | base64 | tr -d '\n' > fcm-key-b64.txt

# Set as environment variable
export FCM_SERVICE_ACCOUNT_JSON=$(cat fcm-key-b64.txt)
export FCM_PROJECT_ID="your-firebase-project-id"
```

5. Add to `.env.local` in `api/`:

```env
FCM_SERVICE_ACCOUNT_JSON=<base64-encoded-service-account-json>
FCM_PROJECT_ID=<your-firebase-project-id>
```

### 1c. iOS Firebase Configuration

1. In Firebase Console → Project Settings → Your Apps → Add iOS app
2. Enter bundle ID (e.g., `com.example.medicationapp`)
3. Download `GoogleService-Info.plist`
4. Add it to `ios/MedicationApp/` in Xcode (ensure it's in the target)

### 1d. Add Firebase SDK via SPM

1. In Xcode → File → Add Package Dependencies
2. URL: `https://github.com/firebase/firebase-ios-sdk`
3. Select `FirebaseMessaging` product
4. Add to `MedicationApp` target

---

## 2. Backend Setup

### 2a. Install Dependencies

```bash
cd api
npm install
```

No new npm packages needed for FCM HTTP v1 (uses built-in `crypto` for JWT).

### 2b. Run Prisma Migration

```bash
cd api
npx prisma migrate dev --name push_device_and_delivery
```

This creates the `PushDevice` and `PushDelivery` tables.

### 2c. Verify Backend Tests

```bash
cd api
npm test
```

Expected: all push-register and push-send-trigger tests pass.

---

## 3. iOS Setup

### 3a. Enable Push Notifications Capability

1. In Xcode → MedicationApp target → Signing & Capabilities
2. Add "Push Notifications" capability
3. Add "Background Modes" → check "Remote notifications"

### 3b. Build and Run

```bash
xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" \
  -scheme "MedicationApp" \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro" \
  build
```

### 3c. Run iOS Tests

```bash
xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" \
  -scheme "MedicationApp" \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro" \
  test
```

---

## 4. Manual Testing Flow

### 4a. Enable Push (Caregiver Mode)

1. Launch app → sign in as caregiver
2. Navigate to Settings tab (5th tab)
3. Toggle "見守りPush通知" ON
4. Accept notification permission prompt
5. Verify "更新中" overlay appears and dismisses
6. Verify toggle shows ON state

### 4b. Trigger TAKEN Push

1. Switch to patient mode (or use second device/simulator)
2. Record a dose as TAKEN (single or bulk)
3. Verify caregiver device receives push notification
4. Notification body should read: "{displayName}さんが薬を服用しました"

### 4c. Test Deep Link

1. Tap the received push notification
2. Verify app opens to History tab
3. Verify correct date is shown in day detail
4. Verify the relevant time slot is scrolled to and highlighted

### 4d. Disable Push

1. Navigate to caregiver Settings
2. Toggle "見守りPush通知" OFF
3. Verify "更新中" overlay appears and dismisses
4. Record another TAKEN → verify no push is received

---

## 5. Simulator Testing (No Real Push)

For testing on the iOS Simulator where real push notifications are not available:

### 5a. Deep Link Testing via Local Notification Substitute

Create a test payload file `push-test-payload.apns`:
```json
{
  "Simulator Target Bundle": "com.example.medicationapp",
  "aps": {
    "alert": {
      "title": "服薬記録",
      "body": "テスト太郎さんが薬を服用しました"
    },
    "sound": "default"
  },
  "type": "DOSE_TAKEN",
  "patientId": "patient-1",
  "date": "2026-02-11",
  "slot": "morning",
  "recordingGroupId": "test-group-id"
}
```

Drag and drop onto the Simulator to trigger the notification.

### 5b. Backend-Only Testing

Test the push register/unregister endpoints without a real device:

```bash
# Register a device (replace token with any string for dev)
curl -X POST http://localhost:3000/api/push/register \
  -H "Authorization: Bearer caregiver-<jwt>" \
  -H "Content-Type: application/json" \
  -d '{"token":"test-fcm-token","platform":"ios","environment":"DEV"}'

# Unregister
curl -X POST http://localhost:3000/api/push/unregister \
  -H "Authorization: Bearer caregiver-<jwt>" \
  -H "Content-Type: application/json" \
  -d '{"token":"test-fcm-token"}'
```

---

## 6. Environment Variables Summary

| Variable | Required | Description |
|----------|----------|-------------|
| `FCM_SERVICE_ACCOUNT_JSON` | Yes (for push send) | Base64-encoded Firebase service account JSON |
| `FCM_PROJECT_ID` | Yes (for push send) | Firebase project ID |
| Existing `APNS_*` vars | No change | Still used for inventory alert push (feature 006) |

---

## 7. Troubleshooting

| Issue | Solution |
|-------|----------|
| Push not received | Check `PushDevice.isEnabled` is true; verify FCM token is valid; check server logs for FCM errors |
| 401 on register/unregister | Ensure using caregiver session token with `caregiver-` prefix |
| Deep link not working | Verify push payload contains `type`, `date`, `slot` in `data` keys |
| "更新中" overlay stuck | Check network connectivity; check server logs for errors |
| FCM returns UNREGISTERED | Token is stale; re-enable push in Settings to get a new token |
