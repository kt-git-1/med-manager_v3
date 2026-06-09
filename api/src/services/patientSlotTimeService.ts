import { AuthError } from "../middleware/auth";
import { DEFAULT_SLOT_TIMES } from "../constants";
import { getPatientRecordById, updatePatientSlotTimes } from "../repositories/patientRepo";
import type { HistorySlot } from "./scheduleResponse";

export type PatientSlotTimes = Record<HistorySlot, string>;
export type PatientSlotTimeOverrides = Partial<Record<HistorySlot, string>>;

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
  const updated = await updatePatientSlotTimes({
    patientId,
    morningTime: slotTimes.morning,
    noonTime: slotTimes.noon,
    eveningTime: slotTimes.evening,
    bedtimeTime: slotTimes.bedtime
  });
  return patientSlotTimesFromRecord(updated);
}
