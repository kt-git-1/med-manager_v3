import { errorResponse } from "../../../../src/middleware/error";
import { requirePatient } from "../../../../src/middleware/auth";
import { validateDoseRecordCreate } from "../../../../src/validators/doseRecord";
import { createDoseRecordIdempotent } from "../../../../src/services/doseRecordService";

export const runtime = "nodejs";

function mapRecordedByType(value: string) {
  return value.toLowerCase();
}

export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requirePatient(authHeader);
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
    const record = await createDoseRecordIdempotent({
      patientId: session.patientId,
      medicationId: body.medicationId,
      scheduledAt: scheduledAt!,
      recordedByType: "PATIENT",
      recordedById: null
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
