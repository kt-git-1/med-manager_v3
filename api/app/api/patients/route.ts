import { errorResponse } from "../../../src/middleware/error";
import { AuthError, getBearerToken, isCaregiverToken, requireCaregiver } from "../../../src/middleware/auth";
import { validatePatientCreate } from "../../../src/validators/patient";
import { createPatientForCaregiver, listPatientsForCaregiver } from "../../../src/services/linkingService";

export const runtime = "nodejs";

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const patients = await listPatientsForCaregiver(session.caregiverUserId);
    return new Response(JSON.stringify({ data: patients }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const body = await request.json();
    const { errors, displayName } = validatePatientCreate({ displayName: body.displayName });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const created = await createPatientForCaregiver(session.caregiverUserId, displayName);
    return new Response(JSON.stringify({ data: created }), {
      status: 201,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
