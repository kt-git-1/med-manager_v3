// ---------------------------------------------------------------------------
// Report Range Validator (011-pdf-export)
//
// Validates `from` and `to` query parameters for the history report endpoint.
// Throws InvalidRangeError on any validation failure.
// Uses getTodayTokyo() from historyRetentionService for consistent timezone.
// ---------------------------------------------------------------------------

import { InvalidRangeError } from "../errors/invalidRangeError";
import { getTodayTokyo } from "../services/historyRetentionService";

/** Maximum number of days (inclusive) allowed in a single report request. */
export const MAX_REPORT_RANGE_DAYS = 90;

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Validates the report date range parameters.
 *
 * Throws `InvalidRangeError` if:
 * - `from` is null/undefined/empty
 * - `to` is null/undefined/empty
 * - `from` or `to` is not a valid YYYY-MM-DD date
 * - `to` is in the future (> todayTokyo)
 * - `from` is after `to`
 * - The inclusive day count exceeds MAX_REPORT_RANGE_DAYS (90)
 */
export function validateReportRange(
  from: string | null | undefined,
  to: string | null | undefined
): void {
  if (!from) {
    throw new InvalidRangeError("開始日(from)は必須です。");
  }
  if (!to) {
    throw new InvalidRangeError("終了日(to)は必須です。");
  }

  if (!DATE_PATTERN.test(from)) {
    throw new InvalidRangeError("開始日(from)はYYYY-MM-DD形式で指定してください。");
  }
  if (!DATE_PATTERN.test(to)) {
    throw new InvalidRangeError("終了日(to)はYYYY-MM-DD形式で指定してください。");
  }

  // Verify the dates parse to valid calendar dates
  const fromDate = new Date(`${from}T00:00:00+09:00`);
  const toDate = new Date(`${to}T00:00:00+09:00`);
  if (Number.isNaN(fromDate.getTime())) {
    throw new InvalidRangeError("開始日(from)が無効な日付です。");
  }
  if (Number.isNaN(toDate.getTime())) {
    throw new InvalidRangeError("終了日(to)が無効な日付です。");
  }

  // to must not be in the future
  const todayTokyo = getTodayTokyo();
  if (to > todayTokyo) {
    throw new InvalidRangeError("終了日は今日以前を指定してください。");
  }

  // from must not be after to
  if (from > to) {
    throw new InvalidRangeError("開始日は終了日以前を指定してください。");
  }

  // Inclusive day count must not exceed MAX_REPORT_RANGE_DAYS
  const dayCount =
    Math.round((toDate.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24)) + 1;
  if (dayCount > MAX_REPORT_RANGE_DAYS) {
    throw new InvalidRangeError(
      `期間は${MAX_REPORT_RANGE_DAYS}日以内で指定してください。`
    );
  }
}
