import { errorResponse } from "../../../../src/middleware/error";
import { requireCaregiver } from "../../../../src/middleware/auth";
import { getEntitlements } from "../../../../src/services/entitlementService";

export const runtime = "nodejs";

export async function GET(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requireCaregiver(authHeader);

    const result = await getEntitlements(session.caregiverUserId);

    return new Response(JSON.stringify({ data: result }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
