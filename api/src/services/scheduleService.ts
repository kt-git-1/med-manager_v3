export type MedicationRecord = {
  id: string;
  patientId: string;
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
  notes?: string | null;
  isActive: boolean;
  isArchived: boolean;
  startDate: Date;
  endDate?: Date | null;
};

export type RegimenRecord = {
  id: string;
  patientId: string;
  medicationId: string;
  timezone: string;
  startDate: Date;
  endDate?: Date | null;
  times: string[];
  daysOfWeek?: string[];
  enabled: boolean;
  createdAt?: Date;
};

export type MedicationSnapshot = {
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
  notes?: string | null;
};

export type ScheduleDose = {
  patientId: string;
  medicationId: string;
  scheduledAt: string;
  medicationSnapshot: MedicationSnapshot;
};

export type DoseStatus = "pending" | "taken" | "missed";

export type ScheduleDoseWithStatus = ScheduleDose & {
  effectiveStatus: DoseStatus;
  recordedByType?: "patient" | "caregiver";
  takenAt?: string;
};

import {
  DEFAULT_TIMEZONE,
  INTL_PARSE_LOCALE,
  DOSE_MISSED_WINDOW_MS,
  DEFAULT_SLOT_TIMES
} from "../constants";
import { resolveSlot } from "./scheduleResponse";

const DEFAULT_REGIMEN_TZ = DEFAULT_TIMEZONE;
type RegimenSlot = keyof typeof DEFAULT_SLOT_TIMES;
type SlotTimeOverrides = Partial<Record<RegimenSlot, string>>;
export type SlotTimeTimelineEntry = {
  effectiveFrom: Date;
  slotTimes: SlotTimeOverrides;
};
type DoseRecordMatch = {
  recordedByType: string;
  takenAt?: Date;
  scheduledAt: Date;
};

function normalizeRegimenTimeZone(timezone: string | null | undefined) {
  if (!timezone) {
    return DEFAULT_REGIMEN_TZ;
  }
  const trimmed = timezone.trim();
  if (!trimmed) {
    return DEFAULT_REGIMEN_TZ;
  }
  if (trimmed === "UTC" || trimmed === "Etc/UTC") {
    return DEFAULT_REGIMEN_TZ;
  }
  return trimmed;
}

const weekdayMap: Record<string, string> = {
  Sun: "SUN",
  Mon: "MON",
  Tue: "TUE",
  Wed: "WED",
  Thu: "THU",
  Fri: "FRI",
  Sat: "SAT"
};

function getZonedParts(date: Date, timeZone: string) {
  const formatter = new Intl.DateTimeFormat(INTL_PARSE_LOCALE, {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  });
  const parts = formatter.formatToParts(date);
  const values: Record<string, string> = {};
  for (const part of parts) {
    if (part.type !== "literal") {
      values[part.type] = part.value;
    }
  }
  return {
    year: Number(values.year),
    month: Number(values.month),
    day: Number(values.day),
    hour: Number(values.hour),
    minute: Number(values.minute),
    second: Number(values.second)
  };
}

function getTimeZoneOffset(date: Date, tz: string) {
  const parts = getZonedParts(date, tz);
  const asUtc = Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    parts.hour,
    parts.minute,
    parts.second
  );
  return asUtc - date.getTime();
}

function makeUtcFromZonedParts(
  parts: { year: number; month: number; day: number; hour: number; minute: number },
  tz: string
) {
  const assumedUtc = Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    parts.hour,
    parts.minute,
    0,
    0
  );
  const offset = getTimeZoneOffset(new Date(assumedUtc), tz);
  return new Date(assumedUtc - offset);
}

function truncateToMinutes(date: Date, tz: string) {
  const parts = getZonedParts(date, tz);
  return makeUtcFromZonedParts(
    {
      year: parts.year,
      month: parts.month,
      day: parts.day,
      hour: parts.hour,
      minute: parts.minute
    },
    tz
  );
}

function startOfLocalDay(date: Date, tz: string) {
  const parts = getZonedParts(date, tz);
  return makeUtcFromZonedParts(
    { year: parts.year, month: parts.month, day: parts.day, hour: 0, minute: 0 },
    tz
  );
}

function nextLocalDay(date: Date, tz: string) {
  const parts = getZonedParts(date, tz);
  return makeUtcFromZonedParts(
    { year: parts.year, month: parts.month, day: parts.day + 1, hour: 0, minute: 0 },
    tz
  );
}

function getWeekday(date: Date, tz: string) {
  const label = new Intl.DateTimeFormat(INTL_PARSE_LOCALE, {
    timeZone: tz,
    weekday: "short"
  }).format(date);
  return weekdayMap[label];
}

function isRegimenSlot(value: string): value is RegimenSlot {
  return Object.prototype.hasOwnProperty.call(DEFAULT_SLOT_TIMES, value);
}

function resolveRegimenTime(time: string, slotTimes?: SlotTimeOverrides) {
  if (isRegimenSlot(time)) {
    return slotTimes?.[time] ?? DEFAULT_SLOT_TIMES[time];
  }
  return time;
}

function slotTimesForDate(
  date: Date,
  timeline?: SlotTimeTimelineEntry[]
): SlotTimeOverrides | undefined {
  if (!timeline?.length) {
    return undefined;
  }
  let selected: SlotTimeTimelineEntry | undefined;
  for (const entry of timeline) {
    if (entry.effectiveFrom <= date) {
      selected = entry;
    } else {
      break;
    }
  }
  return selected?.slotTimes ?? timeline[0]?.slotTimes;
}

export function resolveSlotTimesForDate(
  date: Date,
  fallbackSlotTimes?: SlotTimeOverrides,
  timeline?: SlotTimeTimelineEntry[]
): SlotTimeOverrides | undefined {
  return slotTimesForDate(date, timeline) ?? fallbackSlotTimes;
}

function parseTime(time: string, slotTimes?: SlotTimeOverrides) {
  const resolved = resolveRegimenTime(time, slotTimes);
  const [hour, minute] = resolved.split(":").map(Number);
  return { hour, minute };
}

function intersectWindow(
  from: Date,
  to: Date,
  start: Date,
  end?: Date | null
): { start: Date; end: Date } | null {
  const windowStart = from > start ? from : start;
  const windowEnd = end && end < to ? end : to;
  if (windowEnd <= windowStart) {
    return null;
  }
  return { start: windowStart, end: windowEnd };
}

function laterDate(left: Date, right: Date) {
  return left > right ? left : right;
}

function getRegimenWindowStart(regimen: RegimenRecord, tz: string) {
  const startDateFloor = startOfLocalDay(regimen.startDate, tz);
  if (!regimen.createdAt) {
    return startDateFloor;
  }
  return laterDate(startDateFloor, truncateToMinutes(regimen.createdAt, tz));
}

function buildMedicationSnapshot(medication: MedicationRecord): MedicationSnapshot {
  return {
    name: medication.name,
    dosageText: medication.dosageText,
    doseCountPerIntake: medication.doseCountPerIntake,
    dosageStrengthValue: medication.dosageStrengthValue,
    dosageStrengthUnit: medication.dosageStrengthUnit,
    notes: medication.notes ?? null
  };
}

export function generateSchedule({
  medications,
  regimens,
  from,
  to,
  slotTimes,
  slotTimeTimeline
}: {
  medications: MedicationRecord[];
  regimens: RegimenRecord[];
  from: Date;
  to: Date;
  slotTimes?: SlotTimeOverrides;
  slotTimeTimeline?: SlotTimeTimelineEntry[];
}): ScheduleDose[] {
  const doses: ScheduleDose[] = [];
  const medicationMap = new Map(medications.map((medication) => [medication.id, medication]));

  for (const regimen of regimens) {
    const medication = medicationMap.get(regimen.medicationId);
    if (!medication || medication.isArchived || !medication.isActive || !regimen.enabled) {
      continue;
    }

    const tz = normalizeRegimenTimeZone(regimen.timezone);
    const normalizedFrom = truncateToMinutes(from, tz);
    const normalizedTo = truncateToMinutes(to, tz);
    const regimenStart = getRegimenWindowStart(regimen, tz);
    const regimenEnd = regimen.endDate ? startOfLocalDay(regimen.endDate, tz) : null;
    const window = intersectWindow(normalizedFrom, normalizedTo, regimenStart, regimenEnd);
    if (!window) {
      continue;
    }

    const daysOfWeek = regimen.daysOfWeek ?? [];
    let cursor = startOfLocalDay(window.start, tz);

    while (cursor < window.end) {
      const weekday = getWeekday(cursor, tz);
      const matchesDay = daysOfWeek.length === 0 || daysOfWeek.includes(weekday);
      if (matchesDay) {
        const dayEnd = new Date(nextLocalDay(cursor, tz).getTime() - 1);
        const effectiveSlotTimes = resolveSlotTimesForDate(dayEnd, slotTimes, slotTimeTimeline);
        const times = [...regimen.times].sort((left, right) =>
          resolveRegimenTime(left, effectiveSlotTimes).localeCompare(
            resolveRegimenTime(right, effectiveSlotTimes)
          )
        );
        for (const time of times) {
          const { hour, minute } = parseTime(time, effectiveSlotTimes);
          const cursorParts = getZonedParts(cursor, tz);
          const scheduledAtDate = makeUtcFromZonedParts(
            {
              year: cursorParts.year,
              month: cursorParts.month,
              day: cursorParts.day,
              hour,
              minute
            },
            tz
          );
          if (scheduledAtDate >= window.start && scheduledAtDate < window.end) {
            doses.push({
              patientId: regimen.patientId,
              medicationId: regimen.medicationId,
              scheduledAt: scheduledAtDate.toISOString(),
              medicationSnapshot: buildMedicationSnapshot(medication)
            });
          }
        }
      }
      cursor = nextLocalDay(cursor, tz);
    }
  }

  return doses.sort((left, right) => Date.parse(left.scheduledAt) - Date.parse(right.scheduledAt));
}

export async function generateScheduleForPatient({
  patientId,
  from,
  to,
  slotTimes,
  slotTimeTimeline
}: {
  patientId: string;
  from: Date;
  to: Date;
  slotTimes?: SlotTimeOverrides;
  slotTimeTimeline?: SlotTimeTimelineEntry[];
}) {
  const { prisma } = await import("../repositories/prisma");
  const [medications, regimens] = await Promise.all([
    prisma.medication.findMany({ where: { patientId, isPrn: false } }),
    prisma.regimen.findMany({ where: { patientId } })
  ]);
  return generateSchedule({ medications, regimens, from, to, slotTimes, slotTimeTimeline });
}

function doseKey(input: { patientId: string; medicationId: string; scheduledAt: string }) {
  return `${input.patientId}:${input.medicationId}:${input.scheduledAt}`;
}

function localSlotDoseKey(input: {
  patientId: string;
  medicationId: string;
  scheduledAt: string | Date;
  timeZone: string;
  slotTimes?: SlotTimeOverrides;
  slotTimeTimeline?: SlotTimeTimelineEntry[];
}) {
  const scheduledAt =
    typeof input.scheduledAt === "string" ? input.scheduledAt : input.scheduledAt.toISOString();
  const effectiveSlotTimes = resolveSlotTimesForDate(
    new Date(scheduledAt),
    input.slotTimes,
    input.slotTimeTimeline
  );
  const slot = resolveSlot(scheduledAt, input.timeZone, effectiveSlotTimes);
  if (!slot) {
    return null;
  }
  const dateKey = getLocalDateKey(new Date(scheduledAt), input.timeZone);
  return `${input.patientId}:${input.medicationId}:${dateKey}:${slot}`;
}

function deriveDoseStatus({
  scheduledAt,
  hasTaken,
  now
}: {
  scheduledAt: string;
  hasTaken: boolean;
  now: Date;
}): DoseStatus {
  if (hasTaken) {
    return "taken";
  }
  const scheduledTime = new Date(scheduledAt).getTime();
  const missedAfter = scheduledTime + DOSE_MISSED_WINDOW_MS;
  return now.getTime() > missedAfter ? "missed" : "pending";
}

export function applyDoseStatuses(
  doses: ScheduleDose[],
  doseRecords: {
    patientId: string;
    medicationId: string;
    scheduledAt: Date;
    takenAt?: Date;
    recordedByType: string;
  }[],
  now: Date = new Date(),
  options: {
    timeZone?: string;
    slotTimes?: SlotTimeOverrides;
    slotTimeTimeline?: SlotTimeTimelineEntry[];
  } = {}
): ScheduleDoseWithStatus[] {
  const timeZone = options.timeZone ?? DEFAULT_TIMEZONE;
  const recordMap = new Map<string, DoseRecordMatch>(
    doseRecords.map((record) => [
      doseKey({
        patientId: record.patientId,
        medicationId: record.medicationId,
        scheduledAt: record.scheduledAt.toISOString()
      }),
      {
        recordedByType: record.recordedByType.toLowerCase(),
        takenAt: record.takenAt,
        scheduledAt: record.scheduledAt
      }
    ])
  );
  const localSlotRecordMap = new Map<string, DoseRecordMatch[]>();
  for (const record of doseRecords) {
    const key = localSlotDoseKey({
      patientId: record.patientId,
      medicationId: record.medicationId,
      scheduledAt: record.scheduledAt,
      timeZone,
      slotTimes: options.slotTimes,
      slotTimeTimeline: options.slotTimeTimeline
    });
    if (!key) {
      continue;
    }
    const existing = localSlotRecordMap.get(key) ?? [];
    existing.push({
      recordedByType: record.recordedByType.toLowerCase(),
      takenAt: record.takenAt,
      scheduledAt: record.scheduledAt
    });
    localSlotRecordMap.set(key, existing);
  }
  const consumedRecordKeys = new Set<string>();

  return doses.map((dose) => {
    const key = doseKey(dose);
    let record = recordMap.get(key);
    if (record) {
      consumedRecordKeys.add(key);
    }
    if (!record) {
      const localSlotKey = localSlotDoseKey({
        patientId: dose.patientId,
        medicationId: dose.medicationId,
        scheduledAt: dose.scheduledAt,
        timeZone,
        slotTimes: options.slotTimes,
        slotTimeTimeline: options.slotTimeTimeline
      });
      if (localSlotKey) {
        const candidates = localSlotRecordMap.get(localSlotKey) ?? [];
        const fallbackRecord = candidates.find((candidate) => {
          const candidateKey = doseKey({
            patientId: dose.patientId,
            medicationId: dose.medicationId,
            scheduledAt: candidate.scheduledAt.toISOString()
          });
          return !consumedRecordKeys.has(candidateKey);
        });
        if (fallbackRecord) {
          record = fallbackRecord;
          consumedRecordKeys.add(
            doseKey({
              patientId: dose.patientId,
              medicationId: dose.medicationId,
              scheduledAt: fallbackRecord.scheduledAt.toISOString()
            })
          );
        }
      }
    }
    const hasTaken = !!record;
    return {
      ...dose,
      effectiveStatus: deriveDoseStatus({ scheduledAt: dose.scheduledAt, hasTaken, now }),
      recordedByType: hasTaken ? (record!.recordedByType as "patient" | "caregiver") : undefined,
      takenAt: record?.takenAt ? record.takenAt.toISOString() : undefined
    };
  });
}

export async function generateScheduleForPatientWithStatus({
  patientId,
  from,
  to,
  now = new Date(),
  slotTimes,
  slotTimeTimeline,
  timeZone = DEFAULT_TIMEZONE
}: {
  patientId: string;
  from: Date;
  to: Date;
  now?: Date;
  slotTimes?: SlotTimeOverrides;
  slotTimeTimeline?: SlotTimeTimelineEntry[];
  timeZone?: string;
}) {
  const { listDoseRecordsByPatientRange } = await import("../repositories/doseRecordRepo");
  const [doses, records] = await Promise.all([
    generateScheduleForPatient({ patientId, from, to, slotTimes, slotTimeTimeline }),
    listDoseRecordsByPatientRange({ patientId, from, to })
  ]);
  return applyDoseStatuses(doses, records, now, { timeZone, slotTimes, slotTimeTimeline });
}

export function getLocalDateKey(date: Date, tz: string) {
  const parts = getZonedParts(date, tz);
  return `${String(parts.year).padStart(4, "0")}-${String(parts.month).padStart(2, "0")}-${String(
    parts.day
  ).padStart(2, "0")}`;
}

export function getMonthRange(year: number, month: number, tz: string) {
  const from = makeUtcFromZonedParts({ year, month, day: 1, hour: 0, minute: 0 }, tz);
  const nextMonth = month === 12 ? { year: year + 1, month: 1 } : { year, month: month + 1 };
  const to = makeUtcFromZonedParts(
    { year: nextMonth.year, month: nextMonth.month, day: 1, hour: 0, minute: 0 },
    tz
  );
  return { from, to };
}

export function getDayRange(date: Date, tz: string) {
  const from = startOfLocalDay(date, tz);
  const to = nextLocalDay(from, tz);
  return { from, to };
}

export function normalizeRangeToTimeZone(from: Date, to: Date, tz: string) {
  return {
    from: truncateToMinutes(from, tz),
    to: truncateToMinutes(to, tz)
  };
}

export async function getScheduleWithStatus(
  patientId: string,
  from: Date,
  to: Date,
  tz: string,
  now: Date = new Date(),
  slotTimes?: SlotTimeOverrides,
  slotTimeTimeline?: SlotTimeTimelineEntry[]
) {
  const normalized = normalizeRangeToTimeZone(from, to, tz);
  return generateScheduleForPatientWithStatus({
    patientId,
    from: normalized.from,
    to: normalized.to,
    now,
    slotTimes,
    slotTimeTimeline,
    timeZone: tz
  });
}
