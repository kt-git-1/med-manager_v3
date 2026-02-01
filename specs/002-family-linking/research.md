# Research: Family Linking (002)

## Decision: patientSessionToken validity and rotation

- **Decision**: patientSessionToken is valid until unlink; refresh rotates token and invalidates the old token immediately.
- **Rationale**: Matches spec requirement to avoid re-login burden while keeping short-lived token rotation security.
- **Alternatives considered**:
  - Fixed expiration without refresh (breaks "auto refresh until revoked").
  - Refresh with grace period (increases risk of concurrent token reuse).

## Decision: Linking code format and handling

- **Decision**: 6-digit numeric code, trim whitespace, hash-only storage, expires in 15 minutes, one-time use.
- **Rationale**: Simple patient input, protects secrecy, aligns with MVP constraints.
- **Alternatives considered**:
  - Longer alphanumeric codes (higher UX friction).
  - Plaintext storage (security risk).

## Decision: Lockout and rate limit scope

- **Decision**: Attempt count and lockout tracked per patientId; 5 attempts → 5-minute lockout.
- **Rationale**: Limits brute force while allowing caregiver to retry for same patient.
- **Alternatives considered**:
  - Global or IP-based throttling only (less targeted, more UX risk).

## Decision: Re-issue linking code behavior

- **Decision**: Reissue invalidates existing code immediately; attempt counter is not reset.
- **Rationale**: Prevents multiple valid codes, avoids brute force reset.
- **Alternatives considered**:
  - Parallel codes (risk of leakage).
  - Reissue only after expiry (slower recovery for caregiver).

## Decision: API error concealment

- **Decision**: Use 404 to conceal unauthorized access to patientId-scoped resources per 000-domain-policy.
- **Rationale**: Avoids information leakage across caregivers/patients.
- **Alternatives considered**:
  - 403 for unauthorized access (reveals existence).
