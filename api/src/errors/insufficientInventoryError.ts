export class InsufficientInventoryError extends Error {
  readonly statusCode = 409;
  readonly code = "insufficient_inventory";

  constructor() {
    super("Insufficient inventory");
  }
}
