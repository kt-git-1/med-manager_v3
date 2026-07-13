import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-delete-1" }))
}));

const patientFindManyMock = vi.fn();
const deleteMocks = {
  patientSlotTimeRevision: vi.fn(),
  inventoryAlertEvent: vi.fn(),
  medicationInventoryAdjustment: vi.fn(),
  prnDoseRecord: vi.fn(),
  doseRecord: vi.fn(),
  doseRecordEvent: vi.fn(),
  regimen: vi.fn(),
  medication: vi.fn(),
  patientSession: vi.fn(),
  linkingCode: vi.fn(),
  linkingAttempt: vi.fn(),
  caregiverPatientLink: vi.fn(),
  patient: vi.fn(),
  pushDelivery: vi.fn(),
  pushDevice: vi.fn(),
  deviceToken: vi.fn(),
  caregiverEntitlement: vi.fn()
};
const pushDeviceFindManyMock = vi.fn();

const txClient = {
  patient: {
    findMany: patientFindManyMock,
    deleteMany: deleteMocks.patient
  },
  patientSlotTimeRevision: { deleteMany: deleteMocks.patientSlotTimeRevision },
  inventoryAlertEvent: { deleteMany: deleteMocks.inventoryAlertEvent },
  medicationInventoryAdjustment: { deleteMany: deleteMocks.medicationInventoryAdjustment },
  prnDoseRecord: { deleteMany: deleteMocks.prnDoseRecord },
  doseRecord: { deleteMany: deleteMocks.doseRecord },
  doseRecordEvent: { deleteMany: deleteMocks.doseRecordEvent },
  regimen: { deleteMany: deleteMocks.regimen },
  medication: { deleteMany: deleteMocks.medication },
  patientSession: { deleteMany: deleteMocks.patientSession },
  linkingCode: { deleteMany: deleteMocks.linkingCode },
  linkingAttempt: { deleteMany: deleteMocks.linkingAttempt },
  caregiverPatientLink: { deleteMany: deleteMocks.caregiverPatientLink },
  pushDevice: {
    findMany: pushDeviceFindManyMock,
    deleteMany: deleteMocks.pushDevice
  },
  pushDelivery: { deleteMany: deleteMocks.pushDelivery },
  deviceToken: { deleteMany: deleteMocks.deviceToken },
  caregiverEntitlement: { deleteMany: deleteMocks.caregiverEntitlement }
};

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    $transaction: vi.fn(async (callback: (tx: typeof txClient) => Promise<unknown>) => {
      return callback(txClient);
    })
  }
}));

import { CAREGIVER_TOKEN_PREFIX } from "../../src/constants";

const ORIGINAL_SUPABASE_URL = process.env.SUPABASE_URL;
const ORIGINAL_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

function caregiverRequest(headers: HeadersInit = caregiverHeaders()) {
  return new Request("http://localhost/api/me", {
    method: "DELETE",
    headers
  });
}

function caregiverHeaders(): HeadersInit {
  return {
    authorization: `Bearer ${CAREGIVER_TOKEN_PREFIX}valid-jwt`
  };
}

describe("DELETE /api/me", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    process.env.SUPABASE_URL = "https://example.supabase.co";
    process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role-key";
    patientFindManyMock.mockResolvedValue([{ id: "patient-1" }, { id: "patient-2" }]);
    pushDeviceFindManyMock.mockResolvedValue([{ id: "push-device-1" }]);
    Object.values(deleteMocks).forEach((mock) => mock.mockResolvedValue({ count: 1 }));
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 200 }))
        .mockResolvedValueOnce(new Response(null, { status: 404 }))
    );
    consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEachEnv();

  it("returns 401 when authorization is missing", async () => {
    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest({}));

    expect(res.status).toBe(401);
  });

  it("deletes caregiver-owned app data and Supabase Auth user", async () => {
    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest());

    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ data: { deleted: true } });
    expect(patientFindManyMock).toHaveBeenCalledWith({
      where: { caregiverId: "caregiver-delete-1" },
      select: { id: true }
    });
    expect(deleteMocks.patient).toHaveBeenCalledWith({
      where: { id: { in: ["patient-1", "patient-2"] } }
    });
    expect(deleteMocks.patientSlotTimeRevision).toHaveBeenCalledWith({
      where: { patientId: { in: ["patient-1", "patient-2"] } }
    });
    expect(deleteMocks.patientSlotTimeRevision.mock.invocationCallOrder[0]).toBeLessThan(
      deleteMocks.patient.mock.invocationCallOrder[0]
    );
    expect(deleteMocks.pushDelivery).toHaveBeenCalledWith({
      where: { pushDeviceId: { in: ["push-device-1"] } }
    });
    expect(deleteMocks.pushDevice).toHaveBeenCalledWith({
      where: { ownerType: "caregiver", ownerId: "caregiver-delete-1" }
    });
    expect(deleteMocks.deviceToken).toHaveBeenCalledWith({
      where: { caregiverId: "caregiver-delete-1" }
    });
    expect(deleteMocks.caregiverEntitlement).toHaveBeenCalledWith({
      where: { caregiverId: "caregiver-delete-1" }
    });
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "https://example.supabase.co/auth/v1/admin/users/caregiver-delete-1",
      expect.objectContaining({
        method: "DELETE",
        headers: {
          apikey: "service-role-key",
          authorization: "Bearer service-role-key"
        }
      })
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "https://example.supabase.co/auth/v1/admin/users/caregiver-delete-1",
      expect.objectContaining({
        method: "GET",
        headers: {
          apikey: "service-role-key",
          authorization: "Bearer service-role-key"
        }
      })
    );
  });

  it("deletes push deliveries for all caregiver devices before deleting devices", async () => {
    pushDeviceFindManyMock.mockResolvedValue([
      { id: "push-device-1" },
      { id: "push-device-2" },
      { id: "push-device-3" }
    ]);

    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest());

    expect(res.status).toBe(200);
    expect(deleteMocks.pushDelivery).toHaveBeenCalledWith({
      where: { pushDeviceId: { in: ["push-device-1", "push-device-2", "push-device-3"] } }
    });
    expect(deleteMocks.pushDelivery.mock.invocationCallOrder[0]).toBeLessThan(
      deleteMocks.pushDevice.mock.invocationCallOrder[0]
    );
    expect(deleteMocks.pushDevice).toHaveBeenCalledWith({
      where: { ownerType: "caregiver", ownerId: "caregiver-delete-1" }
    });
  });

  it("does not delete unrelated caregiver patient rows", async () => {
    const { DELETE } = await import("../../app/api/me/route");
    await DELETE(caregiverRequest());

    expect(deleteMocks.patient).not.toHaveBeenCalledWith({
      where: { caregiverId: "other-caregiver" }
    });
    expect(deleteMocks.patient).toHaveBeenCalledTimes(1);
  });

  it("treats already-deleted Supabase Auth user as success for retry", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response(null, { status: 404 })));

    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest());

    expect(res.status).toBe(200);
  });

  it("returns 502 and does not delete app data when Supabase Auth deletion fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response(null, { status: 500 })));

    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest());

    expect(res.status).toBe(502);
    const body = await res.json();
    expect(body.error).toBe("supabase_account_delete_failed");
    expect(deleteMocks.patient).not.toHaveBeenCalled();
  });

  it("returns 502 when Supabase Auth user still exists after delete", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 200 }))
        .mockResolvedValueOnce(
          new Response(JSON.stringify({ id: "caregiver-delete-1" }), { status: 200 })
        )
    );

    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest());

    expect(res.status).toBe(502);
    const body = await res.json();
    expect(body.error).toBe("supabase_account_delete_failed");
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(deleteMocks.patient).not.toHaveBeenCalled();
  });

  it("returns 502 when Supabase admin config is missing", async () => {
    delete process.env.SUPABASE_SERVICE_ROLE_KEY;

    const { DELETE } = await import("../../app/api/me/route");
    const res = await DELETE(caregiverRequest());

    expect(res.status).toBe(502);
    const body = await res.json();
    expect(body.error).toBe("supabase_account_delete_failed");
    expect(deleteMocks.patient).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });
});

function afterEachEnv() {
  afterEach(() => {
    if (ORIGINAL_SUPABASE_URL === undefined) {
      delete process.env.SUPABASE_URL;
    } else {
      process.env.SUPABASE_URL = ORIGINAL_SUPABASE_URL;
    }
    if (ORIGINAL_SERVICE_ROLE_KEY === undefined) {
      delete process.env.SUPABASE_SERVICE_ROLE_KEY;
    } else {
      process.env.SUPABASE_SERVICE_ROLE_KEY = ORIGINAL_SERVICE_ROLE_KEY;
    }
    vi.unstubAllGlobals();
    consoleErrorSpy.mockRestore();
  });
}
