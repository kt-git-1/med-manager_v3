import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 60_000,
  workers: process.env.SUPABASE_TEST_ALLOW_DESTRUCTIVE_LINKING === "true" ? 1 : undefined,
  expect: {
    timeout: 10_000
  },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure"
  }
});
