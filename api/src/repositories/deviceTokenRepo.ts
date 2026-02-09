import type { DeviceToken } from "@prisma/client";
import { prisma } from "./prisma";

export async function upsertDeviceToken(input: {
  caregiverId: string;
  token: string;
  platform?: string;
}): Promise<DeviceToken> {
  return prisma.deviceToken.upsert({
    where: {
      caregiverId_token: {
        caregiverId: input.caregiverId,
        token: input.token
      }
    },
    create: {
      caregiverId: input.caregiverId,
      token: input.token,
      platform: input.platform ?? "ios"
    },
    update: {
      updatedAt: new Date()
    }
  });
}

export async function deleteDeviceToken(input: {
  caregiverId: string;
  token: string;
}): Promise<DeviceToken | null> {
  try {
    return await prisma.deviceToken.delete({
      where: {
        caregiverId_token: {
          caregiverId: input.caregiverId,
          token: input.token
        }
      }
    });
  } catch {
    return null;
  }
}

export async function listDeviceTokensForCaregivers(
  caregiverIds: string[]
): Promise<DeviceToken[]> {
  if (caregiverIds.length === 0) return [];
  return prisma.deviceToken.findMany({
    where: { caregiverId: { in: caregiverIds } }
  });
}

export async function deleteAllDeviceTokensForCaregiver(
  caregiverId: string
): Promise<number> {
  const result = await prisma.deviceToken.deleteMany({
    where: { caregiverId }
  });
  return result.count;
}
