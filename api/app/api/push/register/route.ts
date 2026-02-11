// ---------------------------------------------------------------------------
// POST /api/push/register — Register a push device for the authenticated caregiver
// ---------------------------------------------------------------------------

export const runtime = "nodejs";

import { requireCaregiver } from "../../../../src/middleware/auth";
import { errorResponse } from "../../../../src/middleware/error";
import { validateRegisterRequest } from "../../../../src/validators/pushRegister";
import { upsertPushDevice } from "../../../../src/repositories/pushDeviceRepo";

export async function POST(req: Request) {
  try {
    const authHeader = req.headers.get("authorization") ?? undefined;
    const { caregiverUserId } = await requireCaregiver(authHeader);

    const body = await req.json();
    const validated = validateRegisterRequest(body);

    if (validated.errors.length > 0) {
      return new Response(
        JSON.stringify({ error: "validation_error", messages: validated.errors }),
        { status: 422, headers: { "content-type": "application/json" } }
      );
    }

    await upsertPushDevice({
      ownerType: "CAREGIVER",
      ownerId: caregiverUserId,
      token: validated.token!,
      platform: validated.platform!,
      environment: validated.environment!
    });

    return new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
