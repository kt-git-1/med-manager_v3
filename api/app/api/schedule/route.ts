import { errorResponse } from "../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  requireCaregiver,
  requirePatient
} from "../../../src/middleware/auth";
import { generateScheduleForPatient } from "../../../src/services/scheduleService";
import { buildScheduleResponse } from "../../../src/services/scheduleResponse";

export const runtime = "nodejs";

function parseDate(value: string | null) {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const from = parseDate(searchParams.get("from"));
    const to = parseDate(searchParams.get("to"));
    if (!from || !to) {
      return new Response(JSON.stringify({ error: "validation", message: "from/to required" }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const authHeader = request.headers.get("authorization") ?? undefined;
    const patientIdParam = searchParams.get("patientId");
    let patientId: string;

    if (patientIdParam) {
      const session = await requireCaregiver(authHeader);
      assertCaregiverPatientScope(session.caregiverUserId, patientIdParam);
      patientId = patientIdParam;
    } else {
      const session = await requirePatient(authHeader);
      patientId = session.patientId;
    }

    const doses = await generateScheduleForPatient({ patientId, from, to });
    const payload = buildScheduleResponse(doses);
    return new Response(JSON.stringify(payload), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
