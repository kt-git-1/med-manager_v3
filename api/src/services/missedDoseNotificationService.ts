import { DEFAULT_TIMEZONE, DOSE_MISSED_WINDOW_MS } from "../constants";
import { log } from "../logging/logger";
import { prisma } from "../repositories/prisma";
import {
  generateScheduleForPatientWithStatus,
  getLocalDateKey,
  resolveSlotTimesForDate,
  type ScheduleDoseWithStatus
} from "./scheduleService";
import { getPatientSlotTimeTimeline, patientSlotTimesFromRecord } from "./patientSlotTimeService";
import { notifyCaregiversOfDoseMissed } from "./pushNotificationService";
import { resolveSlot, type HistorySlot } from "./scheduleResponse";

/** The cron runs every five minutes; this overlap protects against small delays. */
export const MISSED_DOSE_SCAN_LOOKBACK_MS = 10 * 60 * 1000;

type MissedSlot = {
  patientId: string;
  displayName: string;
  date: string;
  slot: HistorySlot;
  doses: ScheduleDoseWithStatus[];
};

export type MissedDoseNotificationRunResult = {
  scannedPatients: number;
  dueSlots: number;
  notifiedSlots: number;
  skippedRecordedSlots: number;
};

function missedSlotKey(input: Pick<MissedSlot, "patientId" | "date" | "slot">) {
  return `${input.patientId}:${input.date}:${input.slot}`;
}

async function slotStillHasMissingDose(slot: MissedSlot): Promise<boolean> {
  const recordedCount = await prisma.doseRecord.count({
    where: {
      patientId: slot.patientId,
      OR: slot.doses.map((dose) => ({
        medicationId: dose.medicationId,
        scheduledAt: new Date(dose.scheduledAt)
      }))
    }
  });
  return recordedCount < slot.doses.length;
}

/**
 * Find slots that crossed the one-hour missed threshold during the latest cron
 * window and notify linked caregivers once per patient/date/slot.
 */
export async function sendDueMissedDoseNotifications(
  now: Date = new Date()
): Promise<MissedDoseNotificationRunResult> {
  const cutoff = new Date(now.getTime() - DOSE_MISSED_WINDOW_MS);
  const from = new Date(cutoff.getTime() - MISSED_DOSE_SCAN_LOOKBACK_MS);
  const to = new Date(cutoff.getTime() + 1);

  const regimenPatients = await prisma.regimen.findMany({
    where: {
      enabled: true,
      medication: { isActive: true, isArchived: false, isPrn: false }
    },
    distinct: ["patientId"],
    select: { patientId: true }
  });
  const patientIds = regimenPatients.map((item) => item.patientId);
  if (patientIds.length === 0) {
    return {
      scannedPatients: 0,
      dueSlots: 0,
      notifiedSlots: 0,
      skippedRecordedSlots: 0
    };
  }

  const patients = await prisma.patient.findMany({
    where: { id: { in: patientIds } },
    select: {
      id: true,
      displayName: true,
      morningTime: true,
      noonTime: true,
      eveningTime: true,
      bedtimeTime: true
    }
  });

  const missedSlots = new Map<string, MissedSlot>();
  for (const patient of patients) {
    const slotTimes = patientSlotTimesFromRecord(patient);
    const slotTimeTimeline = await getPatientSlotTimeTimeline(patient.id, from, to);
    const doses = await generateScheduleForPatientWithStatus({
      patientId: patient.id,
      from,
      to,
      now,
      slotTimes,
      slotTimeTimeline,
      timeZone: DEFAULT_TIMEZONE
    });

    for (const dose of doses) {
      if (dose.effectiveStatus !== "missed") continue;

      const scheduledAt = new Date(dose.scheduledAt);
      const effectiveSlotTimes = resolveSlotTimesForDate(scheduledAt, slotTimes, slotTimeTimeline);
      const slot = resolveSlot(dose.scheduledAt, DEFAULT_TIMEZONE, effectiveSlotTimes);
      if (!slot) continue;

      const candidate: MissedSlot = {
        patientId: patient.id,
        displayName: patient.displayName,
        date: getLocalDateKey(scheduledAt, DEFAULT_TIMEZONE),
        slot,
        doses: [dose]
      };
      const key = missedSlotKey(candidate);
      const existing = missedSlots.get(key);
      if (existing) {
        existing.doses.push(dose);
      } else {
        missedSlots.set(key, candidate);
      }
    }
  }

  let notifiedSlots = 0;
  let skippedRecordedSlots = 0;
  for (const slot of missedSlots.values()) {
    // Close the common race where a user records while this cron is scanning.
    if (!(await slotStillHasMissingDose(slot))) {
      skippedRecordedSlots += 1;
      continue;
    }
    await notifyCaregiversOfDoseMissed({
      patientId: slot.patientId,
      displayName: slot.displayName,
      date: slot.date,
      slot: slot.slot
    });
    notifiedSlots += 1;
  }

  const result = {
    scannedPatients: patients.length,
    dueSlots: missedSlots.size,
    notifiedSlots,
    skippedRecordedSlots
  };
  log("info", `Missed dose notification cron result ${JSON.stringify(result)}`);
  return result;
}
