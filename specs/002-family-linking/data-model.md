# Data Model: Family Linking (002)

## Entities

### Patient

- id (UUID)
- caregiverId (string, Supabase user id)
- displayName (string, required, non-blank, max 50)
- createdAt / updatedAt (timestamp)

### CaregiverPatientLink

- id (UUID)
- caregiverId (string, Supabase user id)
- patientId (UUID, unique)
- status (enum: active, revoked)
- revokedAt (timestamp, optional)
- createdAt / updatedAt (timestamp)

### LinkingCode

- id (UUID)
- patientId (UUID)
- codeHash (string, required)
- expiresAt (timestamp, required)
- usedAt (timestamp, optional)
- issuedBy (string, Supabase user id)
- createdAt (timestamp)

### PatientSession

- id (UUID)
- patientId (UUID)
- tokenHash (string, required, unique)
- issuedAt (timestamp)
- expiresAt (timestamp, optional)
- lastRotatedAt (timestamp, optional)
- revokedAt (timestamp, optional)

## Relationships

- Caregiver 1..n Patient
- Patient 1..1 CaregiverPatientLink (unique patientId)
- Patient 1..n LinkingCode
- Patient 1..n PatientSession (current active is the latest non-revoked)

## Validation Rules

- Patient.displayName is required and must not be blank.
- CaregiverPatientLink.patientId is unique (1 patient = 1 caregiver).
- LinkingCode is valid only if now < expiresAt, usedAt is null, and not revoked by reissue.
- LinkingCode is one-time; on exchange, usedAt is set and code is invalid thereafter.
- PatientSession is valid if revokedAt is null and tokenHash matches.
- Refresh rotates tokenHash; previous token becomes invalid immediately.

## Indexes (proposed)

- CaregiverPatientLink: unique(patientId), index(caregiverId)
- LinkingCode: index(codeHash), index(patientId, expiresAt)
- PatientSession: unique(tokenHash), index(patientId), index(revokedAt)

## State Transitions

- LinkingCode: issued → used (usedAt set) OR issued → expired (time-based) OR issued → invalidated (reissue)
- PatientSession: active → rotated (tokenHash replaced) OR active → revoked (revokedAt set)
- CaregiverPatientLink: active → revoked
