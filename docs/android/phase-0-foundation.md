# Android Phase 0: Foundation

**Status: IMPLEMENTED. Rebaseline build passes; release/device verification remains.**

For all later work, follow `android-port-master-plan.md` and `parity-requirements.md`.

## Goal

Create a buildable native Android foundation without changing the existing iOS or API behavior. Android uses Kotlin and Jetpack Compose and reuses the current Next.js API, Supabase Auth/Postgres, and FCM infrastructure.

## Decisions

- Project location: `android/`
- Application ID: `com.afterlifearchive.medmanager`
- UI: Kotlin + Jetpack Compose + Material 3
- Minimum OS: Android 8.0 (API 26)
- Compile SDK: API 36; initial target SDK: API 35
- Java toolchain: 17
- Product roles: `PATIENT` and `CAREGIVER`, matching iOS
- Billing: disabled for the first Android release
- Server-side authorization, inventory checks, limits, and audit behavior remain authoritative
- Secrets must not be committed. Runtime configuration will be injected in a later phase.

## Port boundaries

The following are reusable without a rewrite:

- Next.js API routes and business rules
- Supabase Auth and Postgres data
- FCM server-side delivery
- Legal/support pages and product terminology

The following require Android implementations:

- Compose screens, navigation, and state holders
- Encrypted local session storage
- Supabase sign-in/session refresh client
- Local notifications and FCM token registration
- Deep links and notification routing
- PDF download/share UX
- Google Play policy metadata and, only if enabled later, Play Billing

## Historical phase plan

This original coarse plan is retained as history and is superseded by the revised phases in `android-port-master-plan.md`.

1. **Phase 0 — Foundation:** build system, package identity, minimal Compose UI, architecture and scope.
2. **Phase 1 — Session and API core:** HTTP client, DTOs, secure token storage, mode restoration, error mapping.
3. **Phase 2 — Patient flow:** linking code, today's doses, record/postpone, history and local reminders.
4. **Phase 3 — Caregiver flow:** authentication, patient management, medication/schedule/inventory management, history.
5. **Phase 4 — Platform integration:** FCM, deep links, PDF sharing, accessibility and analytics.
6. **Phase 5 — Release:** automated tests, physical-device matrix, Data safety/health declarations, closed test and production rollout.

## Phase 0 exit criteria

- [x] Android project exists under `android/`.
- [x] App identity and supported SDK range are defined.
- [x] Compose renders the product's initial patient/caregiver choice.
- [x] Product roles are represented in Kotlin.
- [x] Android port boundaries and later phases are documented.
- [x] Debug APK, unit tests, and Android lint pass locally.

Verified with `./gradlew test assembleDebug lint` using Android SDK 36. The generated debug APK is under `android/app/build/outputs/apk/debug/`.
