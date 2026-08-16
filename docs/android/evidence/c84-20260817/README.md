# C84 Supabase client-credential safety gate — 2026-08-17

## Finding

`verifyProductionRuntime` previously treated any `SUPABASE_ANON_KEY` value of at least 20 characters as structurally acceptable. That could allow an operator to accidentally compile an elevated `sb_secret_...` key, a legacy `service_role` JWT or an unrelated opaque string into the Android client.

Supabase's official API-key guidance classifies `sb_publishable_...` and the legacy `anon` JWT as client-side keys, while `sb_secret_...` and legacy `service_role` are backend-only elevated credentials: <https://supabase.com/docs/guides/getting-started/api-keys>.

## Correction

The Gradle production-runtime gate now accepts only:

- an `sb_publishable_...` value with a constrained non-empty opaque body; or
- a three-segment Base64URL legacy JWT with an algorithm header, `iss = supabase`, and exactly one `role = anon` claim.

It rejects without printing the value:

- `sb_secret_...`;
- legacy `service_role`;
- a legacy-looking JWT with another issuer;
- an unrelated long opaque string;
- a malformed JWT.

`verifyRuntimeCredentialSafety` uses generated synthetic strings/JWTs only. It is a dependency of `verifyProductionRuntime` and a dedicated Android CI step.

## Local evidence

- Synthetic current publishable key: accepted
- Synthetic legacy `anon` JWT: accepted
- Synthetic secret/service-role/wrong-issuer/random/malformed values: rejected
- Full synthetic `verifyProductionRuntime`: accepted with no production value
- Full synthetic runtime using `sb_secret_...`: rejected with only the safe failure reason
- Debug JVM: 216/216, 0 failed, 0 skipped
- Release JVM: 213/213, 0 failed, 0 skipped
- Lint: pass
- Release APK compatibility: pass
- Play listing assets: pass
- Tracked diff secret-pattern scan: no credential value found

## Hosted CI

- Android CI run #142 on implementation commit `a788f04`: `PASS`
  - Firebase runtime: success
  - Runtime credential safety: success
  - Connected shard runner contract: success
  - Debug and Release JVM tests: success
  - Debug build: success
  - Lint: success
  - Release APK compatibility: success
  - Play listing assets: success

Run #142 proves the new Runtime credential safety step and all retained downstream gates. This gate prevents a privileged-key packaging error; it does not provide production runtime values, an upload key, a signed AAB or Play Console evidence and therefore does not by itself complete `XP-010`.
