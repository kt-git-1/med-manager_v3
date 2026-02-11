import { errorResponse } from "../../../../../../src/middleware/error";
import {
  AuthError,
  getBearerToken,
  isCaregiverToken,
  requireCaregiver
} from "../../../../../../src/middleware/auth";
import { getPatientRecordForCaregiver } from "../../../../../../src/repositories/patientRepo";
import { validateReportRange } from "../../../../../../src/validators/reportValidator";
import { generateReport } from "../../../../../../src/services/reportService";
import { InvalidRangeError } from "../../../../../../src/errors/invalidRangeError";
import { HistoryRetentionError } from "../../../../../../src/errors/historyRetentionError";
import { checkRetentionForDay } from "../../../../../../src/services/historyRetentionService";

export const runtime = "nodejs";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ patientId: string }> }
) {
  try {
    // ----- Auth: caregiver only -----
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      throw new AuthError("Forbidden", 403);
    }
    const session = await requireCaregiver(authHeader);
    const { patientId } = await params;

    // ----- Scope assertion + patient record for displayName -----
    const patient = await getPatientRecordForCaregiver(
      patientId,
      session.caregiverUserId
    );
    if (!patient) {
      throw new AuthError("Not found", 404);
    }

    // ----- Validate date range -----
    const { searchParams } = new URL(request.url);
    const from = searchParams.get("from");
    const to = searchParams.get("to");
    validateReportRange(from, to);

    // ----- Retention defence-in-depth (optional, blocks free users for old dates) -----
    await checkRetentionForDay(from!, "caregiver", session.caregiverUserId);

    // ----- Generate report -----
    const result = await generateReport(
      patientId,
      from!,
      to!,
      patient.displayName
    );

    return new Response(JSON.stringify(result), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    if (error instanceof InvalidRangeError) {
      return new Response(
        JSON.stringify({
          code: error.code,
          message: error.message
        }),
        {
          status: 400,
          headers: { "content-type": "application/json" }
        }
      );
    }
    if (error instanceof HistoryRetentionError) {
      return new Response(
        JSON.stringify({
          code: "HISTORY_RETENTION_LIMIT",
          message: error.message,
          cutoffDate: error.cutoffDate,
          retentionDays: error.retentionDays
        }),
        {
          status: 403,
          headers: { "content-type": "application/json" }
        }
      );
    }
    return errorResponse(error);
  }
}
