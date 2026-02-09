// ---------------------------------------------------------------------------
// Push notification orchestration service
//
// Determines WHO should receive push notifications and sends them via APNs.
// Called after DoseRecordEvent / InventoryAlertEvent creation.
// ---------------------------------------------------------------------------

import { listDeviceTokensForCaregivers } from "../repositories/deviceTokenRepo";
import { sendPushNotifications, type ApnsPayload } from "./apnsService";
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
