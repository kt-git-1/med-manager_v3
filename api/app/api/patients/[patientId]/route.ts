import { errorResponse } from "../../../../src/middleware/error";
import {
  AuthError,
  assertCaregiverPatientScope,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../src/middleware/auth";
import { deletePatientForCaregiver } from "../../../../src/services/linkingService";
import {
  savePatientSlotTimes,
  validatePatientSlotTimes
} from "../../../../src/services/patientSlotTimeService";

export const runtime = "nodejs";

export async function DELETE(
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
    await deletePatientForCaregiver(session.caregiverUserId, patientId);
    return new Response(JSON.stringify({ data: { deleted: true } }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function PATCH(
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
    const body = await request.json();
    const { errors, slotTimes } = validatePatientSlotTimes(body.slotTimes);
    if (errors.length || !slotTimes) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const saved = await savePatientSlotTimes(patientId, slotTimes);
    return new Response(JSON.stringify({ data: { slotTimes: saved } }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
