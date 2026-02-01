import { errorResponse } from "../../../../../src/middleware/error";
import { AuthError, getBearerToken, isCaregiverToken } from "../../../../../src/middleware/auth";
import { refreshPatientSessionToken } from "../../../../../src/services/patientSessionService";

export const runtime = "nodejs";

export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!token) {
      throw new AuthError("Unauthorized", 401);
    }
    if (isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const refreshed = await refreshPatientSessionToken(token);
    return new Response(
      JSON.stringify({
        data: {
          patientSessionToken: refreshed.patientSessionToken,
          expiresAt: refreshed.expiresAt
        }
      }),
      {
        status: 200,
        headers: { "content-type": "application/json" }
      }
    );
  } catch (error) {
    return errorResponse(error);
  }
}
