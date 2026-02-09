import { INTL_PARSE_LOCALE, DEFAULT_SLOT_TIMES, SLOT_HOUR_RANGES } from "../constants";

export type ScheduleResponseDose = {
  key: string;
  patientId: string;
  medicationId: string;
  scheduledAt: string;
  effectiveStatus?: "pending" | "taken" | "missed";
  recordedByType?: "patient" | "caregiver";
  medicationSnapshot: {
    name: string;
    dosageText: string;
    doseCountPerIntake: number;
    dosageStrengthValue: number;
    dosageStrengthUnit: string;
    notes?: string | null;
  };
};

export type ScheduleResponseInput = Omit<ScheduleResponseDose, "key"> & { key?: string };

export function buildScheduleResponse(doses: ScheduleResponseInput[]) {
  return {
    data: doses.map((dose) => ({
      ...dose,
      key: dose.key ?? `${dose.patientId}:${dose.medicationId}:${dose.scheduledAt}`
    }))
  };
}

export type HistorySlot = "morning" | "noon" | "evening" | "bedtime";
export type SlotSummaryStatus = "pending" | "taken" | "missed" | "none";

const slotTimes: Record<HistorySlot, string> = { ...DEFAULT_SLOT_TIMES };

function getLocalTimeString(scheduledAt: string, tz: string) {
  const date = new Date(scheduledAt);
  const formatter = new Intl.DateTimeFormat(INTL_PARSE_LOCALE, {
    timeZone: tz,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23"
  });
  const parts = formatter.formatToParts(date);
  const values: Record<string, string> = {};
  for (const part of parts) {
    if (part.type !== "literal") {
      values[part.type] = part.value;
    }
  }
  return `${values.hour}:${values.minute}`;
}

function resolveSlotByRange(hour: number): HistorySlot {
  if (hour >= SLOT_HOUR_RANGES.morning.min && hour <= SLOT_HOUR_RANGES.morning.max) return "morning";
  if (hour >= SLOT_HOUR_RANGES.noon.min && hour <= SLOT_HOUR_RANGES.noon.max) return "noon";
  if (hour >= SLOT_HOUR_RANGES.evening.min && hour <= SLOT_HOUR_RANGES.evening.max) return "evening";
  // 21-23 and 0-3
  return "bedtime";
}

export function resolveSlot(
  scheduledAt: string,
  tz: string,
  customSlotTimes?: Partial<Record<HistorySlot, string>>
): HistorySlot | null {
  const localTime = getLocalTimeString(scheduledAt, tz);
  const effectiveTimes = customSlotTimes ? { ...slotTimes, ...customSlotTimes } : slotTimes;
  const entry = Object.entries(effectiveTimes).find(([, time]) => time === localTime);
  if (entry) {
    return entry[0] as HistorySlot;
  }
  // Fallback: assign slot by time-of-day range.
  const hour = Number(localTime.split(":")[0]);
  if (Number.isNaN(hour)) return null;
  return resolveSlotByRange(hour);
}

export function buildSlotSummary(
  doses: { scheduledAt: string; effectiveStatus?: "pending" | "taken" | "missed" }[],
  tz: string,
  customSlotTimes?: Partial<Record<HistorySlot, string>>
) {
  const summary: Record<HistorySlot, SlotSummaryStatus> = {
    morning: "none",
    noon: "none",
    evening: "none",
    bedtime: "none"
  };
  for (const dose of doses) {
    const slot = resolveSlot(dose.scheduledAt, tz, customSlotTimes);
    if (!slot) {
      continue;
    }
    const status = dose.effectiveStatus ?? "pending";
    const current = summary[slot];
    if (status === "missed") {
      summary[slot] = "missed";
    } else if (status === "pending") {
      if (current !== "missed") {
        summary[slot] = "pending";
      }
    } else if (status === "taken") {
      if (current === "none") {
        summary[slot] = "taken";
      }
    }
  }
  return summary;
}

const timePattern = /^([01]\d|2[0-3]):[0-5]\d$/;

export type SlotTimeParseResult = {
  slotTimes?: Partial<Record<HistorySlot, string>>;
  errors: string[];
};

export function parseSlotTimesFromParams(searchParams: URLSearchParams): SlotTimeParseResult {
  const slots: HistorySlot[] = ["morning", "noon", "evening", "bedtime"];
  const result: Partial<Record<HistorySlot, string>> = {};
  const errors: string[] = [];
  let hasAny = false;
  for (const slot of slots) {
    const value = searchParams.get(`${slot}Time`);
    if (value) {
      if (timePattern.test(value)) {
        result[slot] = value;
        hasAny = true;
      } else {
        errors.push(`${slot}Time must be HH:MM between 00:00 and 23:59`);
      }
    }
  }
  return {
    slotTimes: hasAny ? result : undefined,
    errors
  };
}

export function groupDosesByLocalDate(doses: { scheduledAt: string }[], tz: string) {
  const formatter = new Intl.DateTimeFormat(INTL_PARSE_LOCALE, {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });
  const map = new Map<string, typeof doses>();
  for (const dose of doses) {
    const parts = formatter.formatToParts(new Date(dose.scheduledAt));
    const values: Record<string, string> = {};
    for (const part of parts) {
      if (part.type !== "literal") {
        values[part.type] = part.value;
      }
    }
    const key = `${values.year}-${values.month}-${values.day}`;
    const existing = map.get(key) ?? [];
    existing.push(dose);
    map.set(key, existing);
  }
  return map;
}
