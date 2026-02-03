export type DoseRecordCreateInput = {
  medicationId?: string;
  scheduledAt?: string;
};

export type DoseRecordValidationResult = {
  errors: string[];
  scheduledAt?: Date;
};

function parseScheduledAt(value?: string) {
  if (!value) {
    return undefined;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

export function validateDoseRecordCreate(input: DoseRecordCreateInput): DoseRecordValidationResult {
  const errors: string[] = [];
  if (!input.medicationId) {
    errors.push("medicationId is required");
  }
  if (!input.scheduledAt) {
    errors.push("scheduledAt is required");
  }
  const scheduledAt = parseScheduledAt(input.scheduledAt);
  if (input.scheduledAt && !scheduledAt) {
    errors.push("scheduledAt must be a valid ISO date-time");
  }
  return { errors, scheduledAt };
}

export function validateDoseRecordDelete(input: DoseRecordCreateInput): DoseRecordValidationResult {
  return validateDoseRecordCreate(input);
}
