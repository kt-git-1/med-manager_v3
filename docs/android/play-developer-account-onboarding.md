# Google Play developer account onboarding

This is the owner-controlled prerequisite to every Play signing, track, declaration and review action. It records the current policy and observed Console state; it does not authorize Codex or CI to create an account, accept agreements, pay fees, submit identity documents or create an app.

## 1. Fixed product classification and account type

The Play declaration worksheet classifies this product as **Health → Medical → Medication and Treatment Management** because it manages medication schedules, reminders, adherence records, caregiver proxy recording and inventory. Google Play's current account-type guidance says developers providing Health apps, including Medical apps, must use an **Organization** account.

Therefore:

- Personal account onboarding is rejected for this release even if its UI is easier to enter.
- The Personal-account-only 12-testers-for-14-days production-access rule is not used as this project's policy basis.
- Internal and closed testing remain required by this project's own release gates and quality plan; organization ownership does not waive them.
- Recheck the official account-type and Health-app policies immediately before the owner starts onboarding because Console rules can change.

Official sources:

- [Get started with Play Console](https://support.google.com/googleplay/android-developer/answer/6112435)
- [Choose a developer account type](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Required information to create a Play Console developer account](https://support.google.com/googleplay/android-developer/answer/13628312)
- [Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890)
- [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511)

## 2. Current external state

On 2026-08-17, a read-only navigation to Play Console in the currently selected Google account reached **Create a developer account**, with Organization and Personal choices, rather than an app dashboard. No account type was selected and no app list, signing certificate, track or declaration was available to inspect.

No email address, legal identity, payment profile, D-U-N-S value, contact detail or screenshot containing account information is retained in repository evidence.

## 3. Owner inputs required before signup

Organization onboarding must not begin until the release owner has verified all of these inputs:

1. the legal entity that owns and publishes the app;
2. its D-U-N-S number, legal organization name and address, with exact Dun & Bradstreet consistency;
3. an active organization website;
4. owner/contact and public developer email/phone details that can complete OTP verification and remain operational;
5. the matching Google Payments profile and an authorized payment method;
6. the public developer name and support identity;
7. secure private storage for the registration receipt/transaction identifier and verification records.

Google currently documents a one-time US$25 registration fee. The owner must verify the displayed local-currency amount and agreement at action time. No fee has been paid by this project evidence.

## 4. Owner-controlled sequence

1. Recheck the official account-type, organization-information and Health-app policies.
2. Resolve any organization/D-U-N-S/payment-profile mismatch before opening the signup flow.
3. Sign in with the intended long-lived account owner, select **Organization**, review the developer agreement and complete payment/identity/contact verification.
4. Retain the receipt and verification state privately; repository evidence records only status and date.
5. Create exactly `com.afterlifearchive.medmanager`, enable Play App Signing and read the upload/app-signing certificate SHA-256 values independently.
6. Configure owner-managed upload signing, produce the exact C92 handoff and use Internal testing first.
7. Complete the Health apps declaration, Data safety, app access/review credentials, store listing and all RG-006/RG-009 evidence before closed testing or review.

Account creation, agreement acceptance, payment, identity submission, app creation, key enrollment and Console declaration submission are persistent external changes. Each needs explicit owner authorization at the point of action.

## 5. Privacy-safe completion evidence

Evidence may record only:

- Organization account status and verification date;
- D-U-N-S match as `passed` without the number;
- contact/payment/identity verification as status only;
- registration fee currency/amount and receipt retained privately, without transaction identifier;
- production package registration status;
- public certificate fingerprints only where the existing signing/App Links procedures require them.

Never copy legal documents, personal/contact details, OTPs, payment data, D-U-N-S numbers, receipts, login credentials, private keys or account-identifying screenshots into Git, chat, CI logs or Android evidence.
