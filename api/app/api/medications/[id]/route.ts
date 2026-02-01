import { errorResponse } from "../../../../src/middleware/error";
import { assertCaregiverPatientScope, requireCaregiver } from "../../../../src/middleware/auth";
import { validateMedication } from "../../../../src/validators/medication";
import {
  archiveMedication,
  getMedication,
  updateMedication
} from "../../../../src/services/medicationService";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function GET(
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
    return new Response(JSON.stringify({ data: medication }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function PATCH(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const session = await requireCaregiver(request.headers.get("authorization") ?? undefined);
    const body = await request.json();
    const input = {
      ...body,
      startDate: parseDate(body.startDate),
      endDate: parseDate(body.endDate)
    };
    const errors = validateMedication(input);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const existing = await getMedication(params.id);
    if (!existing) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    assertCaregiverPatientScope(session.caregiverUserId, existing.patientId);
    const updated = await updateMedication(params.id, input);
    return new Response(JSON.stringify({ data: updated }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(
  request: Request,
  { params }: { params: { id: string } }
) {
  try {
    const session = await requireCaregiver(request.headers.get("authorization") ?? undefined);
    const existing = await getMedication(params.id);
    if (!existing) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    assertCaregiverPatientScope(session.caregiverUserId, existing.patientId);
    await archiveMedication(params.id);
    return new Response(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
