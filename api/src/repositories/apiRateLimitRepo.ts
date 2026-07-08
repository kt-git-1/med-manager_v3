import { prisma } from "./prisma";

export type ApiRateLimitResult = {
  allowed: boolean;
  count: number;
  resetAt: Date;
};

export async function incrementApiRateLimit(input: {
  key: string;
  maxRequests: number;
  windowMs: number;
  now?: Date;
}): Promise<ApiRateLimitResult> {
  const now = input.now ?? new Date();
  const resetAt = new Date(now.getTime() + input.windowMs);

  return prisma.$transaction(async (tx) => {
    await tx.apiRateLimit.deleteMany({
      where: {
        key: input.key,
        resetAt: { lte: now }
      }
    });

    const record = await tx.apiRateLimit.upsert({
      where: { key: input.key },
      create: {
        key: input.key,
        count: 1,
        resetAt
      },
      update: {
        count: { increment: 1 }
      }
    });

    return {
      allowed: record.count <= input.maxRequests,
      count: record.count,
      resetAt: record.resetAt
    };
  });
}
