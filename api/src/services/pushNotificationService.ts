// ---------------------------------------------------------------------------
// Push notification orchestration service
//
// Determines WHO should receive push notifications and sends them.
// - Legacy APNs path: notifyCaregiversOfDoseRecord
// - New FCM path (012): notifyCaregiversOfDoseTaken (with dedup via PushDelivery)
// Called after DoseRecordEvent creation.
// ---------------------------------------------------------------------------

import { listDeviceTokensForCaregivers } from "../repositories/deviceTokenRepo";
import { sendPushNotifications, type ApnsPayload } from "./apnsService";
import {
  listEnabledPushDevicesForCaregivers,
  disablePushDeviceById
} from "../repositories/pushDeviceRepo";
import { tryInsertDelivery } from "../repositories/pushDeliveryRepo";
import {
  sendFcmMessage,
  isFcmConfigured,
  type FcmNotification,
  type FcmDataPayload,
  type FcmApnsOverride
} from "./fcmService";
import { prisma } from "../repositories/prisma";
import { log } from "../logging/logger";

// ---------------------------------------------------------------------------
// Dose record notification
// ---------------------------------------------------------------------------

export interface DoseRecordNotificationInput {
  patientId: string;
  displayName: string;
  medicationName?: string | null;
  isPrn: boolean;
  takenAt: Date;
}

/**
 * Send push notifications to all caregivers linked to the patient
 * when a dose is recorded.
 *
 * This is fire-and-forget: errors are logged but never thrown.
 */
export async function notifyCaregiversOfDoseRecord(
  input: DoseRecordNotificationInput
): Promise<void> {
  try {
    const caregiverIds = await getLinkedCaregiverIds(input.patientId);
    if (caregiverIds.length === 0) return;

    const deviceTokens = await listDeviceTokensForCaregivers(caregiverIds);
    if (deviceTokens.length === 0) return;

    const payload = buildDoseRecordPayload(input);
    const tokens = deviceTokens.map((dt) => dt.token);

    const results = await sendPushNotifications(tokens, payload, {
      collapseId: `dose-${input.patientId}`
    });

    const failed = results.filter((r) => !r.success);
    if (failed.length > 0) {
      log(
        "warn",
        `Push notification: ${results.length - failed.length}/${results.length} sent for patient ${input.patientId}`
      );
    }
  } catch (error) {
    log(
      "error",
      `Push notification error: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function getLinkedCaregiverIds(patientId: string): Promise<string[]> {
  const links = await prisma.caregiverPatientLink.findMany({
    where: { patientId, status: "ACTIVE" },
    select: { caregiverId: true }
  });
  return links.map((l) => l.caregiverId);
}

function buildDoseRecordPayload(input: DoseRecordNotificationInput): ApnsPayload {
  const patientName = input.displayName;

  let body: string;
  if (input.isPrn && input.medicationName) {
    body = `${patientName}さんが${input.medicationName}（頓服）を服用しました`;
  } else if (input.medicationName) {
    body = `${patientName}さんが${input.medicationName}を服用しました`;
  } else {
    body = `${patientName}さんがお薬を服用しました`;
  }

  return {
    aps: {
      alert: {
        title: "服薬記録",
        body
      },
      sound: "default",
      "thread-id": `patient-${input.patientId}`
    },
    type: "dose_record",
    patientId: input.patientId
  };
}

// ---------------------------------------------------------------------------
// FCM-based dose TAKEN notification (012-push-foundation)
// ---------------------------------------------------------------------------

export interface DoseTakenNotificationInput {
  patientId: string;
  displayName: string;
  date: string; // YYYY-MM-DD in Asia/Tokyo
  slot: string; // morning | noon | evening | bedtime
  recordingGroupId?: string; // present for bulk recordings
  doseEventId?: string; // DoseRecordEvent.id for single-dose dedup
  prnDoseRecordId?: string; // PrnDoseRecord.id for PRN dedup
  excludeCaregiverId?: string; // caregiver who recorded on behalf of the patient
  withinTime: boolean;
  isPrn: boolean;
}

export interface DoseMissedNotificationInput {
  patientId: string;
  displayName: string;
  date: string; // YYYY-MM-DD in Asia/Tokyo
  slot: string; // morning | noon | evening | bedtime
}

const slotLabels: Record<string, string> = {
  morning: "朝",
  noon: "昼",
  evening: "夕",
  bedtime: "就寝前"
};

function buildDoseTakenBody(input: DoseTakenNotificationInput): string {
  if (input.isPrn) {
    return `${input.displayName}さんの頓服を記録しました`;
  }

  const slotLabel = slotLabels[input.slot];
  if (input.recordingGroupId && slotLabel) {
    return `${input.displayName}さんの${slotLabel}のお薬を記録しました`;
  }

  return `${input.displayName}さんのお薬を記録しました`;
}

/**
 * Send FCM push notifications to all caregivers linked to the patient
 * when a dose is taken (single, bulk, or PRN).
 *
 * - Deduplicates via PushDelivery table (exactly-once per device per event)
 * - Disables devices on FCM UNREGISTERED response
 * - Fire-and-forget: errors are logged but never thrown
 */
export async function notifyCaregiversOfDoseTaken(
  input: DoseTakenNotificationInput
): Promise<void> {
  try {
    if (!isFcmConfigured()) return;

    const caregiverIds = (await getLinkedCaregiverIds(input.patientId)).filter(
      (caregiverId) => caregiverId !== input.excludeCaregiverId
    );
    if (caregiverIds.length === 0) return;

    const devices = await listEnabledPushDevicesForCaregivers(caregiverIds);
    if (devices.length === 0) return;

    // Build eventKey for dedup (see research.md Decision 4)
    const eventKey = input.recordingGroupId
      ? `doseTaken:${input.recordingGroupId}`
      : input.prnDoseRecordId
        ? `doseTaken:prn:${input.prnDoseRecordId}`
        : `doseTaken:${input.doseEventId}`;

    // Build FCM payload
    const notification: FcmNotification = {
      title: "服薬記録",
      body: buildDoseTakenBody(input)
    };

    const data: FcmDataPayload = {
      type: "DOSE_TAKEN",
      patientId: input.patientId,
      date: input.date,
      slot: input.slot,
      recordingGroupId: input.recordingGroupId ?? ""
    };

    const apns: FcmApnsOverride = {
      payload: {
        aps: {
          sound: "default",
          "thread-id": `patient-${input.patientId}`
        }
      }
    };

    let sentCount = 0;
    let failedCount = 0;
    let duplicateCount = 0;

    // Send to each device with dedup
    for (const device of devices) {
      const inserted = await tryInsertDelivery({
        eventKey,
        pushDeviceId: device.id
      });

      if (!inserted) {
        // Duplicate — already sent for this event+device
        duplicateCount += 1;
        continue;
      }

      const result = await sendFcmMessage(device.token, notification, data, apns);

      if (result.success) {
        sentCount += 1;
        continue;
      }

      failedCount += 1;

      if (result.errorCode === "UNREGISTERED") {
        try {
          await disablePushDeviceById(device.id);
        } catch (disableError) {
          log(
            "warn",
            `Failed to disable push device ${device.id}: ${disableError instanceof Error ? disableError.message : String(disableError)}`
          );
        }
      }
    }

    log(
      failedCount > 0 ? "warn" : "info",
      [
        "FCM dose taken push result",
        `devices=${devices.length}`,
        `sent=${sentCount}`,
        `failed=${failedCount}`,
        `duplicates=${duplicateCount}`
      ].join(" ")
    );
  } catch (error) {
    log(
      "error",
      `Push notification (FCM dose taken) error: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

/**
 * Send one FCM push per caregiver device when a scheduled slot is still
 * unrecorded one hour after its scheduled time.
 *
 * The stable patient/date/slot event key makes repeated cron executions safe.
 */
export async function notifyCaregiversOfDoseMissed(
  input: DoseMissedNotificationInput
): Promise<void> {
  try {
    if (!isFcmConfigured()) return;

    const caregiverIds = await getLinkedCaregiverIds(input.patientId);
    if (caregiverIds.length === 0) return;

    const devices = await listEnabledPushDevicesForCaregivers(caregiverIds);
    if (devices.length === 0) return;

    const eventKey = `doseMissed:${input.patientId}:${input.date}:${input.slot}`;
    const slotLabel = slotLabels[input.slot] ?? "定時";
    const notification: FcmNotification = {
      title: "飲み忘れのお知らせ",
      body: `${input.displayName}さんの${slotLabel}のお薬が、まだ記録されていません`
    };
    const data: FcmDataPayload = {
      type: "DOSE_MISSED",
      patientId: input.patientId,
      date: input.date,
      slot: input.slot
    };
    const apns: FcmApnsOverride = {
      payload: {
        aps: {
          sound: "default",
          "thread-id": `patient-${input.patientId}`
        }
      }
    };

    let sentCount = 0;
    let failedCount = 0;
    let duplicateCount = 0;

    for (const device of devices) {
      const inserted = await tryInsertDelivery({ eventKey, pushDeviceId: device.id });
      if (!inserted) {
        duplicateCount += 1;
        continue;
      }

      const result = await sendFcmMessage(device.token, notification, data, apns);
      if (result.success) {
        sentCount += 1;
        continue;
      }

      failedCount += 1;
      if (result.errorCode === "UNREGISTERED") {
        try {
          await disablePushDeviceById(device.id);
        } catch (disableError) {
          log(
            "warn",
            `Failed to disable push device ${device.id}: ${disableError instanceof Error ? disableError.message : String(disableError)}`
          );
        }
      }
    }

    log(
      failedCount > 0 ? "warn" : "info",
      [
        "FCM dose missed push result",
        `devices=${devices.length}`,
        `sent=${sentCount}`,
        `failed=${failedCount}`,
        `duplicates=${duplicateCount}`
      ].join(" ")
    );
  } catch (error) {
    log(
      "error",
      `Push notification (FCM dose missed) error: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}
