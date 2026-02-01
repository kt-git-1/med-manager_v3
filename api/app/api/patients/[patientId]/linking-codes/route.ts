import { errorResponse } from "../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../src/middleware/auth";
import { issueLinkingCodeForPatient } from "../../../../../src/services/linkingService";

export const runtime = "nodejs";

export async function POST(
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
    const issued = await issueLinkingCodeForPatient(session.caregiverUserId, patientId);
    return new Response(
      JSON.stringify({
        data: { code: issued.code, expiresAt: issued.expiresAt.toISOString() }
      }),
      {
        status: 201,
        headers: { "content-type": "application/json" }
      }
    );
  } catch (error) {
    return errorResponse(error);
  }
}
