import { errorResponse } from "../../../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../src/middleware/auth";
import { validateRegimen } from "../../../../../src/validators/regimen";
import { createRegimen } from "../../../../../src/services/regimenService";
import { getMedication } from "../../../../../src/services/medicationService";

export const runtime = "nodejs";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function POST(
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
    const medication = await getMedication(id);
    if (!medication) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    assertCaregiverPatientScope(session.caregiverUserId, medication.patientId);
    const body = await request.json();
    const input = {
      medicationId: id,
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
