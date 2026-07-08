import { errorResponse } from "../../../../src/middleware/error";
import { AuthError, getBearerToken, isCaregiverToken } from "../../../../src/middleware/auth";
import { revokePatientSessionToken } from "../../../../src/services/patientSessionService";

export const runtime = "nodejs";

export async function DELETE(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!token) {
      throw new AuthError("Unauthorized", 401);
    }
    if (isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }

    const result = await revokePatientSessionToken(token);
    return new Response(JSON.stringify({ data: result }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
