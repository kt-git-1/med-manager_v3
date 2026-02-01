# ADR 001: Schedule Generation & Auth Stub

## Context

Record decisions for schedule generation on demand and patient auth stub behavior.

## Decision

- Schedule generation is computed on demand from Medication + Regimen data.
- Patient auth is stubbed behind `PatientSessionVerifier` and uses token value as patientId.

## Alternatives Considered

- Persist scheduled doses in the database.
- Implement full link-code exchange in 001.

## Consequences

- No schedule persistence; clients must request ranges as needed.
- Stub verifier is a temporary interface to be replaced in later features.
