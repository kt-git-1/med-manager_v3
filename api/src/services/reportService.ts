// ---------------------------------------------------------------------------
// Report Service (011-pdf-export)
//
// Aggregates scheduled dose + PRN data for a date range, assembling the
// HistoryReportResponse shape consumed by the iOS client for on-device PDF
// generation. Reuses existing schedule/PRN query infrastructure. All date
// computations use Asia/Tokyo timezone. Read-only — no transaction needed.
// ---------------------------------------------------------------------------

import { getScheduleWithStatus, getLocalDateKey } from "./scheduleService";
import { groupDosesByLocalDate, resolveSlot } from "./scheduleResponse";
import { prisma } from "../repositories/prisma";

const HISTORY_TZ = "Asia/Tokyo";

// ---------------------------------------------------------------------------
// Response types (match contracts/openapi.yaml)
// ---------------------------------------------------------------------------

type ReportSlotItem = {
  medicationId: string;
  name: string;
  dosageText: string;
  doseCount: number;
  status: "TAKEN" | "MISSED" | "PENDING";
  recordedAt: string | null;
};

type ReportPrnItem = {
  medicationId: string;
  name: string;
  dosageText: string;
  quantity: number;
  recordedAt: string;
  recordedBy: "PATIENT" | "CAREGIVER";
};

type ReportSlots = {
  morning: ReportSlotItem[];
  noon: ReportSlotItem[];
  evening: ReportSlotItem[];
  bedtime: ReportSlotItem[];
};

type ReportDay = {
  date: string;
  slots: ReportSlots;
  prn: ReportPrnItem[];
};

export type HistoryReportResponse = {
  patient: { id: string; displayName: string };
  range: { from: string; to: string; timezone: string; days: number };
  days: ReportDay[];
};

// ---------------------------------------------------------------------------
// Date helpers
// ---------------------------------------------------------------------------

/**
 * Converts a YYYY-MM-DD string to a Date representing midnight in Asia/Tokyo.
 * Japan does not observe DST, so UTC+9 is always correct.
 */
function tokyoMidnight(dateStr: string): Date {
  return new Date(`${dateStr}T00:00:00+09:00`);
}

// ---------------------------------------------------------------------------
// Main export
// ---------------------------------------------------------------------------

/**
 * Generates a history report for the given patient and date range.
 *
 * @param patientId - Patient UUID
 * @param from - Start date (YYYY-MM-DD, inclusive)
 * @param to - End date (YYYY-MM-DD, inclusive)
 * @param patientDisplayName - Patient display name (from patient record)
 * @returns HistoryReportResponse matching the openapi.yaml contract
 */
export async function generateReport(
  patientId: string,
  from: string,
  to: string,
  patientDisplayName: string
): Promise<HistoryReportResponse> {
  const rangeFrom = tokyoMidnight(from);
  // 'to' is inclusive: exclusive upper bound is midnight of the NEXT day
  const rangeTo = new Date(tokyoMidnight(to));
  rangeTo.setUTCDate(rangeTo.getUTCDate() + 1);

  // Compute inclusive day count from the date strings (avoids DST edge cases)
  const dayCount =
    Math.round((rangeTo.getTime() - rangeFrom.getTime()) / (1000 * 60 * 60 * 24));

  // ----- 1. Scheduled doses -----
  const doses = await getScheduleWithStatus(
    patientId,
    rangeFrom,
    rangeTo,
    HISTORY_TZ
  );
  const grouped = groupDosesByLocalDate(doses, HISTORY_TZ);

  // ----- 2. PRN records (with medication dosageText) -----
  const prnRecords = await prisma.prnDoseRecord.findMany({
    where: {
      patientId,
      takenAt: { gte: rangeFrom, lt: rangeTo }
    },
    include: {
      medication: { select: { name: true, dosageText: true } }
    },
    orderBy: { takenAt: "asc" }
  });

  // Group PRN records by local date key
  const prnByDate = new Map<string, typeof prnRecords>();
  for (const record of prnRecords) {
    const dateKey = getLocalDateKey(record.takenAt, HISTORY_TZ);
    const existing = prnByDate.get(dateKey) ?? [];
    existing.push(record);
    prnByDate.set(dateKey, existing);
  }

  // ----- 3. Build day-by-day report -----
  const days: ReportDay[] = [];
  const cursor = new Date(rangeFrom);

  for (let i = 0; i < dayCount; i++) {
    const dateKey = getLocalDateKey(cursor, HISTORY_TZ);
    const dayDoses = grouped.get(dateKey) ?? [];

    // Build slot arrays
    const slots: ReportSlots = {
      morning: [],
      noon: [],
      evening: [],
      bedtime: []
    };

    for (const dose of dayDoses) {
      const slot = resolveSlot(dose.scheduledAt, HISTORY_TZ);
      if (!slot) continue;

      slots[slot].push({
        medicationId: dose.medicationId,
        name: dose.medicationSnapshot.name,
        dosageText: dose.medicationSnapshot.dosageText,
        doseCount: dose.medicationSnapshot.doseCountPerIntake,
        status: dose.effectiveStatus.toUpperCase() as ReportSlotItem["status"],
        recordedAt:
          dose.effectiveStatus === "taken" ? (dose.takenAt ?? dose.scheduledAt) : null
      });
    }

    // Build PRN array
    const dayPrn = prnByDate.get(dateKey) ?? [];
    const prn: ReportPrnItem[] = dayPrn.map((record) => ({
      medicationId: record.medicationId,
      name: record.medication.name,
      dosageText: record.medication.dosageText,
      quantity: record.quantityTaken,
      recordedAt: record.takenAt.toISOString(),
      recordedBy: record.actorType as ReportPrnItem["recordedBy"]
    }));

    days.push({ date: dateKey, slots, prn });

    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }

  return {
    patient: { id: patientId, displayName: patientDisplayName },
    range: { from, to, timezone: HISTORY_TZ, days: dayCount },
    days
  };
}
