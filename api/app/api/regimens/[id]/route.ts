import { errorResponse } from "../../../../src/middleware/error";
import { assertCaregiverPatientScope, requireCaregiver } from "../../../../src/middleware/auth";
import { validateRegimen } from "../../../../src/validators/regimen";
import { getRegimen, updateRegimen } from "../../../../src/services/regimenService";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function PATCH(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const session = await requireCaregiver(request.headers.get("authorization") ?? undefined);
    const existing = await getRegimen(params.id);
    if (!existing) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    assertCaregiverPatientScope(session.caregiverUserId, existing.patientId);
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
    const updated = await updateRegimen(params.id, {
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
