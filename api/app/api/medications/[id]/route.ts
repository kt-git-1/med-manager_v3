import { errorResponse } from "../../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  assertPatientScope,
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  requirePatient
} from "../../../../src/middleware/auth";
import { validateMedication } from "../../../../src/validators/medication";
import {
  archiveMedication,
  getMedication,
  updateMedication
} from "../../../../src/services/medicationService";

export const runtime = "nodejs";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const medication = await getMedication(id);
    if (!medication) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    const isCaregiver = isCaregiverToken(token);
    if (isCaregiver) {
      const session = await requireCaregiver(authHeader);
      await assertCaregiverPatientScope(session.caregiverUserId, medication.patientId);
    } else {
      const session = await requirePatient(authHeader);
      assertPatientScope(medication.patientId, session.patientId);
    }
    return new Response(JSON.stringify({ data: medication }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
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
    const existing = await getMedication(id);
    if (!existing) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    await assertCaregiverPatientScope(session.caregiverUserId, existing.patientId);
    const updated = await updateMedication(id, input);
    return new Response(JSON.stringify({ data: updated }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(
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
    const existing = await getMedication(id);
    if (!existing) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    await assertCaregiverPatientScope(session.caregiverUserId, existing.patientId);
    await archiveMedication(id);
    return new Response(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
