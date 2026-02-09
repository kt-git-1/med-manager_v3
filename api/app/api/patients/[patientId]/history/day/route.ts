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
import { listPrnHistoryItemsByRange } from "../../../../../../src/services/prnDoseRecordService";
import { validateDateString } from "../../../../../../src/validators/schedule";

export const runtime = "nodejs";

const historyTimeZone = "Asia/Tokyo";
const slotOrder = ["morning", "noon", "evening", "bedtime"] as const;

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

    const range = getDayRange(parsedDate, historyTimeZone);
    const doses = await getScheduleWithStatus(patientId, range.from, range.to, historyTimeZone);
    const prn = await listPrnHistoryItemsByRange({
      patientId,
      from: range.from,
      to: range.to,
      timeZone: historyTimeZone
    });

    const items = doses
      .map((dose) => {
        const slot = resolveSlot(dose.scheduledAt, historyTimeZone, customSlotTimes);
        if (!slot) {
          return null;
        }
        return {
          medicationId: dose.medicationId,
          medicationName: dose.medicationSnapshot.name,
          dosageText: dose.medicationSnapshot.dosageText,
          doseCountPerIntake: dose.medicationSnapshot.doseCountPerIntake,
          scheduledAt: dose.scheduledAt,
          slot,
          effectiveStatus: dose.effectiveStatus
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
    return errorResponse(error);
  }
}
