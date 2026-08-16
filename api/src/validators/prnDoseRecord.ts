export type PrnDoseRecordCreateInput = {
  medicationId?: string;
  clientMutationId?: unknown;
  takenAt?: string;
  quantityTaken?: number;
};

export function validatePrnDoseRecordCreate(input: PrnDoseRecordCreateInput) {
  const errors: string[] = [];
  let parsedTakenAt: Date | undefined;
  let clientMutationId: string | undefined;

  if (!input.medicationId) {
    errors.push("medicationId is required");
  }
  if (input.clientMutationId !== undefined) {
    if (
      typeof input.clientMutationId !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        input.clientMutationId
      )
    ) {
      errors.push("clientMutationId must be a UUID v4");
    } else {
      clientMutationId = input.clientMutationId.toLowerCase();
    }
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
    if (!Number.isFinite(input.quantityTaken) || input.quantityTaken <= 0) {
      errors.push("quantityTaken must be a positive number");
    }
  }

  return { errors, takenAt: parsedTakenAt, clientMutationId };
}
