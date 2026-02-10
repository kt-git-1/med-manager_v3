import { errorResponse } from "../../../../src/middleware/error";
import { requireCaregiver } from "../../../../src/middleware/auth";
import { validateClaimInput } from "../../../../src/validators/iapValidator";
import { claimEntitlement } from "../../../../src/services/entitlementService";

export const runtime = "nodejs";

export async function POST(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requireCaregiver(authHeader);

    const body = await request.json();
    const validation = validateClaimInput(body);

    if (validation.errors.length > 0) {
      return new Response(
        JSON.stringify({ error: "validation", messages: validation.errors }),
        { status: 422, headers: { "content-type": "application/json" } }
      );
    }

    const result = await claimEntitlement(session.caregiverUserId, {
      productId: validation.productId,
      signedTransactionInfo: validation.signedTransactionInfo,
      environment: validation.environment
    });

    return new Response(JSON.stringify({ data: result }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
