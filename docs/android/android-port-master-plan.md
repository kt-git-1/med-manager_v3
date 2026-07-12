# Android Port Master Development Plan

**Status:** Active

**Product:** お薬見守り

**Android stack:** Kotlin, Jetpack Compose, Material 3

**Reference client:** Current SwiftUI iOS app
**Backend:** Existing Next.js API, Supabase Auth/Postgres, Firebase Cloud Messaging

## 1. Objective

Build a native Android app that reproduces the current iOS product's user-visible behavior, business rules, information architecture, Japanese copy, accessibility intent, and visual identity. Android platform conventions may differ only where the operating system requires or strongly expects a different interaction.

The goal is not a screen-shaped approximation. The goal is behavioral and visual parity backed by traceable requirements and repeatable verification.

## 2. Non-negotiable principles

1. **The iOS app is inspected before the Android equivalent is designed.** File names, screenshots, live states, DTOs, tests, and API calls must be traced.
2. **The backend remains the source of truth.** Authorization, inventory, entitlement, retention, linking, and dose-recording rules are never recreated as Android-only policy.
3. **Contracts are modeled before UI code.** A feature begins with endpoint, request, response, error, and session behavior.
4. **All states are first-class requirements.** Loading, empty, content, stale, retry, offline, unauthorized, forbidden, validation, conflict, inventory shortage, partial success, and retention lock are considered where applicable.
5. **A phase is not complete because its happy path renders.** It is complete only when every requirement in the parity matrix reaches `VERIFIED`.
6. **No speculative simplification.** Any deliberate Android difference is recorded in the parity matrix with a reason and approval status.
7. **No new product behavior during porting.** Product changes must be separated from parity work and confirmed before implementation.
8. **Fixtures and previews use the same UI components as production.** Screenshot-only duplicate layouts are prohibited.
9. **Visual comparison is continuous.** Each screen is compared before moving to the next screen group.
10. **Documentation changes with code.** A pull request or work unit that changes behavior must update its requirement status and evidence.

## 3. Architecture boundaries

### Reused

- Next.js API routes and server business rules
- Supabase Auth and Postgres
- Firebase server push transport
- Existing Japanese terminology, privacy/support URLs, and product role model
- Existing API schemas expressed by iOS DTOs and backend validators

### Android-native

- Compose UI and navigation
- State holders/repositories
- Android Keystore session encryption
- Runtime permission flows
- AlarmManager/WorkManager notification scheduling as appropriate
- FCM client token lifecycle and notification tap routing
- Android PDF download/share behavior
- Google Play metadata, signing, testing, and release process

### Dependency direction

`Compose screen -> state holder -> repository -> API/data source -> backend`

Compose code must not construct URLs, parse JSON, decide authorization, or directly mutate stored tokens. Repositories must expose typed states rather than raw `JSONObject` values.

## 4. Development sequence for every vertical slice

Every feature is implemented in this order. Skipping a step requires a recorded blocker.

1. **Reference capture**
   - Identify iOS screen/view model/DTO/test files.
   - Capture iOS screenshots for required states.
   - Record navigation entry and exit behavior.
2. **Contract definition**
   - Record endpoints, methods, request fields, response fields, date/time zone behavior, and error codes.
   - Add typed Android models and parser/contract tests.
3. **State definition**
   - Enumerate loading, empty, content, error, confirmation, success, disabled, and exceptional states.
   - Define state transitions and idempotency expectations.
4. **Logic implementation**
   - Implement repository/data source behavior.
   - Add unit tests for success and meaningful failure paths.
5. **UI implementation**
   - Build production Compose components using shared tokens.
   - Add deterministic fixtures that render those exact components.
6. **Behavior verification**
   - Exercise navigation and actions on an emulator.
   - Verify actual API calls when safe test data is available.
7. **Visual verification**
   - Capture iOS and Android at matched content, locale, text size, theme, and viewport class.
   - Compare side by side and with an overlay/diff.
8. **Physical-device verification**
   - Verify keyboard, back gesture, notifications, lifecycle restoration, and touch targets.
9. **Documentation gate**
   - Update the parity matrix status and link evidence.

## 5. Revised phases

### Phase 0 — Build foundation

Gradle, package identity, SDK range, Compose theme foundation, repeatable debug build.

### Phase 1 — Shared platform core

Configuration, typed networking, Supabase authentication, encrypted sessions, token refresh, common errors, navigation shell, test fixtures.

### Phase 2A — Patient contracts and shared components

Typed patient endpoints, slot-time rules, dose/history models, patient design tokens, reusable header/card/status/tab components, deterministic fixtures.

### Phase 2B — Patient Today parity

Today schedule, actual slot grouping, next-action behavior, individual record, slot bulk record, inventory shortage, partial success, PRN, medication detail, empty/error/updating states, refresh behavior.

### Phase 2C — Patient History and Settings parity

Month calendar, legend, day detail, history retention behavior, notification preferences, primary/secondary reminders, session revoke, legal/support routes.

### Phase 2D — Patient notification and tutorial parity

Permission education, schedule refresh, foreground behavior, deep links, highlight/scroll, real-screen tutorial, accessibility and large-text verification.

### Phase 3A — Caregiver authentication and patient management parity

Auth choice, login, signup, email confirmation/resend, patient list/create/delete/revoke, linking-code issue/share, selected-patient restoration.

### Phase 3B — Caregiver medication and schedule parity

Medication list, add/edit/stop, regular/PRN forms, regimen scheduling, validation and empty/error states.

### Phase 3C — Caregiver Today, inventory, history, settings parity

Today monitoring and proxy record, inventory list/detail/adjustment, calendar/day history, PDF report, push settings, account deletion.

### Phase 4 — Cross-platform integrations

FCM registration/unregistration, push tap routing, analytics parity, PDF sharing, universal/app links, process-death restoration.

### Phase 5 — Release verification

Full regression, accessibility, performance, supported-device matrix, Data safety/health declarations, closed test, production signing and rollout.

## 6. Phase exit gate

A phase may be marked complete only when:

- Every scoped parity-matrix item is `VERIFIED`.
- Unit/contract tests pass for debug and release variants.
- Android lint passes without newly accepted warnings.
- A release-like build installs and launches.
- Required iOS/Android comparison captures exist.
- No unresolved P0/P1 defect remains.
- Deferred items are outside the phase by master-plan definition, not deferred ad hoc.
- Documentation identifies the exact verification evidence.

## 7. Change control

When the iOS app or backend changes during the port:

1. Mark affected matrix rows `RECHECK_REQUIRED`.
2. Update contract fixtures and screenshots.
3. Re-run the vertical-slice gates.
4. Do not silently preserve old Android behavior.

When Android intentionally differs:

1. Record the difference.
2. State whether it is OS-required, accessibility-driven, or product-approved.
3. Add Android-specific acceptance criteria.
4. Keep terminology, data, and business outcome equivalent.

## 8. Current checkpoint

The repository has a functioning foundation and partial patient-mode paths. Phase 0 is complete. Phase 1 and Phase 2 are not yet parity-complete under this plan. See `current-gap-audit.md` for the reset status.
