import { validateDateRange } from "./schedule";

export type MedicationInput = {
  name: string;
  startDate: Date;
  endDate?: Date;
};

export function validateMedication(input: MedicationInput) {
  const errors: string[] = [];
  if (!input.name) {
    errors.push("name is required");
  }
  errors.push(...validateDateRange(input.startDate, input.endDate));
  return errors;
}
