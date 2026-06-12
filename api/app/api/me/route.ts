import { errorResponse } from "../../../src/middleware/error";
import { requireCaregiver } from "../../../src/middleware/auth";
import { deleteCaregiverAccount } from "../../../src/services/accountDeletionService";

export const runtime = "nodejs";

export async function DELETE(request: Request) {
  try {
    const authHeader = request.headers.get("authorization") ?? undefined;
    const session = await requireCaregiver(authHeader);
    const result = await deleteCaregiverAccount(session.caregiverUserId);

    return new Response(JSON.stringify({ data: result }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  } catch (error) {
    return errorResponse(error);
  }
}
