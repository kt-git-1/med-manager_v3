# Quickstart: Dose Recording (003)

## API (Next.js + Prisma)

1. Install dependencies
   - `npm install` (from `api/`)
2. Configure env
   - Create `api/.env` with `DATABASE_URL` (Supabase pooled connection string)
   - Supabase Auth JWT verification:
     - HS256: `SUPABASE_JWT_SECRET`
     - ES256 (ECC P-256): set `SUPABASE_URL` (for JWKS fetch) or `SUPABASE_JWT_PUBLIC_KEY`
3. Prisma
   - Ensure `api/prisma.config.ts` is present (v7.3 style)
   - Run `npx prisma migrate dev --name dose_recording` (from `api/`)
4. Run API
   - `npm run dev` (from `api/`)

## iOS (SwiftUI)

1. Open `ios/MedicationApp` in Xcode
2. Configure endpoints (API base URL) and Supabase Auth keys
   - `API_BASE_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` in scheme environment variables
3. Enable notifications
   - Grant notification permission for reminders in the simulator/device
4. Run on simulator/device

## Tests

- API: `npm test` (from `api/`)
- API e2e: `npm run test:e2e` (from `api/`)
- iOS: `Cmd+U` for unit/UI tests

## Validation

- API: `npm test`
- iOS: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
