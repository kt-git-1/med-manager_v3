// ---------------------------------------------------------------------------
// Slot Bulk Record Service
//
// Orchestrates bulk recording of all pending/missed doses in a single slot.
// Uses transactional upserts, then fires side effects (events, push, inventory).
// ---------------------------------------------------------------------------

import { prisma } from "../repositories/prisma";
import { getScheduleWithStatus, getDayRange } from "./scheduleService";
import {
  resolveSlot,
  buildSlotSummary,
  type HistorySlot,
  type SlotSummaryStatus
} from "./scheduleResponse";
import { createDoseRecordEvent } from "../repositories/doseRecordEventRepo";
import { getPatientRecordById } from "../repositories/patientRepo";
import { getMedicationRecordForPatient } from "../repositories/medicationRepo";
import { notifyCaregiversOfDoseRecord } from "./pushNotificationService";
import { applyInventoryDeltaForDoseRecord } from "./medicationService";
import { DEFAULT_TIMEZONE, DOSE_MISSED_WINDOW_MS, INTL_PARSE_LOCALE } from "../constants";
import { randomUUID } from "crypto";

/** Patient can record from 30 min before slot time to 60 min after. */
const RECORDING_WINDOW_BEFORE_MS = 30 * 60 * 1000;
const RECORDING_WINDOW_AFTER_MS = 60 * 60 * 1000;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type SlotBulkRecordInput = {
  patientId: string;
  date: string;
  slot: HistorySlot;
  customSlotTimes?: Partial<Record<HistorySlot, string>>;
};

export type SlotBulkRecordResult = {
  updatedCount: number;
  remainingCount: number;
  totalPills: number;
  medCount: number;
  slotTime: string;
  slotSummary: Record<HistorySlot, SlotSummaryStatus>;
  recordingGroupId: string | null;
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function getLocalTimeString(isoDate: string, tz: string): string {
  const date = new Date(isoDate);
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

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export async function bulkRecordSlot(
  input: SlotBulkRecordInput
): Promise<SlotBulkRecordResult> {
  const tz = DEFAULT_TIMEZONE;
  const now = new Date();

  // 1. Parse date to day range
  const dateObj = new Date(`${input.date}T00:00:00`);
  const { from, to } = getDayRange(dateObj, tz);

  // 2. Fetch today's schedule with statuses
  const allDoses = await getScheduleWithStatus(input.patientId, from, to, tz, now);

  // 3. Filter to target slot
  const slotDoses = allDoses.filter((dose) => {
    const doseSlot = resolveSlot(dose.scheduledAt, tz, input.customSlotTimes);
    return doseSlot === input.slot;
  });

  // 4. Compute summary from ALL slot doses (including already taken)
  const totalPills = slotDoses.reduce(
    (sum, dose) => sum + dose.medicationSnapshot.doseCountPerIntake,
    0
  );
  const medCount = slotDoses.length;

  // 5. Derive slot time from first dose's scheduledAt
  const slotTime = slotDoses.length > 0
    ? getLocalTimeString(slotDoses[0].scheduledAt, tz)
    : "00:00";

  // 5b. Check recording window: slotTime −30 min … slotTime +60 min
  if (slotDoses.length > 0) {
    const firstScheduledAt = new Date(slotDoses[0].scheduledAt).getTime();
    const windowOpen = firstScheduledAt - RECORDING_WINDOW_BEFORE_MS;
    const windowClose = firstScheduledAt + RECORDING_WINDOW_AFTER_MS;
    if (now.getTime() < windowOpen || now.getTime() > windowClose) {
      const slotSummary = buildSlotSummary(allDoses, tz, input.customSlotTimes);
      return {
        updatedCount: 0,
        remainingCount: slotDoses.filter(
          (d) => d.effectiveStatus === "pending" || d.effectiveStatus === "missed"
        ).length,
        totalPills,
        medCount,
        slotTime,
        slotSummary,
        recordingGroupId: null
      };
    }
  }

  // 6. Partition into recordable (pending or missed) and already-taken
  const recordable = slotDoses.filter(
    (dose) => dose.effectiveStatus === "pending" || dose.effectiveStatus === "missed"
  );

  // 7. If nothing to record, return summary with zero updates
  if (recordable.length === 0) {
    const slotSummary = buildSlotSummary(allDoses, tz, input.customSlotTimes);
    return {
      updatedCount: 0,
      remainingCount: 0,
      totalPills,
      medCount,
      slotTime,
      slotSummary,
      recordingGroupId: null
    };
  }

  // 8. Generate recording group ID
  const recordingGroupId = randomUUID();

  // 9. Transactional bulk upsert
  const takenAt = now;
  const records = await prisma.$transaction(
    recordable.map((dose) =>
      prisma.doseRecord.upsert({
        where: {
          patientId_medicationId_scheduledAt: {
            patientId: input.patientId,
            medicationId: dose.medicationId,
            scheduledAt: new Date(dose.scheduledAt)
          }
        },
        create: {
          patientId: input.patientId,
          medicationId: dose.medicationId,
          scheduledAt: new Date(dose.scheduledAt),
          takenAt,
          recordedByType: "PATIENT",
          recordedById: null,
          recordingGroupId
        },
        update: {}
      })
    )
  );

  // 10. Side effects (outside the transaction)
  const patient = await getPatientRecordById(input.patientId);
  if (patient) {
    for (const record of records) {
      const withinTime =
        record.takenAt.getTime() <= record.scheduledAt.getTime() + DOSE_MISSED_WINDOW_MS;

      const medication = await getMedicationRecordForPatient(
        record.patientId,
        record.medicationId
      );

      await createDoseRecordEvent({
        patientId: record.patientId,
        scheduledAt: record.scheduledAt,
        takenAt: record.takenAt,
        withinTime,
        displayName: patient.displayName,
        medicationName: medication?.name,
        isPrn: false
      });

      // Fire-and-forget push notification
      void notifyCaregiversOfDoseRecord({
        patientId: record.patientId,
        displayName: patient.displayName,
        medicationName: medication?.name,
        isPrn: false,
        takenAt: record.takenAt
      });

      // Inventory delta
      if (medication) {
        await applyInventoryDeltaForDoseRecord({
          patientId: record.patientId,
          medicationId: record.medicationId,
          delta: -medication.doseCountPerIntake,
          reason: "TAKEN_CREATE"
        });
      }
    }
  }

  // 11. Compute remaining count: total recordable minus what we just recorded
  //     (We recorded all recordable doses, so remaining is always 0 after a successful bulk)
  const remainingCount = 0;

  // 12. Build updated slot summary — mark recorded doses as "taken"
  const recordedMedIds = new Set(recordable.map((d) => d.medicationId));
  const updatedDoses = allDoses.map((dose) => {
    if (recordedMedIds.has(dose.medicationId)) {
      const doseSlot = resolveSlot(dose.scheduledAt, tz, input.customSlotTimes);
      if (doseSlot === input.slot && dose.effectiveStatus !== "taken") {
        return { ...dose, effectiveStatus: "taken" as const };
      }
    }
    return dose;
  });
  const slotSummary = buildSlotSummary(updatedDoses, tz, input.customSlotTimes);

  // 13. Return result
  return {
    updatedCount: records.length,
    remainingCount,
    totalPills,
    medCount,
    slotTime,
    slotSummary,
    recordingGroupId
  };
}
