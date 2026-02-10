// ---------------------------------------------------------------------------
// History Retention Service (010-history-retention)
//
// Centralised retention check logic for all 4 history endpoints.
// Computes cutoff date, resolves premium status for both caregiver and
// patient sessions, and throws HistoryRetentionError for blocked requests.
// All queries are read-only — no transaction needed.
// ---------------------------------------------------------------------------

import { prisma } from "../repositories/prisma";
import { RETENTION_DAYS_FREE } from "./historyRetentionConstants";
import { HistoryRetentionError } from "../errors/historyRetentionError";

const HISTORY_TZ = "Asia/Tokyo";

// ---------------------------------------------------------------------------
// Date helpers
// ---------------------------------------------------------------------------

/** Returns the current date in YYYY-MM-DD format using Asia/Tokyo timezone. */
export function getTodayTokyo(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: HISTORY_TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(new Date());
}

/**
 * Returns the retention cutoff date (todayTokyo − 29 days) as YYYY-MM-DD.
 * Free users may view dates in [cutoffDate, todayTokyo] (inclusive, 30 days).
 */
export function getCutoffDate(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: HISTORY_TZ,
    year: "numeric",
    month: "numeric",
    day: "numeric"
  }).formatToParts(new Date());

  const year = Number(parts.find((p) => p.type === "year")!.value);
  const month = Number(parts.find((p) => p.type === "month")!.value);
  const day = Number(parts.find((p) => p.type === "day")!.value);

  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() - (RETENTION_DAYS_FREE - 1));

  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

// ---------------------------------------------------------------------------
// Premium resolution
// ---------------------------------------------------------------------------

/**
 * Returns `true` if the caregiver has an ACTIVE entitlement (premium).
 * Same pattern as linkingService.ts for the patient limit gate (009).
 */
export async function isPremiumForCaregiver(caregiverId: string): Promise<boolean> {
  const entitlement = await prisma.caregiverEntitlement.findFirst({
    where: { caregiverId, status: "ACTIVE" }
  });
  return entitlement !== null;
}

/**
 * Returns `true` if the patient's linked caregiver has an ACTIVE entitlement.
 * Patient → CaregiverPatientLink (1:1, ACTIVE) → CaregiverEntitlement (ACTIVE).
 */
export async function isPremiumForPatient(patientId: string): Promise<boolean> {
  const link = await prisma.caregiverPatientLink.findFirst({
    where: { patientId, status: "ACTIVE" }
  });
  if (!link) {
    return false;
  }
  return isPremiumForCaregiver(link.caregiverId);
}

// ---------------------------------------------------------------------------
// Retention checks
// ---------------------------------------------------------------------------

/**
 * Checks retention for a day endpoint.
 * If `dateStr < cutoffDate` and the user is not premium, throws
 * HistoryRetentionError with the cutoff date and retention days.
 */
export async function checkRetentionForDay(
  dateStr: string,
  sessionType: "caregiver" | "patient",
  sessionId: string
): Promise<void> {
  const cutoff = getCutoffDate();
  if (dateStr >= cutoff) {
    return; // within allowed range
  }

  const premium =
    sessionType === "caregiver"
      ? await isPremiumForCaregiver(sessionId)
      : await isPremiumForPatient(sessionId);

  if (!premium) {
    throw new HistoryRetentionError(cutoff, RETENTION_DAYS_FREE);
  }
}

/**
 * Checks retention for a month endpoint.
 * Computes the first day of the requested month (YYYY-MM-01).
 * If `firstDayOfMonth < cutoffDate` and the user is not premium, throws
 * HistoryRetentionError (MVP straddling rule: entire month is locked).
 */
export async function checkRetentionForMonth(
  year: number,
  month: number,
  sessionType: "caregiver" | "patient",
  sessionId: string
): Promise<void> {
  const cutoff = getCutoffDate();
  const firstDayOfMonth = `${year}-${String(month).padStart(2, "0")}-01`;

  if (firstDayOfMonth >= cutoff) {
    return; // month starts on or after cutoff — allowed
  }

  const premium =
    sessionType === "caregiver"
      ? await isPremiumForCaregiver(sessionId)
      : await isPremiumForPatient(sessionId);

  if (!premium) {
    throw new HistoryRetentionError(cutoff, RETENTION_DAYS_FREE);
  }
}
