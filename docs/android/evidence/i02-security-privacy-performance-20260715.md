# I02 Security, dependency, privacy and performance review — 2026-07-15

## Release artifact and manifest

- `assembleRelease` and Android Lint pass. The current unsigned universal Release APK is about 16 MB; Play delivery size must be measured from the later signed App Bundle.
- No `google-services.json`, `local.properties`, keystore, service-account file or equivalent secret path is tracked by Git.
- Merged Release Manifest keeps `allowBackup=false`. App-owned reminder receiver, FCM service and FileProvider are non-exported; only launcher/deep-link activity and SDK signature-protected receivers are exported.
- The app requests only Internet, network state, wake lock, notifications and FCM receive permissions plus an AndroidX dynamic-receiver protection permission.

Firebase Analytics originally merged `AD_ID`, AdServices ID/attribution and Play Install Referrer permissions despite the product's no-advertising contract. Android now removes all four explicitly with manifest merge rules. A rebuilt Release APK was inspected through `apkanalyzer` and none remain.

## Dependency and backend security

- `npm audit --omit=dev --audit-level=high` reports no high or critical vulnerability. It reports 13 moderate transitive findings under Prisma tooling, Next/PostCSS and Firebase Admin/Google Cloud libraries.
- npm's proposed `--force` remediation would make breaking major downgrades (`prisma@6`, `next@9`, `firebase-admin@10`), so it was not applied. Upgrade them only through separate compatibility-tested dependency work.
- `npm run check:release-security` confirms required database, Supabase and FCM production variables are set in the inspected API environment. `APPLE_ROOT_CA_PEM` and `PREMIUM_PRODUCT_ID` remain recommended-missing; Android/iOS billing is currently disabled, but production IAP enablement must not proceed without them.
- API 300/300 tests and TypeScript typecheck pass at this checkpoint.

## Privacy behavior

- Analytics is manifest-off and explicit-consent only, has no user ID/ad personalization and rejects non-enum identifiers/free text at the wrapper boundary.
- Push display copy is generic on Android; FCM data is limited to strict navigation fields and device/event deduplication. Account deletion removes server devices before local FCM/session cleanup.
- PDF output remains private-cache plus temporary FileProvider access. Screenshot fixtures were moved from MediaStore to test-app cache, eliminating legacy storage permission and user-gallery pollution.

## Initial performance observation

On the API 35 ARM emulator, five force-stopped Debug launches reported 1030–1100 ms `TotalTime` (about 1062 ms average). After launch, Debug `TOTAL PSS` was about 121 MB and `TOTAL RSS` about 218 MB. These are diagnostic emulator/Debug values, not release acceptance numbers.

Positive controls already present include lazy persistent tabs, bounded cross-tab revision refresh, server bulk endpoints, no Analytics initialization before consent and AndroidX profile installer. The current Release build has shrinking disabled and is unsigned; final performance acceptance requires the signed internal/closed build on physical low/mid/high devices, Play-delivered size, cold/warm startup and memory/jank review before production.
