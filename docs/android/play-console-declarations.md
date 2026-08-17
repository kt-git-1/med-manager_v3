# Google Play declaration worksheet

**Status:** implementation-backed draft, not a submitted Console declaration  
**Baseline:** published iOS/API `main@432b34c`, Android `android-dev` C96
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
| Can users request data deletion? | Yes | Caregiver Settings exposes server-first `DELETE /api/me`. Use `https://www.okusuri-mimamori.com/support#section-3` for the Play account-deletion URL: the live public page names お薬見守り, prominently explains in-app deletion and lets an uninstalled/signed-out user initiate deletion by emailing support. |
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

## Resolved Release SDK evidence

C86 resolves the actual `releaseRuntimeClasspath` rather than inferring collection from direct dependency declarations. The current graph contains 175 modules and requires Firebase Analytics, Cloud Messaging and Installations. It contains no Google Mobile Ads SDK, Billing, Install Referrer, Crashlytics, Firebase Performance, AppsFlyer, Adjust, Meta, Sentry, Mixpanel, Amplitude or Segment SDK. The sorted inventory is generated at `android/app/build/reports/release-sdk-inventory.txt` by `verifyReleaseSdkPolicy`; it is build evidence, not a tracked release artifact. C87 checks in strict Gradle lock state for its 174 external modules, so the reviewed graph cannot silently gain, lose or change a version on another machine.

Firebase Analytics currently brings `play-services-ads-identifier` and Privacy Sandbox AdServices support libraries transitively. Their names do not by themselves mean that this app uses advertising identifiers. The independent Release APK gate confirms that `AD_ID`, AdServices attribution/ID/topics and Install Referrer permissions are absent. Both facts must be retained: do not claim that no such support artifact exists, and do not declare advertising use solely from the artifact name.

C88 additionally checks the complete merged Release permission set, not only advertising exclusions. The current APK has exactly Internet, network-state, wake-lock, notification, FCM receive and AndroidX signature-protected dynamic-receiver permissions. Firebase Analytics collection and FCM auto-init are both manifest-off by default. Any added permission, exported component, weakened permission guard, backup/cleartext relaxation or authentication host fails the Release gate and requires an explicit privacy/security review.

C89 validates the generated AAB with strict-locked bundletool and applies the same manifest policy to the protobuf manifest inside `base/manifest/AndroidManifest.xml`. It also rejects an unreviewed feature module or embedded Firebase config, environment file, service-account file or private key/keystore. This is repository evidence for the current unsigned artifact; repeat it on the release-owner-signed production AAB and retain Play's own scan before submission.

C90 validates the upload keystore before that production AAB can be generated: the configured alias must be a usable private-key entry whose certificate SHA-256 equals the Play-registered upload fingerprint. The signed AAB verifier repeats the certificate comparison after generation. Synthetic key coverage proves the mechanism only; it does not answer any Console declaration or replace inspection of the release-owner artifact.

C91 generates `play-release-evidence.json` only after that complete signed path succeeds from clean committed release inputs. The report supplies the exact commit, package/version, AAB/manifest/certificate hashes and reviewed dependency state required for the submission ledger; its synthetic result is still not production evidence.

C92 packages that ledger with its exact commit/version-named AAB and `SHA256SUMS` as one three-file handoff, rejecting a mismatch or conflicting existing directory. Retain and compare the production handoff as a unit; synthetic packaging still does not answer Console declarations.

C93 asks the same locked bundletool to generate a universal APK Set from the exact AAB and reapplies the reviewed manifest, SDK, permission, exported-component, App Links and 16 KB policy to the extracted APK. The APK uses an ephemeral synthetic key, is deliberately excluded from the C92 handoff and cannot answer Play app-signing, optimized split, Console scan or installed-track questions.

C94 builds the complete APK Set and validates the exact four selected base/ABI/Japanese/density APKs for representative API 26/33/35 specifications. The API 35 quartet installs and cleans up on the matching A302SH without launch. This strengthens generated-artifact review but still uses a synthetic certificate and local bundletool selection, so it cannot answer Play app-signing, Play-generated split, Console scan or track-installer questions.

C95 adds strict production and Play-installed App Links verifiers and narrows the Release manifest to the canonical `www` host because the apex Digital Asset Links path redirects. The final Play check must use the independently read app-signing certificate, not the upload certificate, and must show the Google Play installer plus `www.okusuri-mimamori.com: verified`. The current production 404 keeps this evidence open.

SDK-provided coarse technical metadata must still be rechecked against the exact Firebase versions and Google's current disclosure guidance for the final signed AAB. If Google Analytics or Firebase Installations maps any automatically processed field to an additional Play data type, add it even if the app code does not set it directly.

## Health apps declaration draft

| Question area | Draft response |
|---|---|
| Does the app provide health features? | Yes |
| Category | Medical |
| Feature | Medication and Treatment Management |
| Medical device app | No, based on current non-diagnostic/non-recommendation behavior; release owner/legal review required |
| Evidence | Medication schedule CRUD, four daily slots, PRN medication, reminder scheduling, adherence history, caregiver proxy recording and inventory management |

## Submission evidence checklist

- [ ] Retain the complete production C92 three-file handoff; verify `SHA256SUMS` and independently confirm its commit, `versionCode`, `versionName`, AAB SHA-256 and upload-certificate SHA-256 before upload. No release-owner artifact exists yet.
- [x] Inventory and policy-check the resolved Release dependency graph and independently exclude advertising/attribution permissions from the Release APK (C86); repeat against the exact signed AAB below.
- [x] Validate and inspect the current generated unsigned AAB structure/protobuf manifest plus its synthetic-signed universal and API 26/33/35 selected-split surfaces (C89/C93/C94), and fail-close the exact production/installed App Links contract (C95); repeat against the exact signed production AAB and Play-generated artifacts below.
- [ ] Inspect the exact release-owner-signed production AAB and Play scan; attach results to Gate I evidence.
- [ ] Recheck Firebase Analytics, Cloud Messaging and Installations disclosures for the resolved SDK versions immediately before Console submission.
- [ ] Verify production Analytics sharing, retention and consent behavior in Console/DebugView.
- [x] Verify both Android FCM event constructors are data-only and exclude patient display/medication text (C77); retain physical production delivery verification below.
- [ ] Verify the production FCM sender and physical Android delivery preserve the audited data-only envelope on the exact release artifact.
- [x] Verify the public privacy policy and nominated account-deletion URL (`/support#section-3`) are live and match Android behavior (2026-07-15); recheck immediately before submission.
- [ ] Save screenshots/export of submitted Data safety and Health apps answers with date and operator.
- [ ] Repeat review whenever SDKs, billing, OCR/images, crash reporting, advertising, permissions or backend data flows change.
