/**
 * Domain error thrown when the report endpoint receives an invalid date range.
 * Carries a stable `code` ("INVALID_RANGE") for the 400 response contract.
 *
 * Follows the same pattern as HistoryRetentionError (010-history-retention).
 */
export class InvalidRangeError extends Error {
  readonly statusCode = 400;
  readonly code = "INVALID_RANGE" as const;

  constructor(message = "指定された期間が不正です。") {
    super(message);
    this.name = "InvalidRangeError";
  }
}
