# Android Staging / Production Flavor Contract

This document is the source of truth for Android environment selection. A build type (`debug` or `release`) controls debuggability and signing; a product flavor (`staging` or `production`) controls the backend environment. They are separate decisions.

## Non-negotiable mapping

| Flavor | Application ID | Launcher name | API host | Supabase project | App Links |
|---|---|---|---|---|---|
| `staging` | `com.afterlifearchive.medmanager.staging` | `お薬見守り Staging` | `staging-api.okusuri-mimamori.com` | `kairaeahklftpjfcaddh` | no domain verification |
| `production` | `com.afterlifearchive.medmanager` | `お薬見守り` | `www.okusuri-mimamori.com` | `gsnasheyrncfbbnbomh` | production domain verification enabled |

The two application IDs allow both apps to be installed at once and keep sessions, preferences, notification channels and local databases isolated. Never publish a `staging` variant. Never use a generic `assembleDebug` or `bundleRelease` command for a handoff because aggregate tasks build more than one environment.

## Commands

Use these explicit variants from `android/`:

```bash
./gradlew :app:verifyStagingRuntime :app:assembleStagingDebug
ANDROID_SERIAL=<emulator-serial> ./gradlew :app:installStagingDebug
./gradlew :app:testStagingDebugUnitTest :app:lintStagingDebug
```

Production release gates use only `productionRelease`:

```bash
./gradlew :app:verifyProductionRuntime
./gradlew :app:testProductionReleaseUnitTest :app:assembleProductionRelease
./gradlew :app:bundleProductionRelease
```

`bundleSignedRelease` remains the complete Play handoff entry point and internally consumes `productionRelease` only.

## Runtime input names

Staging uses the following preferred names. The former generic names are accepted only as a temporary local/CI compatibility fallback for Staging; they are never read by Production.

```properties
STAGING_API_BASE_URL=https://staging-api.okusuri-mimamori.com/
STAGING_SUPABASE_URL=https://kairaeahklftpjfcaddh.supabase.co
STAGING_SUPABASE_ANON_KEY=...
STAGING_FIREBASE_APP_ID=...
STAGING_FIREBASE_API_KEY=...
STAGING_FIREBASE_PROJECT_ID=...
STAGING_FIREBASE_SENDER_ID=...
STAGING_PUSH_DEVICE_ENVIRONMENT=PROD
STAGING_EMAIL_CONFIRMATION_REDIRECT_URL=https://www.okusuri-mimamori.com/auth/confirmed
STAGING_BILLING_ENABLED=false
```

`STAGING_PUSH_DEVICE_ENVIRONMENT=PROD` is intentionally retained for compatibility with the currently deployed Staging notification API contract. It does not change the API or database selection.

Production accepts only production-prefixed runtime values:

```properties
PRODUCTION_API_BASE_URL=https://www.okusuri-mimamori.com/
PRODUCTION_SUPABASE_URL=https://gsnasheyrncfbbnbomh.supabase.co
PRODUCTION_SUPABASE_ANON_KEY=...
PRODUCTION_FIREBASE_APP_ID=...
PRODUCTION_FIREBASE_API_KEY=...
PRODUCTION_FIREBASE_PROJECT_ID=...
PRODUCTION_FIREBASE_SENDER_ID=...
PRODUCTION_PUSH_DEVICE_ENVIRONMENT=PROD
PRODUCTION_EMAIL_CONFIRMATION_REDIRECT_URL=https://www.okusuri-mimamori.com/auth/confirmed
PRODUCTION_BILLING_ENABLED=false
```

Do not commit populated values, `google-services.json`, tokens or keys. `verifyStagingRuntime` requires the exact Staging API and Supabase hosts and a client-safe Supabase key. `verifyProductionRuntime` independently requires the exact Production hosts, production-prefixed Firebase identity, a client-safe Production Supabase key, the approved confirmation route and billing disabled.

## Firebase boundary

Gradle supports separate Firebase identities for both flavors. Staging currently falls back to the existing generic Firebase inputs so the deployed test notification path continues to work. Before Play release, register/verify the `com.afterlifearchive.medmanager.staging` Firebase Android app separately and move CI/local inputs to `STAGING_FIREBASE_*`; Production must use the dedicated `PRODUCTION_FIREBASE_*` identity. Do not copy a Production service-account credential into either client build.

## Device migration

The pre-flavor test APK used the Production application ID while connected to Staging. Treat it as a legacy local installation only. Install `stagingDebug`, confirm the intended test login and notifications, then remove the legacy package from test devices. Do not uninstall it before the test session has been restored, and never install a Production build merely to replace it during Staging QA.
