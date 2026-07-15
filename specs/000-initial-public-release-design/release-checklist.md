# Initial Public Release Checklist

## Environment

- iOS `API_BASE_URL` points to the production API domain.
- iOS `SUPABASE_URL` and `SUPABASE_ANON_KEY` point to the production Supabase project.
- Vercel Production `SUPABASE_URL` and `SUPABASE_ANON_KEY` point to the same production Supabase project as the iOS build.
- Vercel Production `SUPABASE_SERVICE_ROLE_KEY` is set and is never embedded in iOS.
- Vercel Production `FCM_SERVICE_ACCOUNT_JSON` is set if caregiver push notifications are enabled.
- Supabase Auth confirm-signup email uses the production template in `docs/operations/supabase-auth-email-template.md`.

## App Store

- App Privacy answers match the implemented data handling.
- Privacy policy URL: `https://okusuri-mimamori.com/privacy`
- Terms URL: `https://okusuri-mimamori.com/terms`
- Support URL: `https://okusuri-mimamori.com/support`
- Support contact email: `support@okusuri-mimamori.com`
- `PrivacyInfo.xcprivacy` is included in the app target.

## Manual Smoke

- Caregiver signup/login works against production Supabase.
- Caregiver can create a patient and issue a linking code.
- Patient can link with the code and record a dose.
- Caregiver can see the dose status.
- Every changed behavior has paired positive and negative acceptance cases; a passing positive case alone is not sufficient.
- Boundary times, retries/idempotency, and failure/recovery paths are covered for every time-sensitive or state-changing feature.
- The release decision records evidence for each acceptance category; any unverified category keeps the release on hold.
- Notification permission prompts and local reminders behave as expected.
- For each notification type, verify both delivery when its condition is met and suppression when its condition is not met.
- A recorded dose does not create a missed-dose `PushDelivery` or a caregiver notification after the cutoff.
- An unrecorded dose creates exactly one missed-dose delivery after the cutoff, and tapping it opens the correct patient/date/slot.
- Caregiver can delete the account from settings, and the session returns to mode selection.
