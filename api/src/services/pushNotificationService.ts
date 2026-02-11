// ---------------------------------------------------------------------------
// Push notification orchestration service
//
// Determines WHO should receive push notifications and sends them.
// - Legacy APNs path: notifyCaregiversOfDoseRecord, notifyCaregiversOfInventoryAlert
// - New FCM path (012): notifyCaregiversOfDoseTaken (with dedup via PushDelivery)
// Called after DoseRecordEvent / InventoryAlertEvent creation.
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
      collapseId: `dose-${input.patientId}`,
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
// Inventory alert notification
// ---------------------------------------------------------------------------

export interface InventoryAlertNotificationInput {
  patientId: string;
  patientDisplayName?: string | null;
  medicationName?: string | null;
  alertType: "LOW" | "OUT";
  remaining: number;
}

/**
 * Send push notifications to all caregivers linked to the patient
 * when an inventory alert is triggered.
 */
export async function notifyCaregiversOfInventoryAlert(
  input: InventoryAlertNotificationInput
): Promise<void> {
  try {
    const caregiverIds = await getLinkedCaregiverIds(input.patientId);
    if (caregiverIds.length === 0) return;

    const deviceTokens = await listDeviceTokensForCaregivers(caregiverIds);
    if (deviceTokens.length === 0) return;

    const payload = buildInventoryAlertPayload(input);
    const tokens = deviceTokens.map((dt) => dt.token);

    await sendPushNotifications(tokens, payload, {
      collapseId: `inventory-${input.patientId}`,
    });
  } catch (error) {
    log(
      "error",
      `Push notification (inventory) error: ${error instanceof Error ? error.message : String(error)}`
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
        body,
      },
      sound: "default",
      "thread-id": `patient-${input.patientId}`,
    },
    type: "dose_record",
    patientId: input.patientId,
  };
}

function buildInventoryAlertPayload(input: InventoryAlertNotificationInput): ApnsPayload {
  const patientName = input.patientDisplayName ?? "患者";
  const medicationName = input.medicationName ?? "お薬";

  const body =
    input.alertType === "OUT"
      ? `${patientName}さんの${medicationName}の在庫がなくなりました`
      : `${patientName}さんの${medicationName}の在庫が残り少なくなっています（残り${input.remaining}）`;

  return {
    aps: {
      alert: {
        title: "在庫アラート",
        body,
      },
      sound: "default",
      "thread-id": `patient-${input.patientId}`,
    },
    type: "inventory_alert",
    patientId: input.patientId,
  };
}

// ---------------------------------------------------------------------------
// FCM-based dose TAKEN notification (012-push-foundation)
// ---------------------------------------------------------------------------

export interface DoseTakenNotificationInput {
  patientId: string;
  displayName: string;
  date: string;               // YYYY-MM-DD in Asia/Tokyo
  slot: string;               // morning | noon | evening | bedtime
  recordingGroupId?: string;  // present for bulk recordings
  doseEventId?: string;       // DoseRecordEvent.id for single-dose dedup
  prnDoseRecordId?: string;   // PrnDoseRecord.id for PRN dedup
  withinTime: boolean;
  isPrn: boolean;
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

    const caregiverIds = await getLinkedCaregiverIds(input.patientId);
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
      body: `${input.displayName}さんが薬を服用しました`
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

    // Send to each device with dedup
    for (const device of devices) {
      const inserted = await tryInsertDelivery({
        eventKey,
        pushDeviceId: device.id
      });

      if (!inserted) {
        // Duplicate — already sent for this event+device
        continue;
      }

      const result = await sendFcmMessage(device.token, notification, data, apns);

      if (!result.success && result.errorCode === "UNREGISTERED") {
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
  } catch (error) {
    log(
      "error",
      `Push notification (FCM dose taken) error: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

