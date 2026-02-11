import type { HistorySlot } from "../services/scheduleResponse";

export type SlotBulkRecordInput = {
  date?: string;
  slot?: string;
};

export type SlotBulkRecordValidationResult = {
  errors: string[];
  date?: string;
  slot?: HistorySlot;
};

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const VALID_SLOTS: HistorySlot[] = ["morning", "noon", "evening", "bedtime"];

export function validateSlotBulkRecordRequest(
  input: SlotBulkRecordInput
): SlotBulkRecordValidationResult {
  const errors: string[] = [];

  if (!input.date) {
    errors.push("date is required");
  } else if (!DATE_PATTERN.test(input.date)) {
    errors.push("date must be in YYYY-MM-DD format");
  }

  if (!input.slot) {
    errors.push("slot is required");
  } else if (!VALID_SLOTS.includes(input.slot as HistorySlot)) {
    errors.push("slot must be one of: morning, noon, evening, bedtime");
  }

  if (errors.length) {
    return { errors };
  }

  return {
    errors: [],
    date: input.date!,
    slot: input.slot as HistorySlot
  };
}
