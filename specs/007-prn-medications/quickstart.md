# Quickstart: PRN Medications

## Scope

- Caregiver can create/edit PRN medications and see PRN badges in medication lists.
- Patient can record PRN doses from Today with confirmation.
- PRN records appear in day history; month summary can include PRN counts if available.
- Inventory adjusts on PRN create/delete when inventory is enabled.
- PRN medications are excluded from notification scheduling.

## Key Endpoints

- `POST /api/patients/{patientId}/prn-dose-records`
- `DELETE /api/patients/{patientId}/prn-dose-records/{prnRecordId}`
- Existing medication create/update endpoints accept `isPrn` and `prnInstructions`.
- Existing history endpoints return PRN items in day detail responses.

## How to Create PRN Medication (Caregiver)

1) Open caregiver medication add/edit.
2) Enable "頓服（必要時に服用）".
3) (Optional) Enter "頓服の説明（任意）".
4) Save. Schedule fields are hidden while PRN is on.

## How Patient Records PRN Dose

1) Open Today tab in patient mode.
2) Find the "頓服" section.
3) Tap "飲んだ" and confirm.
4) Success toast appears; record is created with server time.

## How Caregiver Cancels PRN Record

- Call `DELETE /api/patients/{patientId}/prn-dose-records/{prnRecordId}` with caregiver auth.
- Patient tokens are rejected (403/404 per policy).

## Inventory Effects

- If inventory tracking is enabled:
  - Create PRN record: `inventoryQuantity -= doseCountPerIntake` (clamped at 0)
  - Delete PRN record (caregiver only): `inventoryQuantity += doseCountPerIntake`

## Constraints

- PRN does not create scheduled doses and has no pending/missed concept.
- Patient cannot delete PRN records; caregiver can delete only for linked patients.
- `takenAt` defaults to server time.
- All network calls must use the full-screen "更新中" overlay.

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
