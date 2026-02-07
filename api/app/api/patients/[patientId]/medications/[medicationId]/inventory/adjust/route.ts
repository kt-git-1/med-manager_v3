import { errorResponse } from "../../../../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  assertCaregiverPatientScope
} from "../../../../../../../../src/middleware/auth";
import { validateInventoryAdjust } from "../../../../../../../../src/validators/inventory";
import { adjustMedicationInventory } from "../../../../../../../../src/services/medicationService";

export const runtime = "nodejs";

export async function POST(
  request: Request,
  { params }: { params: Promise<{ patientId: string; medicationId: string }> }
) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const { patientId, medicationId } = await params;
    await assertCaregiverPatientScope(session.caregiverUserId, patientId);

    const body = await request.json();
    const validation = validateInventoryAdjust({
      reason: body.reason,
      delta: body.delta,
      absoluteQuantity: body.absoluteQuantity
    });
    if (validation.errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: validation.errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const updated = await adjustMedicationInventory({
      patientId,
      medicationId,
      reason: validation.reason!,
      delta: validation.delta,
      absoluteQuantity: validation.absoluteQuantity,
      actorType: "CAREGIVER",
      actorId: session.caregiverUserId
    });
    if (!updated) {
      throw new AuthError("Not found", 404);
    }
    return new Response(JSON.stringify({ data: updated }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
