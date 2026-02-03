import { errorResponse } from "../../../../src/middleware/error";
import { requirePatient } from "../../../../src/middleware/auth";
import { generateScheduleForPatientWithStatus } from "../../../../src/services/scheduleService";
import { buildScheduleResponse } from "../../../../src/services/scheduleResponse";

export const runtime = "nodejs";

function startOfDay(date: Date) {
  const start = new Date(date);
  start.setHours(0, 0, 0, 0);
  return start;
}

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requirePatient(authHeader);
    const now = new Date();
    const from = startOfDay(now);
    const to = new Date(from);
    to.setDate(to.getDate() + 1);

    const doses = await generateScheduleForPatientWithStatus({
      patientId: session.patientId,
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
