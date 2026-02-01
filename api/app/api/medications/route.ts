import { errorResponse } from "../../../src/middleware/error";
import { assertCaregiverPatientScope, requireCaregiver } from "../../../src/middleware/auth";
import { validateMedication } from "../../../src/validators/medication";
import { createMedication, listMedications } from "../../../src/services/medicationService";
import { generateScheduleForPatient } from "../../../src/services/scheduleService";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function GET(request: Request) {
  try {
    const session = await requireCaregiver(request.headers.get("authorization") ?? undefined);
    const { searchParams } = new URL(request.url);
    const patientId = searchParams.get("patientId");
    if (!patientId) {
      return new Response(JSON.stringify({ error: "validation", message: "patientId required" }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    assertCaregiverPatientScope(session.caregiverUserId, patientId);
    const medications = await listMedications(patientId);
    const rangeStart = new Date();
    const rangeEnd = new Date(rangeStart.getTime() + 7 * 24 * 60 * 60 * 1000);
    const doses = await generateScheduleForPatient({
      patientId,
      from: rangeStart,
      to: rangeEnd
    });
    const nextByMedication = new Map<string, string>();
    for (const dose of doses) {
      if (!nextByMedication.has(dose.medicationId)) {
        nextByMedication.set(dose.medicationId, dose.scheduledAt);
      }
    }
    const enriched = medications.map((medication) => ({
      ...medication,
      nextScheduledAt: nextByMedication.get(medication.id) ?? null
    }));
    return new Response(JSON.stringify({ data: enriched }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function POST(request: Request) {
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
    assertCaregiverPatientScope(session.caregiverUserId, input.patientId);
    const created = await createMedication(input);
    return new Response(JSON.stringify({ data: created }), {
      status: 201,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
