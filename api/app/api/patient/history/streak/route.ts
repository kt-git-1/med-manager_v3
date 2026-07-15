import { errorResponse } from "../../../../../src/middleware/error";
import { requirePatient } from "../../../../../src/middleware/auth";
import { getPatientHistoryStreak } from "../../../../../src/services/historyStreakService";

export const runtime = "nodejs";

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requirePatient(authHeader);
    const streak = await getPatientHistoryStreak(session.patientId);
    return new Response(JSON.stringify(streak), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
