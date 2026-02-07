import { validateDateRange } from "./schedule";

export type MedicationInput = {
  name: string;
  startDate: Date;
  endDate?: Date;
  isPrn?: boolean;
  prnInstructions?: string | null;
};

export function validateMedication(input: MedicationInput) {
  const errors: string[] = [];
  if (!input.name) {
    errors.push("name is required");
  }
  if (input.isPrn !== undefined && typeof input.isPrn !== "boolean") {
    errors.push("isPrn must be a boolean");
  }
  if (input.prnInstructions !== undefined && input.prnInstructions !== null) {
    if (typeof input.prnInstructions !== "string") {
      errors.push("prnInstructions must be a string");
    }
  }
  errors.push(...validateDateRange(input.startDate, input.endDate));
  return errors;
}
