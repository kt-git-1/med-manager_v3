import { errorResponse } from "../../../src/middleware/error";
import { getBearerToken, isCaregiverToken, requireCaregiver } from "../../../src/middleware/auth";
import { validateDeviceTokenRegister, validateDeviceTokenDelete } from "../../../src/validators/deviceToken";
import { upsertDeviceToken, deleteDeviceToken } from "../../../src/repositories/deviceTokenRepo";

export const runtime = "nodejs";

/**
 * POST /api/device-tokens
 * Register a device token for push notifications (caregiver only).
 */
export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      return new Response(JSON.stringify({ error: "forbidden", message: "Forbidden" }), {
        status: 403,
        headers: { "content-type": "application/json" }
      });
    }
    const session = await requireCaregiver(authHeader);
    const body = await request.json();
    const { errors, token: deviceToken, platform } = validateDeviceTokenRegister(body);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation_error", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    const record = await upsertDeviceToken({
      caregiverId: session.caregiverUserId,
      token: deviceToken,
      platform
    });

    return new Response(JSON.stringify({ data: { id: record.id } }), {
      status: 201,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}

/**
 * DELETE /api/device-tokens
 * Unregister a device token (caregiver only).
 */
export async function DELETE(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const token = getBearerToken(authHeader);
    if (!isCaregiverToken(token)) {
      return new Response(JSON.stringify({ error: "forbidden", message: "Forbidden" }), {
        status: 403,
        headers: { "content-type": "application/json" }
      });
    }
    const session = await requireCaregiver(authHeader);
    const body = await request.json();
    const { errors, token: deviceToken } = validateDeviceTokenDelete(body);
    if (errors.length) {
      return new Response(JSON.stringify({ error: "validation_error", messages: errors }), {
        status: 422,
        headers: { "content-type": "application/json" }
      });
    }

    await deleteDeviceToken({
      caregiverId: session.caregiverUserId,
      token: deviceToken
    });

    return new Response(JSON.stringify({ ok: true }), {
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
