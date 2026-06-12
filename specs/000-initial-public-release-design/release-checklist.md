# Initial Public Release Checklist

## Environment

- iOS `API_BASE_URL` points to the production API domain.
- iOS `SUPABASE_URL` and `SUPABASE_ANON_KEY` point to the production Supabase project.
- Vercel Production `SUPABASE_URL` and `SUPABASE_ANON_KEY` point to the same production Supabase project as the iOS build.
- Vercel Production `SUPABASE_SERVICE_ROLE_KEY` is set and is never embedded in iOS.
- Vercel Production `FCM_SERVICE_ACCOUNT_JSON` is set if caregiver push notifications are enabled.

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
- Notification permission prompts and local reminders behave as expected.
- Caregiver can delete the account from settings, and the session returns to mode selection.
