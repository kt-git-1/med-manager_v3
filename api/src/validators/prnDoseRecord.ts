export type PrnDoseRecordCreateInput = {
  medicationId?: string;
  takenAt?: string;
  quantityTaken?: number;
};

export function validatePrnDoseRecordCreate(input: PrnDoseRecordCreateInput) {
  const errors: string[] = [];
  let parsedTakenAt: Date | undefined;

  if (!input.medicationId) {
    errors.push("medicationId is required");
  }
  if (input.takenAt !== undefined) {
    const parsed = new Date(input.takenAt);
    if (Number.isNaN(parsed.getTime())) {
      errors.push("takenAt must be a valid date-time");
    } else {
      parsedTakenAt = parsed;
    }
  }
  if (input.quantityTaken !== undefined) {
    if (!Number.isInteger(input.quantityTaken) || input.quantityTaken <= 0) {
      errors.push("quantityTaken must be a positive integer");
    }
  }

  return { errors, takenAt: parsedTakenAt };
}
