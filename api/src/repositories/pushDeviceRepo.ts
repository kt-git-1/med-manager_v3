// ---------------------------------------------------------------------------
// PushDevice repository
//
// CRUD operations for FCM push device registration.
// Used by push register/unregister endpoints and push dispatch.
// ---------------------------------------------------------------------------

import { prisma } from "./prisma";

// ---------------------------------------------------------------------------
// Upsert — register or re-enable a push device
// ---------------------------------------------------------------------------

export async function upsertPushDevice(input: {
  ownerType: string;
  ownerId: string;
  token: string;
  platform: string;
  environment: string;
}) {
  return prisma.pushDevice.upsert({
    where: {
      ownerType_ownerId_token: {
        ownerType: input.ownerType,
        ownerId: input.ownerId,
        token: input.token
      }
    },
    create: {
      ownerType: input.ownerType,
      ownerId: input.ownerId,
      token: input.token,
      platform: input.platform,
      environment: input.environment,
      isEnabled: true,
      lastSeenAt: new Date()
    },
    update: {
      isEnabled: true,
      lastSeenAt: new Date(),
      platform: input.platform,
      environment: input.environment
    }
  });
}

// ---------------------------------------------------------------------------
// Disable — soft-delete a push device (sets isEnabled=false)
// ---------------------------------------------------------------------------

export async function disablePushDevice(input: {
  ownerType: string;
  ownerId: string;
  token: string;
}) {
  await prisma.pushDevice.updateMany({
    where: {
      ownerType: input.ownerType,
      ownerId: input.ownerId,
      token: input.token
    },
    data: {
      isEnabled: false
    }
  });
}

// ---------------------------------------------------------------------------
// Disable by ID — used when FCM returns UNREGISTERED
// ---------------------------------------------------------------------------

export async function disablePushDeviceById(id: string) {
  await prisma.pushDevice.update({
    where: { id },
    data: { isEnabled: false }
  });
}

// ---------------------------------------------------------------------------
// List enabled devices for caregivers — used by push dispatch
// ---------------------------------------------------------------------------

export async function listEnabledPushDevicesForCaregivers(
  caregiverIds: string[]
) {
  if (caregiverIds.length === 0) return [];

  return prisma.pushDevice.findMany({
    where: {
      ownerType: "CAREGIVER",
      ownerId: { in: caregiverIds },
      isEnabled: true
    }
  });
}
