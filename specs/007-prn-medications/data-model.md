# Data Model: PRN Medications

## Entities

### Medication (PRN Fields)

- **isPrn**: boolean (default false)
- **prnInstructions**: string | null (optional, free text)

### PRNDoseRecord

- **id**: unique identifier
- **patientId**: linked patient
- **medicationId**: linked medication (must be PRN)
- **takenAt**: timestamp (server time default)
- **quantityTaken**: integer (default = medication dose count per intake)
- **actorType**: enum (PATIENT | CAREGIVER)
- **createdAt**: timestamp

## Relationships

- **Medication** belongs to **Patient** (existing).
- **PRNDoseRecord** belongs to **Medication** and **Patient**.

## History Payload Extensions

### Day Detail (HistoryDayResponse)

- **prnItems[]**: array of PRN history entries
  - medicationId
  - medicationName
  - takenAt
  - quantityTaken
  - actorType (PATIENT | CAREGIVER)

### Month Summary (HistoryMonthResponse)

- **prnCountByDay**: optional map `{ "yyyy-MM-dd": number }`

## Validation & Rules

- PRN records can only be created for medications where `isPrn = true`.
- If `isPrn = true`, regimen/schedule is not required and should be omitted.
- If `isPrn = false`, regimen/schedule is required (existing behavior).
- Patient role can create PRN records for linked patient but cannot delete them.
- Caregiver role can create and delete PRN records for linked patient.
- Inventory adjustment on PRN create/delete occurs only when inventory tracking is enabled:
  - Create: `inventoryQuantity = max(0, inventoryQuantity - doseCountPerIntake)`
  - Delete (caregiver only): `inventoryQuantity = inventoryQuantity + doseCountPerIntake`
- PRN records do not generate pending/missed states and are excluded from scheduled dose generation.
