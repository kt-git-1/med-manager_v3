# Android Current Gap Audit

**Audit point:** after initial Phase 2 scaffold

**Purpose:** reset status conservatively before parity-focused development

## Honest current status

| Area | Previous wording | Correct status |
|---|---|---|
| Phase 0 build foundation | Complete | COMPLETE |
| Phase 1 session/API core | Complete | PARTIAL |
| Phase 2 patient mode | Complete | PARTIAL / scaffold |
| High-fidelity iOS UI parity | Implied | NOT ACHIEVED |
| Physical-device verification | Pending | NOT STARTED |

## Keep

- Native Kotlin/Compose project and Gradle wrapper
- Application identity and SDK configuration
- Production API/Supabase runtime configuration mechanism
- Initial Supabase login implementation
- Android Keystore AES-GCM storage foundation
- Patient link exchange foundation
- Initial typed patient dose/history models
- Build, unit-test, lint, APK, and emulator workflow

These are foundations, not proof of feature parity.

## Rework before adding more feature breadth

1. Replace raw `JSONObject` parsing at feature boundaries with contract-tested typed parsing.
2. Implement patient session refresh and centralized 401 invalidation/retry behavior.
3. Extract complete light/dark design tokens from iOS into a shared Android theme.
4. Replace hour-based slot grouping with `/api/patient/slot-times` and the same slot rules as iOS.
5. Split the large `PatientHomeScreen.kt` into shared components and screen/state holders.
6. Replace the simplified history list with the iOS-equivalent month calendar and day detail.
7. Replace the one-off ten-minute alarm treatment with the documented notification preference/scheduling model; retain a postpone action only if it matches current iOS behavior.
8. Add deterministic fixtures that render production components for comparison.
9. Add contract tests for date/time zone, error codes, inventory shortage, partial success, and retention lock.

## Do not start yet

- Broad caregiver UI implementation
- Billing
- Store listing/release work

These wait until shared core and patient parity patterns pass their gates. This avoids repeating weak abstractions across the larger caregiver surface.

## Next execution order

1. Phase 1 hardening: typed networking, refresh/invalidation, error contracts.
2. Phase 2A: tokens, components, slot-time contract, deterministic fixtures.
3. Phase 2B: Today parity including bulk/partial inventory/PRN/details.
4. Phase 2C: history calendar/day detail/settings.
5. Phase 2D: notifications/deep links/tutorial/accessibility.
6. Only then begin Phase 3 caregiver parity.
