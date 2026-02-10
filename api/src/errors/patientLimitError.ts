/**
 * Domain error thrown when a free caregiver attempts to create a patient
 * beyond the allowed limit. Carries `limit` and `current` counts for the
 * stable 403 response contract.
 */
export class PatientLimitError extends Error {
  readonly statusCode = 403;
  readonly limit: number;
  readonly current: number;

  constructor(limit: number, current: number) {
    super("Patient limit reached. Upgrade to premium for unlimited patients.");
    this.name = "PatientLimitError";
    this.limit = limit;
    this.current = current;
  }
}
