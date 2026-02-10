import { errorResponse } from "../../../../../../src/middleware/error";
import {
  AuthError,
  assertCaregiverPatientScope,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../../src/middleware/auth";
import {
  getLocalDateKey,
  getMonthRange,
  getScheduleWithStatus
} from "../../../../../../src/services/scheduleService";
import {
  buildSlotSummary,
  groupDosesByLocalDate,
  parseSlotTimesFromParams
} from "../../../../../../src/services/scheduleResponse";
import { listPrnHistoryItemsByRange } from "../../../../../../src/services/prnDoseRecordService";
import { validateYearMonth } from "../../../../../../src/validators/schedule";
import { checkRetentionForMonth } from "../../../../../../src/services/historyRetentionService";
import { HistoryRetentionError } from "../../../../../../src/errors/historyRetentionError";

export const runtime = "nodejs";

const historyTimeZone = "Asia/Tokyo";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ patientId: string }> }
) {
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
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const { patientId } = await params;
    await assertCaregiverPatientScope(session.caregiverUserId, patientId);
    await checkRetentionForMonth(year, month, "caregiver", session.caregiverUserId);

    const range = getMonthRange(year, month, historyTimeZone);
    const doses = await getScheduleWithStatus(patientId, range.from, range.to, historyTimeZone);
    const grouped = groupDosesByLocalDate(doses, historyTimeZone);
    const prn = await listPrnHistoryItemsByRange({
      patientId,
      from: range.from,
      to: range.to,
      timeZone: historyTimeZone
    });

    const days: { date: string; slotSummary: ReturnType<typeof buildSlotSummary> }[] = [];
    const cursor = new Date(range.from);
    while (cursor < range.to) {
      const dateKey = getLocalDateKey(cursor, historyTimeZone);
      const dayDoses = grouped.get(dateKey) ?? [];
      days.push({
        date: dateKey,
        slotSummary: buildSlotSummary(dayDoses, historyTimeZone, customSlotTimes)
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
