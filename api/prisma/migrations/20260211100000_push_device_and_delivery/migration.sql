-- CreateTable
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

-- CreateTable
CREATE TABLE "PushDelivery" (
    "id" TEXT NOT NULL,
    "eventKey" TEXT NOT NULL,
    "pushDeviceId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PushDelivery_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "PushDevice_ownerType_ownerId_token_key" ON "PushDevice"("ownerType", "ownerId", "token");

-- CreateIndex
CREATE INDEX "PushDevice_ownerId_idx" ON "PushDevice"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "PushDelivery_eventKey_pushDeviceId_key" ON "PushDelivery"("eventKey", "pushDeviceId");

-- CreateIndex
CREATE INDEX "PushDelivery_pushDeviceId_idx" ON "PushDelivery"("pushDeviceId");

-- AddForeignKey
ALTER TABLE "PushDelivery" ADD CONSTRAINT "PushDelivery_pushDeviceId_fkey" FOREIGN KEY ("pushDeviceId") REFERENCES "PushDevice"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
