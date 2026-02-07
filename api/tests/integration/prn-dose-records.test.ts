import { describe, it } from "vitest";

describe("prn dose records integration", () => {
  it.skip("allows patient to create PRN record but denies delete", () => {
    // Implemented with PRN endpoints (T018-T020).
  });

  it.skip("allows linked caregiver create/delete and conceals non-owned", () => {
    // Implemented with PRN endpoints and caregiver concealment (T018-T020).
  });

  it.skip("adjusts inventory on create/delete when enabled", () => {
    // Implemented with PRN inventory integration (T018).
  });

  it.skip("rejects create when medication is not PRN", () => {
    // Implemented with PRN validation (T016-T019).
  });
});
