import type { InventoryAdjustmentReason } from "@prisma/client";

export type InventoryUpdateValidationResult = {
  errors: string[];
  inventoryEnabled?: boolean;
  inventoryQuantity?: number;
};

export type InventoryAdjustValidationResult = {
  errors: string[];
  clientMutationId?: string;
  reason?: InventoryAdjustmentReason;
  delta?: number;
  absoluteQuantity?: number;
};

const allowedReasons: InventoryAdjustmentReason[] = ["REFILL", "SET", "CORRECTION"];

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

export function validateInventoryUpdate(input: {
  inventoryEnabled?: unknown;
  inventoryQuantity?: unknown;
  inventoryLowThreshold?: unknown;
}): InventoryUpdateValidationResult {
  const errors: string[] = [];
  const result: InventoryUpdateValidationResult = { errors };

  if (input.inventoryLowThreshold !== undefined) {
    errors.push("inventoryLowThreshold is fixed and cannot be updated");
  }

  if (input.inventoryEnabled === undefined && input.inventoryQuantity === undefined) {
    errors.push("at least one field must be provided");
    return result;
  }

  if (input.inventoryEnabled !== undefined) {
    if (typeof input.inventoryEnabled !== "boolean") {
      errors.push("inventoryEnabled must be a boolean");
    } else {
      result.inventoryEnabled = input.inventoryEnabled;
    }
  }

  if (input.inventoryQuantity !== undefined) {
    if (!isFiniteNumber(input.inventoryQuantity)) {
      errors.push("inventoryQuantity must be a number");
    } else if (input.inventoryQuantity < 0) {
      errors.push("inventoryQuantity must be >= 0");
    } else {
      result.inventoryQuantity = input.inventoryQuantity;
    }
  }

  return result;
}

export function validateInventoryAdjust(input: {
  reason?: unknown;
  delta?: unknown;
  absoluteQuantity?: unknown;
  clientMutationId?: unknown;
}): InventoryAdjustValidationResult {
  const errors: string[] = [];
  const result: InventoryAdjustValidationResult = { errors };

  if (input.clientMutationId !== undefined) {
    if (
      typeof input.clientMutationId !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        input.clientMutationId
      )
    ) {
      errors.push("clientMutationId must be a UUID v4");
    } else {
      result.clientMutationId = input.clientMutationId.toLowerCase();
    }
  }

  if (!input.reason || typeof input.reason !== "string") {
    errors.push("reason is required");
    return result;
  }

  if (!allowedReasons.includes(input.reason as InventoryAdjustmentReason)) {
    errors.push("reason must be one of REFILL, SET, CORRECTION");
    return result;
  }

  result.reason = input.reason as InventoryAdjustmentReason;

  if (result.reason === "SET") {
    if (input.absoluteQuantity === undefined) {
      errors.push("absoluteQuantity is required for SET");
      return result;
    }
    if (!isFiniteNumber(input.absoluteQuantity)) {
      errors.push("absoluteQuantity must be a number");
      return result;
    }
    result.absoluteQuantity = input.absoluteQuantity;
    if (result.absoluteQuantity < 0) {
      errors.push("absoluteQuantity must be >= 0");
    }
    return result;
  }

  if (input.delta === undefined) {
    errors.push("delta is required for REFILL or CORRECTION");
    return result;
  }
  if (!isFiniteNumber(input.delta)) {
    errors.push("delta must be a number");
    return result;
  }
  result.delta = input.delta;
  return result;
}
