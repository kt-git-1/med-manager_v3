import { errorResponse } from "../../../../../src/middleware/error";
import { logDoseRecordOperation } from "../../../../../src/logging/logger";
import { requirePatient } from "../../../../../src/middleware/auth";
import { validateSlotBulkRecordRequest } from "../../../../../src/validators/slotBulkRecord";
import { parseSlotTimesFromParams } from "../../../../../src/services/scheduleResponse";
import { bulkRecordSlot } from "../../../../../src/services/slotBulkRecordService";

export const runtime = "nodejs";

export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requirePatient(authHeader);
    const body = await request.json();

    // Validate request body
    const validation = validateSlotBulkRecordRequest({
      date: body.date,
      slot: body.slot
    });
    if (validation.errors.length) {
      return new Response(
        JSON.stringify({ error: "validation", messages: validation.errors }),
        { status: 422, headers: { "content-type": "application/json" } }
      );
    }

    // Parse optional custom slot times from query params
    const url = new URL(request.url);
    const slotTimeParse = parseSlotTimesFromParams(url.searchParams);
    if (slotTimeParse.errors.length) {
      return new Response(
        JSON.stringify({ error: "validation", messages: slotTimeParse.errors }),
        { status: 422, headers: { "content-type": "application/json" } }
      );
    }

    // Execute bulk record
    const result = await bulkRecordSlot({
      patientId: session.patientId,
      date: validation.date!,
      slot: validation.slot!,
      customSlotTimes: slotTimeParse.slotTimes
    });

    logDoseRecordOperation("create", "patient");

    return new Response(
      JSON.stringify({
        updatedCount: result.updatedCount,
        remainingCount: result.remainingCount,
        totalPills: result.totalPills,
        medCount: result.medCount,
        slotTime: result.slotTime,
        slotSummary: result.slotSummary,
        recordingGroupId: result.recordingGroupId
      }),
      { status: 200, headers: { "content-type": "application/json" } }
    );
  } catch (error) {
    return errorResponse(error);
  }
}
