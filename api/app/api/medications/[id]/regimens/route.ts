import { errorResponse } from "../../../../../src/middleware/error";
import { assertCaregiverPatientScope, requireCaregiver } from "../../../../../src/middleware/auth";
import { validateRegimen } from "../../../../../src/validators/regimen";
import { createRegimen } from "../../../../../src/services/regimenService";
import { getMedication } from "../../../../../src/services/medicationService";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function POST(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const session = await requireCaregiver(request.headers.get("authorization") ?? undefined);
    const medication = await getMedication(params.id);
    if (!medication) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    assertCaregiverPatientScope(session.caregiverUserId, medication.patientId);
    const body = await request.json();
    const input = {
      medicationId: params.id,
      patientId: medication.patientId,
      timezone: body.timezone,
      startDate: parseDate(body.startDate),
      endDate: parseDate(body.endDate),
      times: body.times,
      daysOfWeek: body.daysOfWeek
    };
    const errors = validateRegimen(input);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const created = await createRegimen(input);
    return new Response(JSON.stringify({ data: created }), {
      status: 201,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
