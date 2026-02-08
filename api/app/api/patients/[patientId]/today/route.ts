import { errorResponse } from "../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  assertCaregiverPatientScope
} from "../../../../../src/middleware/auth";
import {
  generateScheduleForPatientWithStatus,
  getDayRange
} from "../../../../../src/services/scheduleService";
import { buildScheduleResponse } from "../../../../../src/services/scheduleResponse";

export const runtime = "nodejs";

const PATIENT_TZ = "Asia/Tokyo";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ patientId: string }> }
) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const { patientId } = await params;
    await assertCaregiverPatientScope(session.caregiverUserId, patientId);
    const now = new Date();
    const { from, to } = getDayRange(now, PATIENT_TZ);

    const doses = await generateScheduleForPatientWithStatus({
      patientId,
      from,
      to,
      now
    });
    const payload = buildScheduleResponse(doses);
    return new Response(JSON.stringify(payload), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
