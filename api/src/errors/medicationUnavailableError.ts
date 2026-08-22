export class MedicationUnavailableError extends Error {
  readonly statusCode = 404;
  readonly code = "not_found";

  constructor() {
    super("Medication not found");
    this.name = "MedicationUnavailableError";
  }
}
