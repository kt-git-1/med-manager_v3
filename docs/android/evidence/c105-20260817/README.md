# C105 Play Organization account prerequisite — 2026-08-17

**Source baseline:** published iOS 1.0.6 Build 51 at `main@432b34c`

**Android source before C105:** `android-dev@5a82c9598353a51c52a4b4b2d5a4e0da0f01138e`

**Release boundary:** `RG-005` remains unchecked; no developer account, payment, identity submission, Play app, signing key, track or declaration was created or changed

## Live read-only finding

Play Console was opened in the browser's currently selected Google account only to inspect visible state. It redirected to **Create a developer account** and displayed Organization and Personal onboarding choices rather than an application dashboard.

No account type was selected. No agreement was accepted, fee paid, identity/contact/payment information entered, app created, key registered or declaration submitted. No account email or other identifier is retained in this evidence, and the screenshot used for live inspection is intentionally not stored because it contained account information.

## Policy correction

The existing Play worksheet already classifies this product as **Health → Medical → Medication and Treatment Management**. Current official Google Play guidance says developers providing Health apps, including Medical apps, must choose an **Organization** developer account. Organization onboarding requires D-U-N-S-backed organization identity plus matching legal/payment-profile information, website and verified contact details.

Official sources checked on 2026-08-17:

- [Get started with Play Console](https://support.google.com/googleplay/android-developer/answer/6112435) — account creation sequence and one-time US$25 registration fee;
- [Choose a developer account type](https://support.google.com/googleplay/android-developer/answer/13634885) — Health/Medical apps use Organization accounts and Organization onboarding requires D-U-N-S;
- [Required information to create a Play Console developer account](https://support.google.com/googleplay/android-developer/answer/13628312) — organization, website, contact and verification inputs;
- [Play Console Requirements](https://support.google.com/googleplay/android-developer/answer/10788890) — organization ownership, accurate app metadata, privacy/Data safety and review access;
- [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511) — Health declaration and privacy-policy obligations.

The new-Personal-account requirement for 12 opted-in testers over 14 continuous days is not used as this project's account model. Internal and closed testing still remain project release gates; Organization ownership does not weaken the planned test matrix.

## Repository correction

- `play-developer-account-onboarding.md` fixes Organization as the release account type and lists owner inputs/actions without storing their values.
- The Play runbook now starts before signing, at organization/D-U-N-S/payment/contact readiness.
- The declaration worksheet explicitly links Health classification to Organization ownership.
- `RG-005` now includes Play Organization account creation authority, C105 evidence and verified organization/package creation before signing/handoff.
- The release-gate verifier rejects removal of Organization/D-U-N-S/C105 requirements; one accepted and sixteen rejected synthetic ledgers cover the boundary.
- README, master plan, gap audit, parity matrix and backlog use checkpoint C105 without marking any external Play gate complete.

## Owner-controlled next action

Before signup, the release owner must decide and verify the legal publishing entity and its D-U-N-S/payment-profile/website/contact consistency. Only the owner may authorize selecting Organization, accepting the agreement, paying the displayed charge, submitting verification material and creating the production package.

Completion evidence stays status-only. Legal documents, personal/contact details, OTPs, payment data, D-U-N-S values, receipts/transaction IDs, login credentials and private keys must remain outside Git, chat, CI logs and Android evidence.
