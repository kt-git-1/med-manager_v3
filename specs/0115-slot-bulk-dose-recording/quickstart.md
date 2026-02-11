# Quickstart: Slot Bulk Dose Recording (0115)

## API (Next.js + Prisma)

1. Install dependencies
   - `npm install` (from `api/`)

2. Configure env
   - Ensure `api/.env` has `DATABASE_URL` (Supabase pooled connection string)
   - Supabase Auth JWT verification:
     - HS256: `SUPABASE_JWT_SECRET`
     - ES256: set `SUPABASE_URL` (for JWKS) or `SUPABASE_JWT_PUBLIC_KEY`

3. Prisma migration
   - Run `npx prisma migrate dev --name slot_bulk_recording_group_id` (from `api/`)
   - This adds the `recordingGroupId` nullable column to `DoseRecord`

4. Run API
   - `npm run dev` (from `api/`)

5. Test the endpoint manually
   ```bash
   # Bulk-record morning slot for today (with patient session token)
   curl -X POST http://localhost:3000/api/patient/dose-records/slot \
     -H "Authorization: Bearer <patient-token>" \
     -H "Content-Type: application/json" \
     -d '{"date":"2026-02-11","slot":"morning"}'

   # With custom slot times
   curl -X POST "http://localhost:3000/api/patient/dose-records/slot?morningTime=07:30&noonTime=12:00&eveningTime=19:00&bedtimeTime=22:00" \
     -H "Authorization: Bearer <patient-token>" \
     -H "Content-Type: application/json" \
     -d '{"date":"2026-02-11","slot":"morning"}'
   ```

## iOS (SwiftUI)

1. Open `ios/MedicationApp` in Xcode
2. Configure endpoints (API base URL) and Supabase Auth keys
   - `API_BASE_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` in scheme environment variables
3. Run on simulator/device

### Patient bulk recording flow

1. Log in as patient
2. Navigate to Today tab
3. Each slot (朝/昼/夜/眠前) is displayed as a slot card with:
   - Header: slot name + configured time + status badge + remaining count
   - Body: medication rows (name + dosage, "1回X錠" per row)
   - Summary: "合計Y錠（N種類）"
   - Button: "この時間のお薬を飲んだ" (disabled when remaining = 0)
4. Tap the button → confirmation dialog appears
5. Tap "記録する" → full-screen "更新中" overlay blocks interaction
6. On success → overlay dismisses, toast shown, slot card updates to TAKEN

### Key behaviors to verify

- **withinTime**: takenAt <= scheduledAt + 60 minutes → `withinTime = true`
- **MISSED**: scheduledAt + 60 minutes has passed → doses show MISSED badge, but can still be bulk-recorded (withinTime = false)
- **Idempotent**: bulk-recording the same slot twice results in `updatedCount = 0` on the second call
- **Per-patient slot times**: changing slot times in notification preferences updates the header display and MISSED timing
- **Caregiver view**: after patient bulk-records, caregiver sees TAKEN status in today/history/calendar

## Tests

### Backend

```bash
cd api && npm test
```

Key test file: `api/tests/integration/slot-bulk-record.test.ts`

Tests cover:
- PENDING → TAKEN bulk update (correct updatedCount/remainingCount)
- MISSED → TAKEN allowed (withinTime = false)
- Idempotent (second call returns updatedCount = 0)
- totalPills / medCount correctness
- slotTime reflects custom slot times
- Auth required (401/403 for missing/caregiver tokens)
- Validation errors (422 for invalid date/slot)

### iOS

```bash
xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" \
  -scheme "MedicationApp" \
  -destination "platform=iOS Simulator,name=iPhone 17 Pro" \
  test
```

Key test files:
- `ios/MedicationApp/Tests/SlotBulkRecord/SlotCardRenderingTests.swift` (unit)
- `ios/MedicationApp/Tests/SlotBulkRecord/SlotBulkRecordUITests.swift` (UI smoke)

Unit tests cover:
- Medication row rendering: "薬名+用量", "1回X錠"
- Summary calculation: "合計Y錠（N種類）"
- Button enabled/disabled based on remaining count
- Slot time label derived from scheduledAt

UI smoke tests cover:
- Patient bulk record flow (confirm → overlay → success → TAKEN)
- Overlay blocks taps
- MISSED slot can still be recorded
- Per-patient slot time reflected in header

## Validation

- **API**: `cd api && npm test`
- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Manual**: Patient Today → bulk record a slot → confirm → verify TAKEN status
- **Manual**: Switch to caregiver → verify bulk-recorded doses appear as TAKEN
- **Performance**: Bulk record for slot with 10 medications completes in under 3 seconds
