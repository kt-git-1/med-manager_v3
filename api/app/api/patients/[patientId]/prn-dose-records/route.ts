import { errorResponse } from "../../../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  assertPatientScope,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  requirePatient
} from "../../../../../src/middleware/auth";
import { validatePrnDoseRecordCreate } from "../../../../../src/validators/prnDoseRecord";
import { createPrnRecord } from "../../../../../src/services/prnDoseRecordService";

export const runtime = "nodejs";

export async function POST(
  request: Request,
  { params }: { params: Promise<{ patientId: string }> }
) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    const isCaregiver = isCaregiverToken(token);
    const { patientId } = await params;
    let actorType: "PATIENT" | "CAREGIVER";

    if (isCaregiver) {
      const session = await requireCaregiver(authHeader);
      await assertCaregiverPatientScope(session.caregiverUserId, patientId);
      actorType = "CAREGIVER";
    } else {
      const session = await requirePatient(authHeader);
      assertPatientScope(patientId, session.patientId);
      actorType = "PATIENT";
    }

    const body = await request.json();
    const { errors, takenAt } = validatePrnDoseRecordCreate({
      medicationId: body.medicationId,
      takenAt: body.takenAt,
      quantityTaken: body.quantityTaken
    });
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const result = await createPrnRecord({
      patientId,
      medicationId: body.medicationId,
      takenAt,
      quantityTaken: body.quantityTaken,
      actorType
    });
    if (!result) {
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }
    if ("error" in result) {
      if (result.error === "not_prn") {
        return new Response(
          JSON.stringify({ error: "validation", messages: ["medication must be PRN"] }),
          {
            status: 422,
            headers: { "content-type": "application/json" }
          }
        );
      }
      return new Response(JSON.stringify({ error: "not_found" }), {
        status: 404,
        headers: { "content-type": "application/json" }
      });
    }

    const record = result.record;
    return new Response(
      JSON.stringify({
        record: {
          id: record.id,
          patientId: record.patientId,
          medicationId: record.medicationId,
          takenAt: record.takenAt.toISOString(),
          quantityTaken: record.quantityTaken,
          actorType: record.actorType,
          createdAt: record.createdAt.toISOString()
        },
        medicationInventory: null
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
