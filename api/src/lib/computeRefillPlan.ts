const TOKYO_TIMEZONE = "Asia/Tokyo";
const MAX_SIMULATION_DAYS = 180;

export type RefillPlanRegimen = {
  startDate: Date;
  endDate?: Date | null;
  times: string[];
  daysOfWeek?: string[] | null;
  enabled: boolean;
};

export type RefillPlanInput = {
  inventoryEnabled: boolean;
  inventoryQuantity: number;
  doseCountPerIntake: number;
  regimens: RefillPlanRegimen[];
};

export type RefillPlanResult = {
  dailyPlannedUnits: number | null;
  daysRemaining: number | null;
  refillDueDate: string | null;
};

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
  const formatter = new Intl.DateTimeFormat("en-US", {
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
  const asUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
  return asUtc - date.getTime();
}

function makeUtcFromZonedParts(
  parts: { year: number; month: number; day: number; hour: number; minute: number },
  tz: string
) {
  const assumedUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, 0, 0);
  const offset = getTimeZoneOffset(new Date(assumedUtc), tz);
  return new Date(assumedUtc - offset);
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
  const label = new Intl.DateTimeFormat("en-US", { timeZone: tz, weekday: "short" }).format(date);
  return weekdayMap[label];
}

function dateKey(date: Date, tz: string) {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });
  const parts = formatter.formatToParts(date);
  const values: Record<string, string> = {};
  for (const part of parts) {
    if (part.type !== "literal") {
      values[part.type] = part.value;
    }
  }
  return `${values.year}-${values.month}-${values.day}`;
}

function isRegimenActiveOnDate(regimen: RefillPlanRegimen, date: Date, tz: string) {
  if (!regimen.enabled || !regimen.times || regimen.times.length === 0) {
    return false;
  }
  const targetKey = dateKey(date, tz);
  const startKey = dateKey(regimen.startDate, tz);
  if (targetKey < startKey) {
    return false;
  }
  if (regimen.endDate) {
    const endKey = dateKey(regimen.endDate, tz);
    if (targetKey >= endKey) {
      return false;
    }
  }
  const daysOfWeek = regimen.daysOfWeek ?? [];
  const weekday = getWeekday(date, tz);
  return daysOfWeek.length === 0 || daysOfWeek.includes(weekday);
}

function plannedUnitsForDate({
  regimens,
  date,
  doseCountPerIntake,
  tz
}: {
  regimens: RefillPlanRegimen[];
  date: Date;
  doseCountPerIntake: number;
  tz: string;
}) {
  let total = 0;
  for (const regimen of regimens) {
    if (isRegimenActiveOnDate(regimen, date, tz)) {
      total += regimen.times.length * doseCountPerIntake;
    }
  }
  return total;
}

export function computeRefillPlan(input: RefillPlanInput): RefillPlanResult {
  if (!input.inventoryEnabled) {
    return { dailyPlannedUnits: null, daysRemaining: null, refillDueDate: null };
  }

  const activeRegimens = input.regimens.filter((regimen) => regimen.enabled && regimen.times.length > 0);
  if (activeRegimens.length === 0) {
    return { dailyPlannedUnits: null, daysRemaining: null, refillDueDate: null };
  }

  const today = startOfLocalDay(new Date(), TOKYO_TIMEZONE);
  const dailyPlannedUnits = plannedUnitsForDate({
    regimens: activeRegimens,
    date: today,
    doseCountPerIntake: input.doseCountPerIntake,
    tz: TOKYO_TIMEZONE
  });

  if (input.inventoryQuantity === 0) {
    return {
      dailyPlannedUnits,
      daysRemaining: 0,
      refillDueDate: dateKey(today, TOKYO_TIMEZONE)
    };
  }

  let remaining = input.inventoryQuantity;
  let cursor = today;
  let hasPlannedUnits = false;
  for (let dayOffset = 0; dayOffset < MAX_SIMULATION_DAYS; dayOffset += 1) {
    const plannedUnits = plannedUnitsForDate({
      regimens: activeRegimens,
      date: cursor,
      doseCountPerIntake: input.doseCountPerIntake,
      tz: TOKYO_TIMEZONE
    });
    if (plannedUnits > 0) {
      hasPlannedUnits = true;
      if (remaining < plannedUnits) {
        return {
          dailyPlannedUnits,
          daysRemaining: dayOffset,
          refillDueDate: dateKey(cursor, TOKYO_TIMEZONE)
        };
      }
      remaining -= plannedUnits;
    }
    cursor = nextLocalDay(cursor, TOKYO_TIMEZONE);
  }

  if (!hasPlannedUnits) {
    return { dailyPlannedUnits: null, daysRemaining: null, refillDueDate: null };
  }

  return { dailyPlannedUnits, daysRemaining: null, refillDueDate: null };
}
