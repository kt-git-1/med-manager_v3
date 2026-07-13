import { prisma } from "../repositories/prisma";
import { log } from "../logging/logger";

export class SupabaseAccountDeletionError extends Error {
  statusCode = 502;
  code = "supabase_account_delete_failed";

  constructor(message = "Failed to delete Supabase account") {
    super(message);
  }
}

type SupabaseAdminDeleteResult = {
  deleted: boolean;
};

function supabaseAdminConfig() {
  const supabaseUrl = process.env.SUPABASE_URL?.replace(/\/$/, "");
  const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

  if (!supabaseUrl || !serviceRoleKey) {
    log("error", "account_delete.supabase_admin_config_missing");
    throw new SupabaseAccountDeletionError("Supabase admin config is missing");
  }

  return { supabaseUrl, serviceRoleKey };
}

function supabaseAdminHeaders(serviceRoleKey: string) {
  return {
    apikey: serviceRoleKey,
    authorization: `Bearer ${serviceRoleKey}`
  };
}

async function assertSupabaseAuthUserDeleted(
  supabaseUrl: string,
  serviceRoleKey: string,
  caregiverId: string
) {
  const response = await fetch(
    `${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(caregiverId)}`,
    {
      method: "GET",
      headers: supabaseAdminHeaders(serviceRoleKey)
    }
  );

  if (response.status === 404) {
    return;
  }

  const body = await response.text().catch(() => "");
  log(
    "error",
    `account_delete.supabase_auth_user_still_exists status=${response.status} body=${body.slice(0, 500)}`
  );
  throw new SupabaseAccountDeletionError();
}

async function deleteSupabaseAuthUser(caregiverId: string): Promise<SupabaseAdminDeleteResult> {
  const { supabaseUrl, serviceRoleKey } = supabaseAdminConfig();

  const response = await fetch(
    `${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(caregiverId)}`,
    {
      method: "DELETE",
      headers: supabaseAdminHeaders(serviceRoleKey)
    }
  );

  if (response.status === 404) {
    return { deleted: true };
  }

  if (response.ok) {
    await assertSupabaseAuthUserDeleted(supabaseUrl, serviceRoleKey, caregiverId);
    return { deleted: true };
  }

  const body = await response.text().catch(() => "");
  log(
    "error",
    `account_delete.supabase_auth_delete_failed status=${response.status} body=${body.slice(0, 500)}`
  );
  throw new SupabaseAccountDeletionError();
}

async function deleteCaregiverAppData(caregiverId: string) {
  await prisma.$transaction(async (tx) => {
    const patients = await tx.patient.findMany({
      where: { caregiverId },
      select: { id: true }
    });
    const patientIds = patients.map((patient) => patient.id);
    const pushDevices = await tx.pushDevice.findMany({
      where: { ownerType: "caregiver", ownerId: caregiverId },
      select: { id: true }
    });
    const pushDeviceIds = pushDevices.map((device) => device.id);

    if (patientIds.length > 0) {
      await tx.patientSlotTimeRevision.deleteMany({
        where: { patientId: { in: patientIds } }
      });
      await tx.inventoryAlertEvent.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.medicationInventoryAdjustment.deleteMany({
        where: { patientId: { in: patientIds } }
      });
      await tx.prnDoseRecord.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.doseRecord.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.doseRecordEvent.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.regimen.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.medication.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.patientSession.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.linkingCode.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.linkingAttempt.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.caregiverPatientLink.deleteMany({ where: { patientId: { in: patientIds } } });
      await tx.patient.deleteMany({ where: { id: { in: patientIds } } });
    }

    if (pushDeviceIds.length > 0) {
      await tx.pushDelivery.deleteMany({ where: { pushDeviceId: { in: pushDeviceIds } } });
    }

    await tx.pushDevice.deleteMany({ where: { ownerType: "caregiver", ownerId: caregiverId } });
    await tx.deviceToken.deleteMany({ where: { caregiverId } });
    await tx.caregiverEntitlement.deleteMany({ where: { caregiverId } });
  });
}

export async function deleteCaregiverAccount(caregiverId: string) {
  await deleteSupabaseAuthUser(caregiverId);
  await deleteCaregiverAppData(caregiverId);
  return { deleted: true };
}
