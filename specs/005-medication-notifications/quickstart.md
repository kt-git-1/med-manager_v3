# Quickstart: Medication Notifications

## Scope

- Patient local reminders for pending slots (7-day rolling window)
- Settings toggles (master, per-slot, re-reminder)
- Notification tap routing to Today + highlight
- Caregiver in-app banner via realtime dose events

## Notification Format

- Identifier: `notif:{YYYY-MM-DD}:{slot}:{1|2}` (slot = morning/noon/evening/bedtime)
- Body messages:
  - 朝のお薬の時間です
  - 昼のお薬の時間です
  - 夜のお薬の時間です
  - 眠前のお薬の時間です

### Deep Link Example

- Example identifier: `notif:2026-02-05:morning:1`
- Expected behavior: open Today, scroll to morning slot, highlight briefly

## Key Endpoints Used

- `GET /api/patient/history/month` (slot summary source)
- `GET /api/patient/history/day` (refresh today after TAKEN)
- `POST /api/patient/dose-records` (emits dose record event on TAKEN)

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

## Notes

- Scheduling refreshes must show the full-screen "更新中" overlay while foregrounded.
- Notification permission denied → Settings shows guidance and disables toggles.
- Caregiver banners appear only when withinTime is true and app is foregrounded.
