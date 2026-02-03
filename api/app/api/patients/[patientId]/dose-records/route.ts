import { errorResponse } from "../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../src/middleware/auth";
import {
  createCaregiverDoseRecord,
  deleteCaregiverDoseRecord
} from "../../../../../src/services/doseRecordService";
import { validateDoseRecordCreate, validateDoseRecordDelete } from "../../../../../src/validators/doseRecord";

export const runtime = "nodejs";

function mapRecordedByType(value: string) {
  return value.toLowerCase();
}

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
    const body = await request.json();
    const { errors, scheduledAt } = validateDoseRecordCreate({
      medicationId: body.medicationId,
      scheduledAt: body.scheduledAt
    });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const record = await createCaregiverDoseRecord({
      caregiverUserId: session.caregiverUserId,
      patientId,
      medicationId: body.medicationId,
      scheduledAt: scheduledAt!
    });
    return new Response(
      JSON.stringify({
        data: {
          medicationId: record.medicationId,
          scheduledAt: record.scheduledAt.toISOString(),
          takenAt: record.takenAt.toISOString(),
          recordedByType: mapRecordedByType(record.recordedByType)
        }
      }),
      {
        status: 200,
        headers: { "content-type": "application/json" }
      }
    );
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(
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
    const url = new URL(request.url);
    const medicationId = url.searchParams.get("medicationId") ?? undefined;
    const scheduledAt = url.searchParams.get("scheduledAt") ?? undefined;
    const { errors, scheduledAt: parsedScheduledAt } = validateDoseRecordDelete({
      medicationId,
      scheduledAt
    });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    await deleteCaregiverDoseRecord({
      caregiverUserId: session.caregiverUserId,
      patientId,
      medicationId: medicationId!,
      scheduledAt: parsedScheduledAt!
    });
    return new Response(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
import { errorResponse } from "../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../src/middleware/auth";
import {
  createCaregiverDoseRecord,
  deleteCaregiverDoseRecord
} from "../../../../../src/services/doseRecordService";
import { validateDoseRecordCreate, validateDoseRecordDelete } from "../../../../../src/validators/doseRecord";

export const runtime = "nodejs";

function mapRecordedByType(value: string) {
  return value.toLowerCase();
}

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
    const body = await request.json();
    const { errors, scheduledAt } = validateDoseRecordCreate({
      medicationId: body.medicationId,
      scheduledAt: body.scheduledAt
    });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const record = await createCaregiverDoseRecord({
      caregiverUserId: session.caregiverUserId,
      patientId,
      medicationId: body.medicationId,
      scheduledAt: scheduledAt!
    });
    return new Response(
      JSON.stringify({
        data: {
          medicationId: record.medicationId,
          scheduledAt: record.scheduledAt.toISOString(),
          takenAt: record.takenAt.toISOString(),
          recordedByType: mapRecordedByType(record.recordedByType)
        }
      }),
      {
        status: 200,
        headers: { "content-type": "application/json" }
      }
    );
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(
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
    const url = new URL(request.url);
    const medicationId = url.searchParams.get("medicationId") ?? undefined;
    const scheduledAt = url.searchParams.get("scheduledAt") ?? undefined;
    const { errors, scheduledAt: parsedScheduledAt } = validateDoseRecordDelete({
      medicationId,
      scheduledAt
    });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    await deleteCaregiverDoseRecord({
      caregiverUserId: session.caregiverUserId,
      patientId,
      medicationId: medicationId!,
      scheduledAt: parsedScheduledAt!
    });
    return new Response(null, { status: 204 });
  } catch (error) {
    return errorResponse(error);
  }
}
