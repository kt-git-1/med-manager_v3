import { errorResponse } from "../../../../../../src/middleware/error";
import { logDoseRecordOperation } from "../../../../../../src/logging/logger";
import {
  assertCaregiverPatientScope,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  AuthError
} from "../../../../../../src/middleware/auth";
import { validateSlotBulkRecordRequest } from "../../../../../../src/validators/slotBulkRecord";
import { bulkRecordSlot } from "../../../../../../src/services/slotBulkRecordService";
import { resolvePatientSlotTimes } from "../../../../../../src/services/patientSlotTimeService";

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
    await assertCaregiverPatientScope(session.caregiverUserId, patientId);

    const body = await request.json();
    const validation = validateSlotBulkRecordRequest({ date: body.date, slot: body.slot });
    if (validation.errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: validation.errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const result = await bulkRecordSlot({
      patientId,
      date: validation.date!,
      slot: validation.slot!,
      customSlotTimes: await resolvePatientSlotTimes(patientId),
      recordedByType: "CAREGIVER",
      recordedById: session.caregiverUserId
    });

    logDoseRecordOperation("create", "caregiver");
    return Response.json(result);
  } catch (error) {
    return errorResponse(error);
  }
}
