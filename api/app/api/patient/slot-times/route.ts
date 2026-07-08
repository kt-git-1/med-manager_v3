import { errorResponse } from "../../../../src/middleware/error";
import { requirePatient } from "../../../../src/middleware/auth";
import { getPatientSlotTimes } from "../../../../src/services/patientSlotTimeService";

export const runtime = "nodejs";

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requirePatient(authHeader);
    const slotTimes = await getPatientSlotTimes(session.patientId);
    return new Response(JSON.stringify({ data: { slotTimes } }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
