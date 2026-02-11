# Data Model: Push Notification Foundation (012)

**Branch**: `012-push-foundation` | **Date**: 2026-02-11 | **Plan**: [plan.md](./plan.md)

## Overview

Two new Prisma models introduced for push device management and delivery deduplication. No modifications to existing tables.

---

## New Models

### PushDevice

Stores registered push notification devices for caregivers. Each row represents one FCM registration token for one caregiver on one device.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | String (UUID) | PK, auto-generated | Unique device record ID |
| `ownerType` | String | Not null | Owner role type. MVP: "CAREGIVER". Future: "PATIENT" |
| `ownerId` | String | Not null | Owner's user ID (caregiverId for CAREGIVER) |
| `token` | String | Not null | FCM registration token |
| `platform` | String | Not null, default "ios" | Device platform. MVP: "ios" only |
| `environment` | String | Not null | Push environment: "DEV" or "PROD" |
| `isEnabled` | Boolean | Not null, default true | Whether push is active for this device |
| `lastSeenAt` | DateTime | Not null, default now() | Last time the device registered or re-registered |
| `createdAt` | DateTime | Not null, default now() | Record creation timestamp |
| `updatedAt` | DateTime | Not null, auto-updated | Record update timestamp |

**Indexes**:
- `@@unique([ownerType, ownerId, token])` — Prevents duplicate registrations for the same owner+token
- `@@index([ownerId])` — Fast lookup by caregiver ID

**Prisma schema**:
```prisma
model PushDevice {
  id           String   @id @default(uuid())
  ownerType    String
  ownerId      String
  token        String
  platform     String   @default("ios")
  environment  String
  isEnabled    Boolean  @default(true)
  lastSeenAt   DateTime @default(now())
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt

  deliveries PushDelivery[]

  @@unique([ownerType, ownerId, token])
  @@index([ownerId])
}
```

### PushDelivery

Deduplication record. Each row represents one push notification sent for a specific event to a specific device. The UNIQUE constraint on (eventKey, pushDeviceId) prevents duplicate sends.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | String (UUID) | PK, auto-generated | Unique delivery record ID |
| `eventKey` | String | Not null | Dedup key derived from the triggering event |
| `pushDeviceId` | String | Not null, FK → PushDevice.id | Target device |
| `createdAt` | DateTime | Not null, default now() | Delivery timestamp |

**Indexes**:
- `@@unique([eventKey, pushDeviceId])` — Prevents duplicate deliveries for the same event+device
- `@@index([pushDeviceId])` — Fast lookup by device

**eventKey format**:
- Slot bulk TAKEN: `doseTaken:{recordingGroupId}` (e.g., `doseTaken:a1b2c3d4-e5f6-7890-abcd-ef1234567890`)
- Single dose TAKEN: `doseTaken:{doseRecordEventId}` (e.g., `doseTaken:event-abc123`)
- PRN dose TAKEN: `doseTaken:prn:{prnDoseRecordId}` (e.g., `doseTaken:prn:prn-xyz789`)

**Prisma schema**:
```prisma
model PushDelivery {
  id           String     @id @default(uuid())
  eventKey     String
  pushDeviceId String
  createdAt    DateTime   @default(now())

  pushDevice PushDevice @relation(fields: [pushDeviceId], references: [id])

  @@unique([eventKey, pushDeviceId])
  @@index([pushDeviceId])
}
```

---

## Existing Models (unchanged)

### DeviceToken (legacy, APNs)

The existing `DeviceToken` model remains unchanged for backward compatibility with inventory alert push (feature 006). Once all push paths migrate to FCM, this table can be retired.

```prisma
model DeviceToken {
  id           String   @id @default(uuid())
  caregiverId  String
  token        String
  platform     String   @default("ios")
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt

  @@unique([caregiverId, token])
  @@index([caregiverId])
}
```

### CaregiverPatientLink (used by push service)

Used by `pushNotificationService.ts` to resolve which caregivers are linked to a patient. Not modified by 012.

---

## Relationships

```
CaregiverPatientLink (caregiverId, patientId)
  └── PushDevice (ownerId = caregiverId, ownerType = "CAREGIVER")
        └── PushDelivery (pushDeviceId = PushDevice.id)
```

**Flow**: When a patient records TAKEN:
1. Look up linked caregivers via `CaregiverPatientLink` (patientId → caregiverIds)
2. Find enabled devices via `PushDevice` (ownerId in caregiverIds, isEnabled=true)
3. For each device, check dedup via `PushDelivery` (eventKey + pushDeviceId unique)
4. If new, send FCM push and insert delivery record

---

## Migration

Single Prisma migration: `push_device_and_delivery`

```sql
CREATE TABLE "PushDevice" (
    "id" TEXT NOT NULL,
    "ownerType" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "platform" TEXT NOT NULL DEFAULT 'ios',
    "environment" TEXT NOT NULL,
    "isEnabled" BOOLEAN NOT NULL DEFAULT true,
    "lastSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "PushDevice_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "PushDelivery" (
    "id" TEXT NOT NULL,
    "eventKey" TEXT NOT NULL,
    "pushDeviceId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "PushDelivery_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "PushDevice_ownerType_ownerId_token_key" ON "PushDevice"("ownerType", "ownerId", "token");
CREATE INDEX "PushDevice_ownerId_idx" ON "PushDevice"("ownerId");
CREATE UNIQUE INDEX "PushDelivery_eventKey_pushDeviceId_key" ON "PushDelivery"("eventKey", "pushDeviceId");
CREATE INDEX "PushDelivery_pushDeviceId_idx" ON "PushDelivery"("pushDeviceId");

ALTER TABLE "PushDelivery" ADD CONSTRAINT "PushDelivery_pushDeviceId_fkey"
    FOREIGN KEY ("pushDeviceId") REFERENCES "PushDevice"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
```
