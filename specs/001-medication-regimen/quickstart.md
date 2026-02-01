# Quickstart: Medication Regimen (001)

## API (Next.js + Prisma)

1. Install dependencies
   - `npm install`
2. Configure env
   - Create `.env` with `DATABASE_URL` (Supabase pooled connection string)
   - Configure Supabase Auth keys for JWT verification
3. Initialize Prisma v7.3
   - Ensure `prisma.config.ts` exists at repo root
   - Run `npx prisma migrate dev --name init`
4. Run API
   - `npm run dev`

## patientSessionToken (001 stub)

- 001では `patientSessionToken` 検証はスタブです。
- 置換時は `api/src/auth/patientSessionVerifier.ts` を本実装に差し替えます。

## iOS (SwiftUI)

1. Open `ios/MedicationApp` in Xcode
2. Configure endpoints (API base URL)
3. Run on simulator/device

## Tests

- API: `npm test`
- iOS: `Cmd+U` for unit/UI tests
