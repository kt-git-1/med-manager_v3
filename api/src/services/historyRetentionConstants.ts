// ---------------------------------------------------------------------------
// History Retention Constants (010-history-retention)
// ---------------------------------------------------------------------------

/**
 * Number of days of history a free-plan user can view.
 * The viewable range is [todayTokyo − (RETENTION_DAYS_FREE − 1), todayTokyo].
 *
 * Adjustable for future plan tiers without code changes.
 */
export const RETENTION_DAYS_FREE = 30;
