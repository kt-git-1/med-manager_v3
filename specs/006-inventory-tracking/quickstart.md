# Quickstart: Inventory Tracking

## Scope

- Caregiver-only inventory management per medication
- Automatic inventory updates on TAKEN create/delete (idempotent)
- Realtime low/out banner alerts (no cron, no push)

## Key Endpoints

- `GET /api/patients/{patientId}/inventory`
- `PATCH /api/patients/{patientId}/medications/{medicationId}/inventory`
- `POST /api/patients/{patientId}/medications/{medicationId}/inventory/adjust`

## Realtime Event

- Channel emits inventory alert events on LOW/OUT transitions only.
- Expected payload includes patientId, medicationId, type, remaining, threshold, createdAt, patientDisplayName, medicationName.

## Refill Plan Fields

- Inventory list and update/adjust responses include nullable refill planning fields.
- `dailyPlannedUnits`: planned intake units for today (sum of times * doseCountPerIntake).
- `daysRemaining`: number of days until inventory cannot cover a scheduled day.
- `refillDueDate`: Tokyo date (YYYY-MM-DD) when a refill is needed.
- When inventory is disabled or schedules are missing, all fields are null.
- When remaining quantity is 0, `daysRemaining` is 0 and `refillDueDate` is today (Tokyo).
- Weekday-only schedules are computed by day-by-day simulation using Tokyo day boundaries.

## Constraints

- No cron or always-on workers; event-driven only.
- All inventory API calls must display the full-screen "更新中" overlay.
- Non-owned patient access is concealed as not found.

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
