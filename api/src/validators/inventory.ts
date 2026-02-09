import type { InventoryAdjustmentReason } from "@prisma/client";

export type InventoryUpdateValidationResult = {
  errors: string[];
  inventoryEnabled?: boolean;
  inventoryQuantity?: number;
  inventoryLowThreshold?: number;
};

export type InventoryAdjustValidationResult = {
  errors: string[];
  reason?: InventoryAdjustmentReason;
  delta?: number;
  absoluteQuantity?: number;
};

const allowedReasons: InventoryAdjustmentReason[] = ["REFILL", "SET", "CORRECTION"];

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value);
}

export function validateInventoryUpdate(input: {
  inventoryEnabled?: unknown;
  inventoryQuantity?: unknown;
  inventoryLowThreshold?: unknown;
}): InventoryUpdateValidationResult {
  const errors: string[] = [];
  const result: InventoryUpdateValidationResult = { errors };

  if (
    input.inventoryEnabled === undefined &&
    input.inventoryQuantity === undefined &&
    input.inventoryLowThreshold === undefined
  ) {
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

  if (input.inventoryLowThreshold !== undefined) {
    if (!isInteger(input.inventoryLowThreshold)) {
      errors.push("inventoryLowThreshold must be an integer");
    } else if (input.inventoryLowThreshold < 0) {
      errors.push("inventoryLowThreshold must be >= 0");
    } else {
      result.inventoryLowThreshold = input.inventoryLowThreshold;
    }
  }

  return result;
}

export function validateInventoryAdjust(input: {
  reason?: unknown;
  delta?: unknown;
  absoluteQuantity?: unknown;
}): InventoryAdjustValidationResult {
  const errors: string[] = [];
  const result: InventoryAdjustValidationResult = { errors };

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
