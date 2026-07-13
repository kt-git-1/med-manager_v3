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
import { createDoseRecordEvents } from "../repositories/doseRecordEventRepo";
import { getPatientRecordById } from "../repositories/patientRepo";
import { listMedicationRecordsForPatientByIds } from "../repositories/medicationRepo";
import { notifyCaregiversOfDoseTaken } from "./pushNotificationService";
import { applyInventoryDeltasForDoseRecords } from "./medicationService";
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
  recordedByType?: "PATIENT" | "CAREGIVER";
  recordedById?: string | null;
};

export type SlotBulkRecordResult = {
  updatedCount: number;
  remainingCount: number;
  insufficientCount: number;
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

function scheduleDoseKey(dose: { patientId: string; medicationId: string; scheduledAt: string }) {
  return `${dose.patientId}:${dose.medicationId}:${dose.scheduledAt}`;
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export async function bulkRecordSlot(input: SlotBulkRecordInput): Promise<SlotBulkRecordResult> {
  const tz = DEFAULT_TIMEZONE;
  const now = new Date();
  const recordedByType = input.recordedByType ?? "PATIENT";
  const recordedById = input.recordedById ?? null;

  // 1. Parse date to day range
  const dateObj = new Date(`${input.date}T00:00:00`);
  const { from, to } = getDayRange(dateObj, tz);

  // 2. Fetch today's schedule with statuses
  const allDoses = await getScheduleWithStatus(
    input.patientId,
    from,
    to,
    tz,
    now,
    input.customSlotTimes
  );

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
  const slotTime =
    slotDoses.length > 0 ? getLocalTimeString(slotDoses[0].scheduledAt, tz) : "00:00";

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
        insufficientCount: 0,
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
      insufficientCount: 0,
      totalPills,
      medCount,
      slotTime,
      slotSummary,
      recordingGroupId: null
    };
  }

  const medications = await listMedicationRecordsForPatientByIds(input.patientId, [
    ...new Set(recordable.map((dose) => dose.medicationId))
  ]);
  const medicationById = new Map(medications.map((medication) => [medication.id, medication]));
  const availableQuantityByMedicationId = new Map<string, number>();
  const recordableWithInventory: typeof recordable = [];
  const insufficientDoses: typeof recordable = [];
  for (const dose of recordable) {
    const medication = medicationById.get(dose.medicationId);
    if (!medication || !medication.inventoryEnabled) {
      recordableWithInventory.push(dose);
      continue;
    }

    const available =
      availableQuantityByMedicationId.get(dose.medicationId) ?? medication.inventoryQuantity;
    if (available < medication.doseCountPerIntake) {
      insufficientDoses.push(dose);
      continue;
    }

    availableQuantityByMedicationId.set(
      dose.medicationId,
      available - medication.doseCountPerIntake
    );
    recordableWithInventory.push(dose);
  }

  if (recordableWithInventory.length === 0) {
    const slotSummary = buildSlotSummary(allDoses, tz, input.customSlotTimes);
    return {
      updatedCount: 0,
      remainingCount: insufficientDoses.length,
      insufficientCount: insufficientDoses.length,
      totalPills,
      medCount,
      slotTime,
      slotSummary,
      recordingGroupId: null
    };
  }

  // 8. Generate recording group ID
  const recordingGroupId = randomUUID();

  // 9. Insert all records in one database round trip. The unique constraint keeps
  // concurrent/retried requests idempotent without an upsert per medication.
  const takenAt = now;
  const records = await prisma.doseRecord.createManyAndReturn({
    data: recordableWithInventory.map((dose) => ({
      patientId: input.patientId,
      medicationId: dose.medicationId,
      scheduledAt: new Date(dose.scheduledAt),
      takenAt,
      recordedByType,
      recordedById,
      recordingGroupId
    })),
    skipDuplicates: true
  });

  // 10. Side effects (outside the transaction)
  const patient = await getPatientRecordById(input.patientId);
  let anyWithinTime = false;
  if (patient) {
    const eventInputs = records.map((record) => {
      const withinTime =
        record.takenAt.getTime() <= record.scheduledAt.getTime() + DOSE_MISSED_WINDOW_MS;

      if (withinTime) anyWithinTime = true;

      const medication = medicationById.get(record.medicationId);
      return {
        patientId: record.patientId,
        scheduledAt: record.scheduledAt,
        takenAt: record.takenAt,
        withinTime,
        displayName: patient.displayName,
        medicationName: medication?.name,
        isPrn: false
      };
    });
    await createDoseRecordEvents(eventInputs);

    await applyInventoryDeltasForDoseRecords({
      patientId: input.patientId,
      patientDisplayName: patient.displayName,
      deltas: records.flatMap((record) => {
        const medication = medicationById.get(record.medicationId);
        if (!medication?.inventoryEnabled) return [];
        return [{ medicationId: medication.id, quantity: medication.doseCountPerIntake }];
      })
    });

    if (records.length > 0) {
      // Await notification work so serverless runtimes do not stop before FCM send completes.
      await notifyCaregiversOfDoseTaken({
        patientId: input.patientId,
        displayName: patient.displayName,
        date: input.date,
        slot: input.slot,
        recordingGroupId,
        excludeCaregiverId:
          recordedByType === "CAREGIVER" ? (recordedById ?? undefined) : undefined,
        withinTime: anyWithinTime,
        isPrn: false
      });
    }
  }

  // 11. Compute remaining count: insufficient doses stay pending/missed after partial bulk recording.
  const remainingCount = insufficientDoses.length;

  // 12. Build updated slot summary — mark recorded doses as "taken"
  const recordedKeys = new Set(recordableWithInventory.map(scheduleDoseKey));
  const updatedDoses = allDoses.map((dose) => {
    if (recordedKeys.has(scheduleDoseKey(dose))) {
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
    insufficientCount: insufficientDoses.length,
    totalPills,
    medCount,
    slotTime,
    slotSummary,
    recordingGroupId
  };
}
