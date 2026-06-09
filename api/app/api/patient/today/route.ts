import { errorResponse } from "../../../../src/middleware/error";
import { requirePatient } from "../../../../src/middleware/auth";
import {
  generateScheduleForPatientWithStatus,
  getDayRange
} from "../../../../src/services/scheduleService";
import {
  buildScheduleResponse,
  parseSlotTimesFromParams
} from "../../../../src/services/scheduleResponse";

export const runtime = "nodejs";

const PATIENT_TZ = "Asia/Tokyo";

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requirePatient(authHeader);
    const now = new Date();
    const { from, to } = getDayRange(now, PATIENT_TZ);
    const url = new URL(request.url);
    const slotTimeParse = parseSlotTimesFromParams(url.searchParams);
    if (slotTimeParse.errors.length > 0) {
      return new Response(JSON.stringify({ errors: slotTimeParse.errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const doses = await generateScheduleForPatientWithStatus({
      patientId: session.patientId,
      from,
      to,
      now,
      slotTimes: slotTimeParse.slotTimes
    });
    const payload = buildScheduleResponse(doses);
    return new Response(JSON.stringify(payload), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
