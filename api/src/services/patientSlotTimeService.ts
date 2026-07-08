import { AuthError } from "../middleware/auth";
import { DEFAULT_SLOT_TIMES } from "../constants";
import { getPatientRecordById } from "../repositories/patientRepo";
import { prisma } from "../repositories/prisma";
import type { HistorySlot } from "./scheduleResponse";

export type PatientSlotTimes = Record<HistorySlot, string>;
export type PatientSlotTimeOverrides = Partial<Record<HistorySlot, string>>;
export type PatientSlotTimeTimelineEntry = {
  effectiveFrom: Date;
  slotTimes: PatientSlotTimes;
};

const timePattern = /^([01]\d|2[0-3]):[0-5]\d$/;

export function patientSlotTimesFromRecord(record: {
  morningTime?: string | null;
  noonTime?: string | null;
  eveningTime?: string | null;
  bedtimeTime?: string | null;
}): PatientSlotTimes {
  return {
    morning: record.morningTime ?? DEFAULT_SLOT_TIMES.morning,
    noon: record.noonTime ?? DEFAULT_SLOT_TIMES.noon,
    evening: record.eveningTime ?? DEFAULT_SLOT_TIMES.evening,
    bedtime: record.bedtimeTime ?? DEFAULT_SLOT_TIMES.bedtime
  };
}

export async function getPatientSlotTimes(patientId: string): Promise<PatientSlotTimes> {
  const patient = await getPatientRecordById(patientId);
  if (!patient) {
    throw new AuthError("Not found", 404);
  }
  return patientSlotTimesFromRecord(patient);
}

export async function resolvePatientSlotTimes(
  patientId: string,
  overrides?: PatientSlotTimeOverrides
): Promise<PatientSlotTimes> {
  if (overrides) {
    return { ...DEFAULT_SLOT_TIMES, ...overrides };
  }
  const stored = await getPatientSlotTimes(patientId);
  return stored;
}

export async function getPatientSlotTimeTimeline(
  patientId: string,
  from: Date,
  to: Date
): Promise<PatientSlotTimeTimelineEntry[]> {
  const patient = await getPatientRecordById(patientId);
  if (!patient) {
    throw new AuthError("Not found", 404);
  }

  const baseline = [
    {
      effectiveFrom: patient.createdAt,
      slotTimes: patientSlotTimesFromRecord(patient)
    }
  ];
  const revisionDelegate = prisma.patientSlotTimeRevision as
    | { findMany?: typeof prisma.patientSlotTimeRevision.findMany }
    | undefined;
  if (!revisionDelegate?.findMany) {
    return baseline;
  }

  const revisions = await revisionDelegate.findMany({
    where: {
      patientId,
      effectiveFrom: { lt: to }
    },
    orderBy: { effectiveFrom: "asc" }
  });
  if (revisions.length === 0) {
    return baseline;
  }

  const entries = revisions.map((revision) => ({
    effectiveFrom: revision.effectiveFrom,
    slotTimes: patientSlotTimesFromRecord(revision)
  }));
  if (entries[0] && entries[0].effectiveFrom > from) {
    entries.unshift(baseline[0]);
  }
  return entries;
}

function slotTimesEqual(left: PatientSlotTimes, right: PatientSlotTimes) {
  return (["morning", "noon", "evening", "bedtime"] as const).every(
    (slot) => left[slot] === right[slot]
  );
}

function legacyTimeSlotMapping(slotTimes: PatientSlotTimes) {
  const mapping = new Map<string, HistorySlot>();
  for (const slot of ["morning", "noon", "evening", "bedtime"] as const) {
    mapping.set(slotTimes[slot], slot);
  }
  return mapping;
}

export function validatePatientSlotTimes(input: unknown): {
  errors: string[];
  slotTimes?: PatientSlotTimes;
} {
  const body = input as Partial<Record<keyof PatientSlotTimes, unknown>> | null;
  const errors: string[] = [];
  if (!body || typeof body !== "object") {
    return { errors: ["slotTimes object is required"] };
  }

  const result: PatientSlotTimes = { ...DEFAULT_SLOT_TIMES };
  for (const slot of ["morning", "noon", "evening", "bedtime"] as const) {
    const value = body[slot];
    if (typeof value !== "string" || !timePattern.test(value)) {
      errors.push(`${slot} must be HH:MM between 00:00 and 23:59`);
    } else {
      result[slot] = value;
    }
  }

  return errors.length ? { errors } : { errors, slotTimes: result };
}

export async function savePatientSlotTimes(
  patientId: string,
  slotTimes: PatientSlotTimes
): Promise<PatientSlotTimes> {
  const patient = await getPatientRecordById(patientId);
  if (!patient) {
    throw new AuthError("Not found", 404);
  }
  const previousSlotTimes = patientSlotTimesFromRecord(patient);
  if (slotTimesEqual(previousSlotTimes, slotTimes)) {
    return previousSlotTimes;
  }

  const now = new Date();
  const legacyMapping = legacyTimeSlotMapping(previousSlotTimes);
  const updated = await prisma.$transaction(async (tx) => {
    const revisionCount = await tx.patientSlotTimeRevision.count({ where: { patientId } });
    if (revisionCount === 0) {
      await tx.patientSlotTimeRevision.create({
        data: {
          patientId,
          effectiveFrom: patient.createdAt,
          morningTime: previousSlotTimes.morning,
          noonTime: previousSlotTimes.noon,
          eveningTime: previousSlotTimes.evening,
          bedtimeTime: previousSlotTimes.bedtime
        }
      });
    }
    await tx.patientSlotTimeRevision.create({
      data: {
        patientId,
        effectiveFrom: now,
        morningTime: slotTimes.morning,
        noonTime: slotTimes.noon,
        eveningTime: slotTimes.evening,
        bedtimeTime: slotTimes.bedtime
      }
    });
    const regimens = await tx.regimen.findMany({
      where: { patientId, enabled: true }
    });
    for (const regimen of regimens) {
      const nextTimes = regimen.times.map((time) => legacyMapping.get(time) ?? time);
      if (nextTimes.some((time, index) => time !== regimen.times[index])) {
        await tx.regimen.update({
          where: { id: regimen.id },
          data: { times: nextTimes }
        });
      }
    }
    return tx.patient.update({
      where: { id: patientId },
      data: {
        morningTime: slotTimes.morning,
        noonTime: slotTimes.noon,
        eveningTime: slotTimes.evening,
        bedtimeTime: slotTimes.bedtime
      }
    });
  });
  return patientSlotTimesFromRecord(updated);
}
