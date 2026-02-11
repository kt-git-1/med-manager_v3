import { describe, expect, it, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// T004 – T006: Backend integration tests for push send trigger + dedup
//
// Tests validate:
// - TAKEN recording triggers push to all enabled caregiver devices
// - Dedup via PushDelivery prevents duplicate sends
// - Stable eventKey using doseEventId (single) / prnDoseRecordId (PRN) / recordingGroupId (bulk)
// - NotRegistered response disables PushDevice
// - Caregiver linkage scoping (no push to unlinked caregivers)
// ---------------------------------------------------------------------------

// -- Types ------------------------------------------------------------------

type PushDevice = {
  id: string;
  ownerType: string;
  ownerId: string;
  token: string;
  platform: string;
  environment: string;
  isEnabled: boolean;
};

// -- In-memory stores -------------------------------------------------------

const deviceStore: PushDevice[] = [];
const deliveryStore = new Set<string>(); // "eventKey:pushDeviceId"
const disabledDeviceIds = new Set<string>();

// -- Mocks ------------------------------------------------------------------

// FCM sender mock
const sendFcmMessageMock = vi.fn();

vi.mock("../../src/services/fcmService", () => ({
  sendFcmMessage: (...args: unknown[]) => sendFcmMessageMock(...args),
  isFcmConfigured: () => true
}));

// PushDevice repo mock
const listEnabledPushDevicesForCaregiversMock = vi.fn();
const disablePushDeviceByIdMock = vi.fn();

vi.mock("../../src/repositories/pushDeviceRepo", () => ({
  listEnabledPushDevicesForCaregivers: (...args: unknown[]) =>
    listEnabledPushDevicesForCaregiversMock(...args),
  disablePushDeviceById: (...args: unknown[]) =>
    disablePushDeviceByIdMock(...args),
  upsertPushDevice: vi.fn(),
  disablePushDevice: vi.fn()
}));

// PushDelivery repo mock
const tryInsertDeliveryMock = vi.fn();

vi.mock("../../src/repositories/pushDeliveryRepo", () => ({
  tryInsertDelivery: (...args: unknown[]) => tryInsertDeliveryMock(...args)
}));

// CaregiverPatientLink — mock via prisma
vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    caregiverPatientLink: {
      findMany: vi.fn(async (args: { where: { patientId: string; status: string } }) => {
        if (args.where.patientId === "patient-1") {
          return [{ caregiverId: "caregiver-1" }];
        }
        return [];
      })
    }
  }
}));

// Suppress log output in tests
vi.mock("../../src/logging/logger", () => ({
  log: vi.fn()
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function resetStores() {
  deviceStore.length = 0;
  deliveryStore.clear();
  disabledDeviceIds.clear();
}

function seedDevices(devices: PushDevice[]) {
  deviceStore.push(...devices);
}

const defaultDevices: PushDevice[] = [
  {
    id: "device-1",
    ownerType: "CAREGIVER",
    ownerId: "caregiver-1",
    token: "fcm-token-a",
    platform: "ios",
    environment: "DEV",
    isEnabled: true
  },
  {
    id: "device-2",
    ownerType: "CAREGIVER",
    ownerId: "caregiver-1",
    token: "fcm-token-b",
    platform: "ios",
    environment: "DEV",
    isEnabled: true
  }
];

// ---------------------------------------------------------------------------
// T004: Push send trigger tests
// ---------------------------------------------------------------------------

describe("notifyCaregiversOfDoseTaken — send trigger", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetStores();

    seedDevices(defaultDevices);

    listEnabledPushDevicesForCaregiversMock.mockImplementation(
      async (caregiverIds: string[]) =>
        deviceStore.filter((d) => caregiverIds.includes(d.ownerId) && d.isEnabled)
    );

    tryInsertDeliveryMock.mockImplementation(
      async (input: { eventKey: string; pushDeviceId: string }) => {
        const dedupKey = `${input.eventKey}:${input.pushDeviceId}`;
        if (deliveryStore.has(dedupKey)) {
          return false; // Duplicate
        }
        deliveryStore.add(dedupKey);
        return true; // Inserted
      }
    );

    disablePushDeviceByIdMock.mockImplementation(async (id: string) => {
      disabledDeviceIds.add(id);
    });

    sendFcmMessageMock.mockResolvedValue({ success: true });
  });

  it("sends push to all enabled devices for linked caregivers", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      recordingGroupId: "group-uuid-1",
      withinTime: true,
      isPrn: false
    });

    // Should send to both devices
    expect(sendFcmMessageMock).toHaveBeenCalledTimes(2);
  });

  it("includes correct payload with type, date, slot, recordingGroupId", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      recordingGroupId: "group-uuid-1",
      withinTime: true,
      isPrn: false
    });

    // Verify first call's args
    const firstCall = sendFcmMessageMock.mock.calls[0];
    const token = firstCall[0];
    const notification = firstCall[1];
    const data = firstCall[2];

    expect(token).toBe("fcm-token-a");
    expect(notification.title).toBe("服薬記録");
    expect(notification.body).toContain("太郎");
    expect(data.type).toBe("DOSE_TAKEN");
    expect(data.patientId).toBe("patient-1");
    expect(data.date).toBe("2026-02-11");
    expect(data.slot).toBe("morning");
    expect(data.recordingGroupId).toBe("group-uuid-1");
  });

  it("notification body contains displayName", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "花子",
      date: "2026-02-11",
      slot: "evening",
      recordingGroupId: "group-uuid-2",
      withinTime: false,
      isPrn: false
    });

    const notification = sendFcmMessageMock.mock.calls[0][1];
    expect(notification.body).toContain("花子");
  });
});

// ---------------------------------------------------------------------------
// T005: Dedup tests
// ---------------------------------------------------------------------------

describe("notifyCaregiversOfDoseTaken — dedup", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetStores();
    seedDevices(defaultDevices);

    listEnabledPushDevicesForCaregiversMock.mockImplementation(
      async (caregiverIds: string[]) =>
        deviceStore.filter((d) => caregiverIds.includes(d.ownerId) && d.isEnabled)
    );

    tryInsertDeliveryMock.mockImplementation(
      async (input: { eventKey: string; pushDeviceId: string }) => {
        const dedupKey = `${input.eventKey}:${input.pushDeviceId}`;
        if (deliveryStore.has(dedupKey)) {
          return false;
        }
        deliveryStore.add(dedupKey);
        return true;
      }
    );

    disablePushDeviceByIdMock.mockImplementation(async (id: string) => {
      disabledDeviceIds.add(id);
    });

    sendFcmMessageMock.mockResolvedValue({ success: true });
  });

  it("prevents duplicate push for same recordingGroupId (second call skipped)", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    const input = {
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning" as const,
      recordingGroupId: "group-uuid-dedup",
      withinTime: true,
      isPrn: false
    };

    // First call → pushes sent
    await notifyCaregiversOfDoseTaken(input);
    expect(sendFcmMessageMock).toHaveBeenCalledTimes(2);

    sendFcmMessageMock.mockClear();

    // Second call with same recordingGroupId → dedup, no push
    await notifyCaregiversOfDoseTaken(input);
    expect(sendFcmMessageMock).toHaveBeenCalledTimes(0);
  });

  it("uses eventKey doseTaken:{recordingGroupId} when recordingGroupId present", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      recordingGroupId: "group-abc",
      withinTime: true,
      isPrn: false
    });

    // Verify tryInsertDelivery was called with correct eventKey
    expect(tryInsertDeliveryMock).toHaveBeenCalledWith(
      expect.objectContaining({
        eventKey: "doseTaken:group-abc"
      })
    );
  });

  it("sends no push when no linked caregiver has push enabled (empty device list)", async () => {
    listEnabledPushDevicesForCaregiversMock.mockResolvedValue([]);

    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      recordingGroupId: "group-uuid-1",
      withinTime: true,
      isPrn: false
    });

    expect(sendFcmMessageMock).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// T006: Fallback eventKey + linkage scoping + NotRegistered
// ---------------------------------------------------------------------------

describe("notifyCaregiversOfDoseTaken — stable eventKey + linkage + NotRegistered", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetStores();
    seedDevices(defaultDevices);

    listEnabledPushDevicesForCaregiversMock.mockImplementation(
      async (caregiverIds: string[]) =>
        deviceStore.filter((d) => caregiverIds.includes(d.ownerId) && d.isEnabled)
    );

    tryInsertDeliveryMock.mockImplementation(
      async (input: { eventKey: string; pushDeviceId: string }) => {
        const dedupKey = `${input.eventKey}:${input.pushDeviceId}`;
        if (deliveryStore.has(dedupKey)) {
          return false;
        }
        deliveryStore.add(dedupKey);
        return true;
      }
    );

    disablePushDeviceByIdMock.mockImplementation(async (id: string) => {
      disabledDeviceIds.add(id);
    });

    sendFcmMessageMock.mockResolvedValue({ success: true });
  });

  it("uses doseEventId in eventKey for single-dose recording", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      doseEventId: "dose-event-uuid-1",
      withinTime: true,
      isPrn: false
    });

    const firstCallEventKey = tryInsertDeliveryMock.mock.calls[0][0].eventKey;
    expect(firstCallEventKey).toBe("doseTaken:dose-event-uuid-1");
  });

  it("uses prnDoseRecordId in eventKey for PRN dose recording", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      prnDoseRecordId: "prn-record-uuid-1",
      withinTime: true,
      isPrn: true
    });

    const firstCallEventKey = tryInsertDeliveryMock.mock.calls[0][0].eventKey;
    expect(firstCallEventKey).toBe("doseTaken:prn:prn-record-uuid-1");
  });

  it("sends no push when caregiver is not linked to patient", async () => {
    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    // patient-2 has no linked caregivers (see prisma mock above)
    await notifyCaregiversOfDoseTaken({
      patientId: "patient-2",
      displayName: "次郎",
      date: "2026-02-11",
      slot: "noon",
      recordingGroupId: "group-uuid-1",
      withinTime: true,
      isPrn: false
    });

    expect(listEnabledPushDevicesForCaregiversMock).not.toHaveBeenCalled();
    expect(sendFcmMessageMock).not.toHaveBeenCalled();
  });

  it("disables device on UNREGISTERED FCM response", async () => {
    sendFcmMessageMock.mockResolvedValue({
      success: false,
      errorCode: "UNREGISTERED"
    });

    const { notifyCaregiversOfDoseTaken } = await import(
      "../../src/services/pushNotificationService"
    );

    await notifyCaregiversOfDoseTaken({
      patientId: "patient-1",
      displayName: "太郎",
      date: "2026-02-11",
      slot: "morning",
      recordingGroupId: "group-uuid-unreg",
      withinTime: true,
      isPrn: false
    });

    // Both devices should be disabled
    expect(disablePushDeviceByIdMock).toHaveBeenCalledWith("device-1");
    expect(disablePushDeviceByIdMock).toHaveBeenCalledWith("device-2");
  });
});
