# Phase 4 — Analytics and privacy

## Gate H implementation — 2026-07-15

Android Analytics follows the current iOS `AnalyticsService.swift` contract but does not require a checked-in `google-services.json`. `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID` and `FIREBASE_SENDER_ID` come only from Git-ignored `android/local.properties` or CI environment variables. Missing configuration leaves the app fully runnable and collection inactive.

`firebase_analytics_collection_enabled=false` is authoritative at startup. The shared `AnalyticsService` does not initialize Firebase Analytics until persisted explicit consent is ON. First mode selection presents the exact current iOS Japanese explanation; the same consent is visible and editable in both Patient and Caregiver Settings. Turning it OFF disables collection and calls `resetAnalyticsData()`.

The wrapper exposes fixed enums for iOS event names and parameters. A final schema boundary requires the exact key set and allow-listed value for every event; unknown events, extra keys, IDs, arbitrary text and invalid tutorial steps are dropped. Firebase user ID is always null and ad-personalization signals are disabled. Preview sessions are explicitly suppressed; tests use no Firebase values and verify behavior through an injected fake transport.

Production wiring currently records mode selection, safe screen/tab usage, tutorial progress/completion and safe caregiver core actions. Purchase/paywall/auth/link event methods use the same fixed schema and can be attached only to their corresponding real action outcomes; no render-time mutation event is permitted.

The public privacy policy now states that consent can be changed from both roles. `docs/firebase-analytics.md` contains Android runtime configuration, DebugView commands, Realtime/Events/Explore checks and a Play Data safety input basis.

Automated coverage proves default-off behavior, explicit enable, disable/reset, environment suppression, enum event output and rejection of patient-ID/free-text/unknown/out-of-range payloads. Compose coverage proves first-decision UI and the shared Caregiver Settings toggle. At the Gate H checkpoint the aggregate API suite passed 300/300 and Android API-35 passed 84/84; the later Gate I matrix is authoritative at 96/96 per API level. JVM, Debug/Release and Lint pass after correcting the Firebase reserved ad-personalization property usage.

## Remaining external verification

The current local environment has none of the four Android Firebase values. Therefore DebugView, then Realtime/Events/Explore evidence cannot be honestly marked complete yet. After values are supplied, follow `docs/firebase-analytics.md`, capture the Console evidence without user/patient data, and only then upgrade `XP-004` from `PARTIAL`.
