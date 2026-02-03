export type MedicationRecord = {
  id: string;
  patientId: string;
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
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
};

export type MedicationSnapshot = {
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
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

function truncateToMinutes(date: Date, tz: string) {
  const parts = getZonedParts(date, tz);
  return makeUtcFromZonedParts(
    { year: parts.year, month: parts.month, day: parts.day, hour: parts.hour, minute: parts.minute },
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
  const label = new Intl.DateTimeFormat("en-US", { timeZone: tz, weekday: "short" }).format(date);
  return weekdayMap[label];
}

function parseTime(time: string) {
  const [hour, minute] = time.split(":").map(Number);
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

function buildMedicationSnapshot(medication: MedicationRecord): MedicationSnapshot {
  return {
    name: medication.name,
    dosageText: medication.dosageText,
    doseCountPerIntake: medication.doseCountPerIntake,
    dosageStrengthValue: medication.dosageStrengthValue,
    dosageStrengthUnit: medication.dosageStrengthUnit
  };
}

export function generateSchedule({
  medications,
  regimens,
  from,
  to
}: {
  medications: MedicationRecord[];
  regimens: RegimenRecord[];
  from: Date;
  to: Date;
}): ScheduleDose[] {
  const doses: ScheduleDose[] = [];
  const medicationMap = new Map(medications.map((medication) => [medication.id, medication]));

  for (const regimen of regimens) {
    const medication = medicationMap.get(regimen.medicationId);
    if (!medication || medication.isArchived || !medication.isActive || !regimen.enabled) {
      continue;
    }

    const tz = regimen.timezone;
    if (!tz || tz.trim().length === 0) {
      throw new Error("Missing timezone");
    }
    const normalizedFrom = truncateToMinutes(from, tz);
    const normalizedTo = truncateToMinutes(to, tz);
    const regimenStart = startOfLocalDay(regimen.startDate, tz);
    const regimenEnd = regimen.endDate ? startOfLocalDay(regimen.endDate, tz) : null;
    const window = intersectWindow(normalizedFrom, normalizedTo, regimenStart, regimenEnd);
    if (!window) {
      continue;
    }

    const daysOfWeek = regimen.daysOfWeek ?? [];
    const times = [...regimen.times].sort();
    let cursor = startOfLocalDay(window.start, tz);

    while (cursor < window.end) {
      const weekday = getWeekday(cursor, tz);
      const matchesDay = daysOfWeek.length === 0 || daysOfWeek.includes(weekday);
      if (matchesDay) {
        for (const time of times) {
          const { hour, minute } = parseTime(time);
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

  return doses.sort(
    (left, right) => Date.parse(left.scheduledAt) - Date.parse(right.scheduledAt)
  );
}

export async function generateScheduleForPatient({
  patientId,
  from,
  to
}: {
  patientId: string;
  from: Date;
  to: Date;
}) {
  const { prisma } = await import("../repositories/prisma");
  const [medications, regimens] = await Promise.all([
    prisma.medication.findMany({ where: { patientId } }),
    prisma.regimen.findMany({ where: { patientId } })
  ]);
  return generateSchedule({ medications, regimens, from, to });
}

function doseKey(input: { patientId: string; medicationId: string; scheduledAt: string }) {
  return `${input.patientId}:${input.medicationId}:${input.scheduledAt}`;
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
  const missedAfter = scheduledTime + 60 * 60 * 1000;
  return now.getTime() > missedAfter ? "missed" : "pending";
}

export function applyDoseStatuses(
  doses: ScheduleDose[],
  doseRecords: { patientId: string; medicationId: string; scheduledAt: Date }[],
  now: Date = new Date()
): ScheduleDoseWithStatus[] {
  const takenKeys = new Set(
    doseRecords.map((record) =>
      doseKey({
        patientId: record.patientId,
        medicationId: record.medicationId,
        scheduledAt: record.scheduledAt.toISOString()
      })
    )
  );
  return doses.map((dose) => {
    const hasTaken = takenKeys.has(doseKey(dose));
    return {
      ...dose,
      effectiveStatus: deriveDoseStatus({ scheduledAt: dose.scheduledAt, hasTaken, now })
    };
  });
}

export async function generateScheduleForPatientWithStatus({
  patientId,
  from,
  to,
  now = new Date()
}: {
  patientId: string;
  from: Date;
  to: Date;
  now?: Date;
}) {
  const { listDoseRecordsByPatientRange } = await import("../repositories/doseRecordRepo");
  const [doses, records] = await Promise.all([
    generateScheduleForPatient({ patientId, from, to }),
    listDoseRecordsByPatientRange({ patientId, from, to })
  ]);
  return applyDoseStatuses(doses, records, now);
}
