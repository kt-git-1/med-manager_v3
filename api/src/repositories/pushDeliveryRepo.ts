// ---------------------------------------------------------------------------
// PushDelivery repository
//
// Deduplication insert-or-ignore for exactly-once push per device per event.
// Uses UNIQUE(eventKey, pushDeviceId) constraint.
// ---------------------------------------------------------------------------

import { prisma } from "./prisma";

/**
 * Attempt to insert a delivery record.
 * Returns true if inserted (new delivery), false if duplicate (already sent).
 *
 * Uses Prisma createMany with skipDuplicates to leverage the
 * UNIQUE(eventKey, pushDeviceId) constraint for insert-or-ignore behavior.
 */
export async function tryInsertDelivery(input: {
  eventKey: string;
  pushDeviceId: string;
}): Promise<boolean> {
  const result = await prisma.pushDelivery.createMany({
    data: [
      {
        eventKey: input.eventKey,
        pushDeviceId: input.pushDeviceId
      }
    ],
    skipDuplicates: true
  });

  return result.count > 0;
}
