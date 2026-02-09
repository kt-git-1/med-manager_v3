import { errorResponse } from "../../../src/middleware/error";
import {
  assertCaregiverPatientScope,
  assertPatientScope,
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver,
  requirePatient
} from "../../../src/middleware/auth";
import { validateMedication } from "../../../src/validators/medication";
import { createMedication, listMedications, listMedicationInventory, listActiveRegimens } from "../../../src/services/medicationService";
import { generateScheduleForPatient } from "../../../src/services/scheduleService";
import { SCHEDULE_LOOKAHEAD_DAYS } from "../../../src/constants";

export const runtime = "nodejs";

function parseDate(value: string | undefined) {
  return value ? new Date(value) : undefined;
}

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const patientId = searchParams.get("patientId");
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    const isCaregiver = isCaregiverToken(token);
    let resolvedPatientId = patientId;

    if (!resolvedPatientId) {
      if (isCaregiver) {
        return new Response(
          JSON.stringify({ error: "validation", message: "patientId required" }),
          {
            status: 422,
            headers: { "content-type": "application/json" }
          }
        );
      }
      const session = await requirePatient(authHeader);
      resolvedPatientId = session.patientId;
    } else if (isCaregiver) {
      const session = await requireCaregiver(authHeader);
      await assertCaregiverPatientScope(session.caregiverUserId, resolvedPatientId);
    } else {
      const session = await requirePatient(authHeader);
      assertPatientScope(resolvedPatientId, session.patientId);
    }

    const medications = await listMedications(resolvedPatientId);
    const rangeStart = new Date();
    const rangeEnd = new Date(rangeStart.getTime() + SCHEDULE_LOOKAHEAD_DAYS * 24 * 60 * 60 * 1000);
    const [doses, inventoryItems, regimens] = await Promise.all([
      generateScheduleForPatient({
        patientId: resolvedPatientId,
        from: rangeStart,
        to: rangeEnd
      }),
      listMedicationInventory(resolvedPatientId),
      listActiveRegimens(resolvedPatientId)
    ]);
    const nextByMedication = new Map<string, string>();
    for (const dose of doses) {
      if (!nextByMedication.has(dose.medicationId)) {
        nextByMedication.set(dose.medicationId, dose.scheduledAt);
      }
    }
    const inventoryOutMap = new Map<string, boolean>();
    for (const item of inventoryItems) {
      inventoryOutMap.set(item.medicationId, item.out);
    }
    const regimenByMedication = new Map<string, { times: string[]; daysOfWeek: string[] }>();
    for (const regimen of regimens) {
      if (!regimenByMedication.has(regimen.medicationId)) {
        regimenByMedication.set(regimen.medicationId, {
          times: regimen.times,
          daysOfWeek: regimen.daysOfWeek
        });
      }
    }
    const enriched = medications.map((medication) => ({
      ...medication,
      nextScheduledAt: nextByMedication.get(medication.id) ?? null,
      inventoryOut: inventoryOutMap.get(medication.id) ?? false,
      regimenTimes: regimenByMedication.get(medication.id)?.times ?? null,
      regimenDaysOfWeek: regimenByMedication.get(medication.id)?.daysOfWeek ?? null
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
      isPrn: body.isPrn ?? false,
      prnInstructions: body.prnInstructions ?? null,
      startDate: parseDate(body.startDate),
      endDate: parseDate(body.endDate)
    };
    if (!input.patientId) {
      return new Response(JSON.stringify({ error: "validation", message: "patientId required" }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    const errors = validateMedication(input);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }
    await assertCaregiverPatientScope(session.caregiverUserId, input.patientId);
    const created = await createMedication(input);
    return new Response(JSON.stringify({ data: created }), {
      status: 201,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
