import { errorResponse } from "../../../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  assertCaregiverPatientScope
} from "../../../../../../../src/middleware/auth";
import { validateInventoryUpdate } from "../../../../../../../src/validators/inventory";
import { updateMedicationInventorySettings } from "../../../../../../../src/services/medicationService";

export const runtime = "nodejs";

export async function PATCH(
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
    const validation = validateInventoryUpdate({
      inventoryEnabled: body.inventoryEnabled,
      inventoryQuantity: body.inventoryQuantity,
      inventoryLowThreshold: body.inventoryLowThreshold
    });
    if (validation.errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: validation.errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const updated = await updateMedicationInventorySettings({
      patientId,
      medicationId,
      update: {
        inventoryEnabled: validation.inventoryEnabled,
        inventoryQuantity: validation.inventoryQuantity,
        inventoryLowThreshold: validation.inventoryLowThreshold
      }
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
