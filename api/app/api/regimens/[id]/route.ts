import { errorResponse } from "../../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../src/middleware/auth";
import { validateRegimen } from "../../../../src/validators/regimen";
import { getRegimen, updateRegimen } from "../../../../src/services/regimenService";

export const runtime = "nodejs";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    const isCaregiver = isCaregiverToken(token);
    if (!isCaregiver) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const existing = await getRegimen(id);
    if (!existing) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    await assertCaregiverPatientScope(session.caregiverUserId, existing.patientId);
    const body = await request.json();
    const merged = {
      medicationId: existing.medicationId,
      patientId: existing.patientId,
      timezone: body.timezone ?? existing.timezone,
      startDate: parseDate(body.startDate) ?? existing.startDate,
      endDate: parseDate(body.endDate) ?? existing.endDate ?? undefined,
      times: body.times ?? existing.times,
      daysOfWeek: body.daysOfWeek ?? existing.daysOfWeek
    };
    const errors = validateRegimen(merged);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const updated = await updateRegimen(id, {
      timezone: body.timezone,
      startDate: parseDate(body.startDate),
      endDate: parseDate(body.endDate),
      times: body.times,
      daysOfWeek: body.daysOfWeek,
      enabled: body.enabled
    });
    return new Response(JSON.stringify({ data: updated }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
