# Phase 4 — Analytics and privacy

## Gate H implementation — updated 2026-08-17

Android Analytics follows the current iOS `AnalyticsService.swift` contract but does not require a checked-in `google-services.json`. `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID` and `FIREBASE_SENDER_ID` come only from Git-ignored `android/local.properties` or CI environment variables. Missing configuration leaves the app fully runnable and collection inactive.

`firebase_analytics_collection_enabled=false` is authoritative at startup. The shared `AnalyticsService` does not initialize Firebase Analytics until persisted explicit consent is ON. First mode selection presents the exact current iOS Japanese explanation; the same consent is visible and editable in both Patient and Caregiver Settings. Turning it OFF disables collection and calls `resetAnalyticsData()`.

The wrapper exposes fixed enums for iOS event names and parameters. A final schema boundary requires the exact key set and allow-listed value for every event; unknown events, extra keys, IDs, arbitrary text and invalid tutorial steps are dropped. Firebase user ID is always null and ad-personalization signals are disabled. Preview sessions are explicitly suppressed; tests use no Firebase values and verify behavior through an injected fake transport.

Production wiring currently records mode selection, safe screen/tab usage, tutorial progress/completion and safe caregiver core actions. Purchase/paywall/auth/link event methods use the same fixed schema and can be attached only to their corresponding real action outcomes; no render-time mutation event is permitted.

The public privacy policy now states that consent can be changed from both roles. `docs/android/firebase-analytics.md` contains Android runtime configuration, consent-off/on/reset controls, DebugView commands, the exact safe event matrix, Realtime/Events/Explore checks and the required H07 evidence format.

Automated coverage proves default-off behavior, explicit enable, disable/reset, environment suppression, enum event output and rejection of patient-ID/free-text/unknown/out-of-range payloads. Compose coverage proves first-decision UI, both decision actions at 200% font scale, opt-out persistence and the shared Caregiver Settings toggle. Historical Gate H/Gate I counts remain evidence for those commits; C75 is the latest complete regression checkpoint at Debug JVM 216/216, Release JVM 213/213 and A302SH instrumentation 278/278 before the C76 runtime-resource-only correction.

## C76 live verification and remaining external evidence

The production-package Android Firebase app is registered and the four values are held only in GitHub Actions secrets or supplied ephemerally for local verification. C76 passed the physical consent-off/on/reset boundary, safe fixed-enum transport, both-role shared state, DebugView parameter inspection and Realtime aggregation without recording identity or health data. The redacted result is `docs/android/evidence/h07-20260817/README.md`.

Processed Events passed in C80. The 2026-08-23 acceptance then verified Android-only fixed-enum rows in a privacy-reviewed Explore and removed the temporary Explore under explicit owner approval. `RG-001` and `XP-004` are verified.
