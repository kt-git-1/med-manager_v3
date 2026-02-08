import { errorResponse } from "../../../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  assertPatientScope,
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  requirePatient
} from "../../../../../src/middleware/auth";
import { validateRegimen } from "../../../../../src/validators/regimen";
import { createRegimen, listRegimensForMedication } from "../../../../../src/services/regimenService";
import { getMedication } from "../../../../../src/services/medicationService";

export const runtime = "nodejs";

const DEFAULT_REGIMEN_TZ = "Asia/Tokyo";

function normalizeRegimenTimeZone(value: unknown) {
  if (typeof value !== "string") {
    return DEFAULT_REGIMEN_TZ;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return DEFAULT_REGIMEN_TZ;
  }
  if (trimmed === "UTC" || trimmed === "Etc/UTC") {
    return DEFAULT_REGIMEN_TZ;
  }
  return trimmed;
}

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
    await assertCaregiverPatientScope(session.caregiverUserId, medication.patientId);
    const body = await request.json();
    const startDate = parseDate(body.startDate);
    if (!startDate) {
      return new Response(
        JSON.stringify({ error: "validation", messages: ["startDate is required"] }),
        {
          status: 422,
          headers: { "content-type": "application/json" }
        }
      );
    }
    const input = {
      medicationId: id,
      patientId: medication.patientId,
      timezone: normalizeRegimenTimeZone(body.timezone),
      startDate,
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

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    const isCaregiver = isCaregiverToken(token);
    const medication = await getMedication(id);
    if (!medication) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    if (isCaregiver) {
      const session = await requireCaregiver(authHeader);
      await assertCaregiverPatientScope(session.caregiverUserId, medication.patientId);
    } else {
      const session = await requirePatient(authHeader);
      assertPatientScope(medication.patientId, session.patientId);
    }
    const regimens = await listRegimensForMedication(id);
    return new Response(JSON.stringify({ data: regimens }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
