// ---------------------------------------------------------------------------
// Application-wide constants
// ---------------------------------------------------------------------------

/** Default timezone used for schedule calculations, inventory, and refill plans. */
export const DEFAULT_TIMEZONE = "Asia/Tokyo";

/** Locale used for Intl.DateTimeFormat part-based parsing (produces predictable parts). */
export const INTL_PARSE_LOCALE = "en-US";

// ---------------------------------------------------------------------------
// Dose / Schedule
// ---------------------------------------------------------------------------

/** Duration (ms) after scheduledAt beyond which a dose is considered "missed". */
export const DOSE_MISSED_WINDOW_MS = 60 * 60 * 1000; // 1 hour

/** Number of days to look ahead when fetching the next scheduled dose. */
export const SCHEDULE_LOOKAHEAD_DAYS = 7;

// ---------------------------------------------------------------------------
// Refill plan
// ---------------------------------------------------------------------------

/** Maximum number of days the refill simulation will project forward. */
export const MAX_REFILL_SIMULATION_DAYS = 180;

// ---------------------------------------------------------------------------
// Auth – caregiver token
// ---------------------------------------------------------------------------

/** Prefix prepended to a Supabase JWT to identify caregiver tokens. */
export const CAREGIVER_TOKEN_PREFIX = "caregiver-";

/** Placeholder value used during development / testing. */
export const CAREGIVER_PLACEHOLDER_TOKEN = "caregiver-placeholder";

// ---------------------------------------------------------------------------
// Default slot times
// ---------------------------------------------------------------------------

export const DEFAULT_SLOT_TIMES = {
  morning: "08:00",
  noon: "12:00",
  evening: "19:00",
  bedtime: "22:00",
} as const;

/** Hour ranges used to assign a slot when the exact time doesn't match. */
export const SLOT_HOUR_RANGES = {
  morning: { min: 4, max: 10 },
  noon: { min: 11, max: 15 },
  evening: { min: 16, max: 20 },
  // bedtime covers 21-23 and 0-3 (fallback)
} as const;
