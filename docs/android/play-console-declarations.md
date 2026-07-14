# Google Play declaration worksheet

**Status:** implementation-backed draft, not a submitted Console declaration  
**Baseline:** `main@1d9d19e`, Android `android-dev`  
**Recheck:** the exact signed AAB, current Firebase SDK disclosures and Play Console questions immediately before submission

This worksheet separates repository evidence from release-owner/Console decisions. It must not be copied blindly if the production configuration or SDK set changes.

## Official references

- [Google Play Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Firebase Android Play data disclosure guidance](https://firebase.google.com/docs/android/play-data-disclosure)
- [Google Analytics disclosure guidance](https://support.google.com/analytics/answer/11582702)
- [Google Play Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)

Google states that every published app, including closed-test apps, must complete the Health apps declaration. The current official category that matches this product is **Medical → Medication and Treatment Management**: the app manages medication schedules, reminders and treatment adherence. It is not presented as diagnosis, treatment recommendation, a regulated medical device, or an emergency service; the release owner must reconfirm those claims and applicable local law.

## Data collection and security

| Console question | Draft answer | Repository evidence / release check |
|---|---|---|
| Does the app collect or share required user data types? | Yes, collects | Account, patient, medication, adherence, inventory and optional SDK data are sent off-device to service providers. |
| Is all collected user data encrypted in transit? | Yes, after final endpoint verification | Android forbids cleartext traffic. Production API, Supabase and Firebase use HTTPS/TLS. Inspect the signed merged manifest and production environment again. |
| Can users request data deletion? | Yes | Caregiver Settings exposes server-first `DELETE /api/me`; the public privacy/support pages provide contact handling. Verify the Play account-deletion URL field points to a public deletion instruction/request page accepted by current policy. |
| Is data shared with third parties? | Draft: No | Supabase, Vercel and Firebase act as instructed service providers, not advertising recipients. Reconfirm contracts, Console sharing settings and Google's current definition before submission. |

## Data-type mapping

“Required” here means required for the selected product feature, not that every field is mandatory in every UI state. “Optional” is used only when every user can avoid or disable that collection.

| Play data type | Collected | Required / optional | Purpose | Actual flow and exclusions |
|---|---:|---|---|---|
| Personal info — Name | Yes | Required for patient-management use | App functionality, account management | Patient display name goes to the API/Supabase-backed database. It is never sent to Analytics. |
| Personal info — Email address | Yes | Required for caregiver account | Authentication, account management | Caregiver email is handled by Supabase Auth. It is never sent to Analytics or notification payloads. |
| Personal info — User IDs | Yes | Required | Authentication, app functionality, security/fraud prevention | Supabase caregiver ID, generated patient/session IDs and hashed/rotated session records support authorization. Firebase Analytics user ID is always unset. |
| Health and fitness — Health info | Yes | Required for medication-management use | App functionality | Medication name/dose/instructions/schedule, adherence records and related inventory are stored by the API. No health information is sent to Analytics. |
| App activity — App interactions | Yes, only after consent | Optional | Analytics | Fixed allow-listed navigation/tutorial/core-action events only. No names, IDs, medication, dose/date/status, inventory, notification content or free text. Disabling collection resets device Analytics data. |
| Device or other IDs | Yes, feature-dependent | Optional | App functionality, notifications, Analytics | FCM token/Firebase installation identifiers support opt-in notifications; an Analytics app instance exists only after Analytics consent. No advertising ID, AdServices ID/attribution or Install Referrer permission is present. |

## Explicitly not collected by the current Android release

- Precise or approximate location as an app feature
- Contacts, SMS/message content, phone number, audio or browsing/search history
- Photos or videos; prescription image/OCR behavior is not part of this Android release
- Payment information or purchase history; `BILLING_ENABLED=false`
- Crash logs or performance diagnostics through Crashlytics/Performance SDKs; neither SDK is included
- Advertising ID, ad-personalization signals or advertising/marketing attribution
- Generated medication PDF files: generation is on-device and sharing occurs only through the user-invoked Android Sharesheet; the app does not upload the PDF

SDK-provided coarse technical metadata must still be rechecked against the exact Firebase versions in the final dependency report. If Google Analytics or Firebase Installations currently maps any automatically processed field to an additional Play data type, add it even if the app code does not set it directly.

## Health apps declaration draft

| Question area | Draft response |
|---|---|
| Does the app provide health features? | Yes |
| Category | Medical |
| Feature | Medication and Treatment Management |
| Medical device app | No, based on current non-diagnostic/non-recommendation behavior; release owner/legal review required |
| Evidence | Medication schedule CRUD, four daily slots, PRN medication, reminder scheduling, adherence history, caregiver proxy recording and inventory management |

## Submission evidence checklist

- [ ] Record exact commit, `versionCode`, `versionName`, AAB SHA-256 and upload-certificate SHA-256.
- [ ] Inspect the signed AAB manifest and dependencies; attach results to Gate I evidence.
- [ ] Recheck Firebase Analytics, Cloud Messaging and Installations disclosures for the resolved SDK versions.
- [ ] Verify production Analytics sharing, retention and consent behavior in Console/DebugView.
- [ ] Verify production FCM data-only payloads never contain patient/medication text.
- [ ] Verify the public privacy policy and account-deletion web route are live and match Android behavior.
- [ ] Save screenshots/export of submitted Data safety and Health apps answers with date and operator.
- [ ] Repeat review whenever SDKs, billing, OCR/images, crash reporting, advertising, permissions or backend data flows change.
