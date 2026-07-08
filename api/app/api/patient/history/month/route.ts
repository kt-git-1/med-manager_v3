import { errorResponse } from "../../../../../src/middleware/error";
import { requirePatient } from "../../../../../src/middleware/auth";
import {
  getLocalDateKey,
  getMonthRange,
  getScheduleWithStatus
} from "../../../../../src/services/scheduleService";
import {
  buildSlotSummary,
  groupDosesByLocalDate,
  parseSlotTimesFromParams
} from "../../../../../src/services/scheduleResponse";
import {
  getPatientSlotTimeTimeline,
  resolvePatientSlotTimes
} from "../../../../../src/services/patientSlotTimeService";
import { listPrnHistoryItemsByRange } from "../../../../../src/services/prnDoseRecordService";
import { validateYearMonth } from "../../../../../src/validators/schedule";
import { checkRetentionForMonth } from "../../../../../src/services/historyRetentionService";
import { HistoryRetentionError } from "../../../../../src/errors/historyRetentionError";

export const runtime = "nodejs";

const historyTimeZone = "Asia/Tokyo";

type RouteSlotTimes = Partial<Record<"morning" | "noon" | "evening" | "bedtime", string>>;
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

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const year = Number(searchParams.get("year"));
    const month = Number(searchParams.get("month"));
    const errors = validateYearMonth(year, month);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
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
    const session = await requirePatient(authHeader);
    await checkRetentionForMonth(year, month, "patient", session.patientId);
    const range = getMonthRange(year, month, historyTimeZone);
    const slotTimeTimeline = customSlotTimes
      ? undefined
      : await getPatientSlotTimeTimeline(session.patientId, range.from, range.to);
    const effectiveSlotTimes = customSlotTimes
      ? await resolvePatientSlotTimes(session.patientId, customSlotTimes)
      : undefined;
    const doses = await getScheduleWithStatus(
      session.patientId,
      range.from,
      range.to,
      historyTimeZone,
      new Date(),
      effectiveSlotTimes,
      slotTimeTimeline
    );
    const grouped = groupDosesByLocalDate(doses, historyTimeZone);
    const prn = await listPrnHistoryItemsByRange({
      patientId: session.patientId,
      from: range.from,
      to: range.to,
      timeZone: historyTimeZone
    });

    const days: { date: string; slotSummary: ReturnType<typeof buildSlotSummary> }[] = [];
    const cursor = new Date(range.from);
    while (cursor < range.to) {
      const dateKey = getLocalDateKey(cursor, historyTimeZone);
      const dayDoses = grouped.get(dateKey) ?? [];
      const endOfLocalDay = new Date(cursor.getTime() + 24 * 60 * 60 * 1000 - 1);
      const summarySlotTimes = resolveRouteSlotTimesForDate(
        endOfLocalDay,
        effectiveSlotTimes,
        slotTimeTimeline
      );
      days.push({
        date: dateKey,
        slotSummary: buildSlotSummary(dayDoses, historyTimeZone, summarySlotTimes)
      });
      cursor.setUTCDate(cursor.getUTCDate() + 1);
    }

    return new Response(JSON.stringify({ year, month, days, prnCountByDay: prn.countByDay }), {
      headers: { "content-type": "application/json" }
    });
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
