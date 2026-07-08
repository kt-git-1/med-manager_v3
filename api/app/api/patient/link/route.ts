import { errorResponse } from "../../../../src/middleware/error";
import { AuthError, getBearerToken, isCaregiverToken } from "../../../../src/middleware/auth";
import { assertPersistentIpRateLimit } from "../../../../src/middleware/persistentRateLimit";
import { validateLinkCodeInput } from "../../../../src/validators/patient";
import { exchangeLinkingCodeForSession } from "../../../../src/services/patientSessionService";

export const runtime = "nodejs";

const LINK_EXCHANGE_RATE_LIMIT = {
  maxRequests: 20,
  windowMs: 15 * 60 * 1000
};

export async function POST(request: Request) {
  try {
    await assertPersistentIpRateLimit(request, "patient-link", LINK_EXCHANGE_RATE_LIMIT);
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (token && isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const body = await request.json();
    const { errors, code } = validateLinkCodeInput({ code: body.code });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const issued = await exchangeLinkingCodeForSession(code);
    return new Response(
      JSON.stringify({
        data: {
          patientSessionToken: issued.patientSessionToken,
          expiresAt: issued.expiresAt
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
