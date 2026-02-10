# Quickstart: Free Limit Gates (009)

## Scope

- Free caregivers can register at most 1 patient; attempting to add a 2nd triggers the paywall.
- Premium caregivers can register unlimited patients.
- Backend enforces the limit — client cannot bypass.
- Existing caregivers with >1 patients can still view/manage them (grandfather rule).
- Patient mode remains free with zero billing/paywall UI.

## Prerequisites

- Feature 008 (Billing Foundation) must be fully implemented and deployed.
- Sandbox Apple ID configured for StoreKit2 testing.

## How to Test the Free Limit (Caregiver, Sandbox)

### Free Caregiver — First Patient (Allowed)

1. Open the app in caregiver mode (ensure no premium purchase).
2. Navigate to the patient management screen.
3. Tap "患者を追加" (Add Patient).
4. Enter a display name and save.
5. Patient is created successfully and appears in the list.

### Free Caregiver — Second Patient (Blocked)

1. With 1 patient already linked, tap "患者を追加" again.
2. The paywall is presented instead of the create form.
3. No network request is made to create a patient.
4. The paywall shows: "プレミアムにアップグレード" with purchase and restore options.
5. Tap "閉じる" to dismiss without purchasing.

### Premium Caregiver — Unlimited

1. Purchase premium via sandbox (or restore a previous purchase).
2. Verify "Premium: 有効" is shown in Settings.
3. Tap "患者を追加" — the create form appears.
4. Add a 2nd, 3rd, Nth patient. Each creation succeeds without paywall.

### Grandfather Rule (Existing Multi-Patient Caregiver)

1. Seed a free caregiver account with >1 linked patients (via database fixture or by creating patients before the gate is deployed).
2. Open the app — all patients appear in the list and are selectable.
3. Tap "患者を追加" — the paywall is shown (add is blocked).
4. Existing patients remain fully accessible: view, manage, issue linking codes, record doses.

## Where the Paywall Triggers

| Action | Free (0 patients) | Free (1+ patients) | Premium |
|--------|-------------------|---------------------|---------|
| Add patient | Allowed | Paywall | Allowed |
| View patient list | Allowed | Allowed | Allowed |
| Issue linking code | Allowed | Allowed | Allowed |
| Record dose | Allowed | Allowed | Allowed |

Only the "add patient" action is gated. All other operations are unrestricted.

## Backend Enforcement

The server enforces the limit at `POST /api/patients`:

- If the caregiver is NOT premium and has >= 1 active linked patients, the request is rejected with:
  ```
  HTTP 403
  {
    "code": "PATIENT_LIMIT_EXCEEDED",
    "message": "Patient limit reached. Upgrade to premium for unlimited patients.",
    "limit": 1,
    "current": <active linked count>
  }
  ```
- The iOS app intercepts this error and shows the paywall as a fallback.
- This ensures a modified client cannot bypass the limit.

## Patient Mode

- Patient mode is unaffected by this feature.
- No paywall, upgrade, or premium UI appears in patient mode.
- Patient mode users do not interact with any billing or entitlement logic.

## Error Contract

| Field | Type | Description |
|-------|------|-------------|
| `code` | string | Stable identifier: `"PATIENT_LIMIT_EXCEEDED"` |
| `message` | string | Human-readable (informational, do not parse) |
| `limit` | number | Maximum patients allowed on free plan (currently 1) |
| `current` | number | Caregiver's current active linked patient count |

## "更新中" Overlay Behavior

- If the caregiver's entitlement state is unknown when they tap "add patient", the app refreshes entitlements first, showing the full-screen "更新中" overlay.
- The overlay blocks all user interaction until the refresh completes.
- After refresh, the gate decision is applied (paywall or proceed).

## Local Development

### API

```bash
cd api
npm test
```

### iOS

```bash
xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test
```

### Sandbox Testing Tips

- Use a Sandbox Apple ID configured in App Store Connect.
- To test the free path: ensure no premium purchase exists (or use a fresh account).
- To test the premium path: complete a sandbox purchase, then verify the gate is lifted.
- To test grandfather: use a database fixture to seed >1 patients for a free caregiver.
- To test restore: delete the app, reinstall, tap "購入を復元" in Settings.

## Key File Locations

| Component | File |
|-----------|------|
| Patient Limit Constant | `api/src/services/patientLimitConstants.ts` |
| PatientLimitError | `api/src/errors/patientLimitError.ts` |
| Limit Check (in service) | `api/src/services/linkingService.ts` |
| Route Handler (403 response) | `api/app/api/patients/route.ts` |
| iOS APIError Case | `ios/MedicationApp/Networking/APIError.swift` |
| iOS APIClient (error parsing) | `ios/MedicationApp/Networking/APIClient.swift` |
| iOS Gate (button action) | `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift` |
| FeatureGate (existing) | `ios/MedicationApp/Features/Billing/FeatureGate.swift` |
| PaywallView (existing) | `ios/MedicationApp/Features/Billing/PaywallView.swift` |
| EntitlementStore (existing) | `ios/MedicationApp/Features/Billing/EntitlementStore.swift` |
| Localization | `ios/MedicationApp/Resources/Localizable.strings` |
| Integration Tests | `api/tests/integration/patient-limit.test.ts` |
| Contract Tests | `api/tests/contract/patients.contract.test.ts` |
