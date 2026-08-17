# C106 Play policy and review-access source readiness — 2026-08-17

## Scope and authority boundary

C106 changes only `android-dev` source and documentation. It does not deploy the website/API, create or modify a Play developer account, submit a declaration, read or store review credentials, identify the dedicated QA account/patient, or claim that a Play artifact is reviewable.

Current official inputs reviewed for this checkpoint:

- [Google Play Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511)
- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)
- [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Google Play app access and review sign-in details](https://support.google.com/googleplay/android-developer/answer/15748846)

## Implemented source boundary

- `/account-deletion` is a stable unauthenticated page that names お薬見守り, explains the in-app path, provides a signed-out email initiation path, enumerates deleted and minimally retained categories, and tells users never to send a password, confirmation code or health data.
- Privacy now explicitly documents HTTPS/TLS with cleartext disabled, authenticated authorization, Android Keystore AES-GCM session-secret storage, limited service-provider access and the exclusion of identity/health data from Analytics.
- Privacy, Support and the global footer all point to the dedicated route.
- `play-review-access.json` contains facts and readiness state only. It stores no email, password, token, code, patient identifier or legal name before verification.
- The retained QA route uses reusable caregiver email/password login and selection of the retained dedicated QA patient. It does not require an expiring one-time patient linking code. This is a code-path finding, not a live credential check.

## Fail-closed contract

`node api/scripts/verify-android-play-policy-readiness.mjs` checks 27 source boundaries. Its synthetic suite accepts the exact source once and rejects 15 drifts covering app identity, signed-out deletion, retention, privacy security, links, cleartext, AES-GCM storage, caregiver login, canonical URL, repository secrets, one-time code use, unverified legal name, false artifact claims and release-mode use while evidence is pending.

`--release` adds hard requirements for:

1. verified Play Organization status and a non-placeholder legal name present in the public privacy policy;
2. verified reusable caregiver credentials stored only outside the repository;
3. region-independent access;
4. an ISO UTC final verification time;
5. the exact positive Play artifact `versionCode`.

Source mode is run in API CI. Release mode intentionally fails at C106 because Organization/legal identity, deployed-route HTTP evidence and final artifact credential verification do not exist yet.

## Remaining external evidence

- Merge and deploy the approved API/web source, then verify the public deletion URL returns HTTP 200 without authentication.
- Finish C105 Organization onboarding and place the verified legal developer identity in the privacy policy.
- Using the exact Play-installed artifact, verify the retained credentials are always available, reusable and region-independent and select only the dedicated QA patient. Never copy the secret or identifiers into logs/screenshots/Git.
- Rerun release mode, reconcile the exact AAB/SDK disclosures, then submit and record Data safety, Health apps, account deletion and app-access declarations under RG-009/RG-010.
