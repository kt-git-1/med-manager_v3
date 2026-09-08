import { errorResponse } from "../../../../../../src/middleware/error";
import {
  AuthError,
  assertCaregiverPatientScope,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../../src/middleware/auth";
import {
  getDayRange,
  getLocalDateKey,
  getScheduleWithStatus
} from "../../../../../../src/services/scheduleService";
import {
  resolveSlot,
  parseSlotTimesFromParams
} from "../../../../../../src/services/scheduleResponse";
import {
  getPatientSlotTimeTimeline,
  resolvePatientSlotTimes
} from "../../../../../../src/services/patientSlotTimeService";
import { listPrnHistoryItemsByRange } from "../../../../../../src/services/prnDoseRecordService";
import { validateDateString } from "../../../../../../src/validators/schedule";
import { checkRetentionForDay } from "../../../../../../src/services/historyRetentionService";
import { HistoryRetentionError } from "../../../../../../src/errors/historyRetentionError";
import { listCancelledDoseRecordsByPatientRange } from "../../../../../../src/repositories/doseRecordRepo";

export const runtime = "nodejs";

const historyTimeZone = "Asia/Tokyo";
const slotOrder = ["morning", "noon", "evening", "bedtime"] as const;

type RouteSlotTimes = Partial<Record<(typeof slotOrder)[number], string>>;
type RouteSlotTimeTimelineEntry = { effectiveFrom: Date; slotTimes: RouteSlotTimes };

function resolveRouteSlotTimesForDate(
  date: Date,
  fallback?: RouteSlotTimes,
  timeline?: RouteSlotTimeTimelineEntry[]
) {
  if (!timeline?.length) {
    return fallback;
  }
  let selected: RouteSlotTimeTimelineEntry | undefined;
  for (const entry of timeline) {
    if (entry.effectiveFrom <= date) selected = entry;
    else break;
  }
  return selected?.slotTimes ?? timeline[0]?.slotTimes ?? fallback;
}

export async function GET(
  request: Request,
  { params }: { params: Promise<{ patientId: string }> }
) {
  try {
    const { searchParams } = new URL(request.url);
    const dateParam = searchParams.get("date");
    if (!dateParam) {
      return new Response(JSON.stringify({ error: "validation", message: "date required" }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const errors = validateDateString(dateParam);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const parsedDate = new Date(dateParam);
    if (Number.isNaN(parsedDate.getTime())) {
      return new Response(JSON.stringify({ error: "validation", message: "date invalid" }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const { slotTimes: customSlotTimes, errors: slotTimeErrors } =
      parseSlotTimesFromParams(searchParams);
    if (slotTimeErrors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: slotTimeErrors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const { patientId } = await params;
    await assertCaregiverPatientScope(session.caregiverUserId, patientId);
    await checkRetentionForDay(dateParam, "caregiver", session.caregiverUserId);

    const range = getDayRange(parsedDate, historyTimeZone);
    const slotTimeTimeline = customSlotTimes
      ? undefined
      : await getPatientSlotTimeTimeline(patientId, range.from, range.to);
    const effectiveSlotTimes = customSlotTimes
      ? await resolvePatientSlotTimes(patientId, customSlotTimes)
      : undefined;
    const [doses, prn, cancelledRecords] = await Promise.all([
      getScheduleWithStatus(
        patientId,
        range.from,
        range.to,
        historyTimeZone,
        new Date(),
        effectiveSlotTimes,
        slotTimeTimeline,
        true
      ),
      listPrnHistoryItemsByRange({
        patientId,
        from: range.from,
        to: range.to,
        timeZone: historyTimeZone
      }),
      listCancelledDoseRecordsByPatientRange({ patientId, from: range.from, to: range.to })
    ]);
    const cancelledByDoseKey = new Map(
      cancelledRecords.map((record) => [
        `${record.medicationId}:${record.scheduledAt.toISOString()}`,
        record
      ])
    );
    const cancelledByMedicationSlot = new Map<string, (typeof cancelledRecords)[number][]>();
    for (const record of cancelledRecords) {
      const recordSlotTimes = resolveRouteSlotTimesForDate(
        record.scheduledAt,
        effectiveSlotTimes,
        slotTimeTimeline
      );
      const recordSlot = resolveSlot(
        record.scheduledAt.toISOString(),
        historyTimeZone,
        recordSlotTimes
      );
      if (!recordSlot) continue;
      const key = `${record.medicationId}:${recordSlot}`;
      const matches = cancelledByMedicationSlot.get(key) ?? [];
      matches.push(record);
      cancelledByMedicationSlot.set(key, matches);
    }
    const consumedCancelledRecordIds = new Set<string>();

    const items = doses
      .map((dose) => {
        const doseSlotTimes = resolveRouteSlotTimesForDate(
          new Date(dose.scheduledAt),
          effectiveSlotTimes,
          slotTimeTimeline
        );
        const slot = resolveSlot(dose.scheduledAt, historyTimeZone, doseSlotTimes);
        if (!slot) {
          return null;
        }
        let cancelledRecord = cancelledByDoseKey.get(
          `${dose.medicationId}:${new Date(dose.scheduledAt).toISOString()}`
        );
        if (!cancelledRecord && dose.effectiveStatus !== "taken") {
          cancelledRecord = (
            cancelledByMedicationSlot.get(`${dose.medicationId}:${slot}`) ?? []
          ).find((record) => !consumedCancelledRecordIds.has(record.id));
        }
        if (cancelledRecord) consumedCancelledRecordIds.add(cancelledRecord.id);
        return {
          medicationId: dose.medicationId,
          medicationName: dose.medicationSnapshot.name,
          dosageText: dose.medicationSnapshot.dosageText,
          doseCountPerIntake: dose.medicationSnapshot.doseCountPerIntake,
          scheduledAt: cancelledRecord?.scheduledAt.toISOString() ?? dose.scheduledAt,
          takenAt: dose.takenAt ?? null,
          slot,
          effectiveStatus: dose.effectiveStatus,
          recordedByType: dose.recordedByType ?? null,
          cancelledAt: cancelledRecord?.cancelledAt?.toISOString() ?? null,
          cancelledByType: cancelledRecord?.cancelledByType?.toLowerCase() ?? null,
          cancelledRecordTakenAt: cancelledRecord?.takenAt.toISOString() ?? null,
          inventoryRestored: cancelledRecord?.inventoryRestoredAt != null
        };
      })
      .filter((item): item is NonNullable<typeof item> => item !== null)
      .sort((left, right) => {
        const slotDiff = slotOrder.indexOf(left.slot) - slotOrder.indexOf(right.slot);
        if (slotDiff !== 0) {
          return slotDiff;
        }
        return left.medicationName.localeCompare(right.medicationName);
      });

    return new Response(
      JSON.stringify({
        date: getLocalDateKey(range.from, historyTimeZone),
        doses: items,
        prnItems: prn.items
      }),
      {
        headers: { "content-type": "application/json" }
      }
    );
  } catch (error) {
    if (error instanceof HistoryRetentionError) {
      return new Response(
        JSON.stringify({
          code: "HISTORY_RETENTION_LIMIT",
          message: error.message,
          cutoffDate: error.cutoffDate,
          retentionDays: error.retentionDays
        }),
        {
          status: 403,
          headers: { "content-type": "application/json" }
        }
      );
    }
    return errorResponse(error);
  }
}
