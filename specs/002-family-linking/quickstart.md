# Quickstart: Family Linking (002)

## API (Next.js + Prisma)

1. Install dependencies
   - `npm install` (from `api/`)
2. Configure env
   - Create `api/.env` with `DATABASE_URL` (Supabase pooled connection string)
   - Configure Supabase Auth keys for JWT verification (`SUPABASE_JWT_SECRET`)
3. Prisma v7.3
   - Ensure `api/prisma.config.ts` is present (v7.3 style)
   - Run `npx prisma migrate dev --name family_linking` (from `api/`)
   - Run `npx prisma migrate dev --name linking_attempts` (from `api/`)
4. Run API
   - `npm run dev` (from `api/`)

## patientSessionToken (002 real verification)

- 001のスタブ検証は `api/src/auth/patientSessionVerifier.ts` を本実装に置換する。
- PatientSession を参照して tokenHash を検証し、revokedAt で失効判断する。

## iOS (SwiftUI)

1. Open `ios/MedicationApp` in Xcode
2. Configure endpoints (API base URL) and Supabase Auth keys
   - `API_BASE_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` in scheme environment variables
3. Run on simulator/device

## Tests

- API: `npm test` (from `api/`)
- iOS: `Cmd+U` for unit/UI tests

## Validation

- API: `npm test`
- iOS: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
