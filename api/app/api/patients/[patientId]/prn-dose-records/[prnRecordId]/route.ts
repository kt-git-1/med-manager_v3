import { errorResponse } from "../../../../../../src/middleware/error";
import {
  AuthError,
  assertCaregiverPatientScope,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../../src/middleware/auth";
import { deletePrnRecord } from "../../../../../../src/services/prnDoseRecordService";

export const runtime = "nodejs";

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ patientId: string; prnRecordId: string }> }
) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const { patientId, prnRecordId } = await params;
    await assertCaregiverPatientScope(session.caregiverUserId, patientId);

    const deleted = await deletePrnRecord({ patientId, prnRecordId });
    if (!deleted) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    return new Response(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
