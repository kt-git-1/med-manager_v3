import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

import {
  ProductionReleaseError,
  validateProductionWorkflow,
  validateReleaseDispatch,
  validateVercelReleaseConfig
} from "./verify-android-production-release.mjs";

const SHA = "1234567890abcdef1234567890abcdef12345678";
const WORKFLOW_PATH = new URL(
  "../../.github/workflows/android-api-production-release.yml",
  import.meta.url
);
const VERCEL_PATH = new URL("../vercel.json", import.meta.url);
const workflow = await readFile(WORKFLOW_PATH, "utf8");
const vercelConfig = JSON.parse(await readFile(VERCEL_PATH, "utf8"));

function accepted(mode, confirmation) {
  const result = validateReleaseDispatch({
    releaseCommit: SHA,
    actualCommit: SHA,
    ref: "refs/heads/main",
    mode,
    confirmation,
    releaseEnabled: "true",
    reviewersConfigured: "true",
    vercelConfig
  });
  assert.equal(result.mode, mode);
  assert.equal(result.writeEnabled, mode !== "preflight");
}

function rejected(label, action) {
  assert.throws(action, ProductionReleaseError, label);
}

accepted("preflight", "PREFLIGHT_ANDROID_API_PRODUCTION");
accepted("deploy", "DEPLOY_ANDROID_API_PRODUCTION");
accepted("release", "RELEASE_ANDROID_API_PRODUCTION");
validateProductionWorkflow(workflow);

const base = {
  releaseCommit: SHA,
  actualCommit: SHA,
  ref: "refs/heads/main",
  mode: "deploy",
  confirmation: "DEPLOY_ANDROID_API_PRODUCTION",
  releaseEnabled: "true",
  reviewersConfigured: "true",
  vercelConfig
};
const dispatchCases = [
  ["short SHA", { releaseCommit: SHA.slice(0, 12) }],
  ["uppercase SHA", { releaseCommit: SHA.toUpperCase() }],
  ["commit mismatch", { actualCommit: "a".repeat(40) }],
  ["wrong ref", { ref: "refs/heads/android-dev" }],
  ["unknown mode", { mode: "apply" }],
  ["wrong confirmation", { confirmation: "yes" }],
  ["release disabled", { releaseEnabled: "false" }],
  ["reviewers unattested", { reviewersConfigured: "false" }]
];
for (const [label, changes] of dispatchCases) {
  rejected(label, () => validateReleaseDispatch({ ...base, ...changes }));
}

const configCases = [
  [
    "main auto deploy",
    { ...vercelConfig, git: { deploymentEnabled: { "android-dev": false, main: true } } }
  ],
  [
    "missing main policy",
    { ...vercelConfig, git: { deploymentEnabled: { "android-dev": false } } }
  ],
  [
    "extra policy",
    {
      ...vercelConfig,
      git: { deploymentEnabled: { "android-dev": false, main: false, staging: false } }
    }
  ]
];
for (const [label, config] of configCases) {
  rejected(label, () => validateVercelReleaseConfig(config));
}

const workflowCases = [
  ["automatic trigger", workflow.replace("  workflow_dispatch:", "  push:\n  workflow_dispatch:")],
  ["secret drift", workflow.replace("secrets.VERCEL_TOKEN", "secrets.EXTRA_TOKEN")],
  ["variable drift", workflow.replace("vars.VERCEL_PROJECT_ID", "vars.EXTRA_PROJECT_ID")],
  ["non-preflight default", workflow.replace("default: preflight", "default: deploy")],
  ["Node runtime drift", workflow.replace("node-version: 22", "node-version: 20")],
  ["unpinned Vercel", workflow.replaceAll("vercel@59.1.3", "vercel@latest")],
  [
    "removed dependency audit",
    workflow.replace("npm audit --audit-level=high", "echo audit-skipped")
  ],
  [
    "unpinned action",
    workflow.replace(
      "actions/checkout@11d5960a326750d5838078e36cf38b85af677262",
      "actions/checkout@v4"
    )
  ],
  [
    "unguarded migration",
    workflow.replace(
      "if: inputs.mode != 'preflight'\n        env:\n          DIRECT_URL",
      "if: always()\n        env:\n          DIRECT_URL"
    )
  ],
  [
    "unguarded deploy",
    workflow.replace(
      "- name: Build and deploy the verified API\n        if: inputs.mode != 'preflight'",
      "- name: Build and deploy the verified API\n        if: always()"
    )
  ],
  [
    "unguarded health",
    workflow.replace(
      "- name: Verify official production health\n        if: inputs.mode != 'preflight'",
      "- name: Verify official production health\n        if: always()"
    )
  ],
  [
    "operation reordering",
    workflow
      .replace("npx prisma migrate deploy", "ORDER_PLACEHOLDER")
      .replace("npx --yes vercel@59.1.3 pull", "npx prisma migrate deploy")
      .replace("ORDER_PLACEHOLDER", "npx --yes vercel@59.1.3 pull")
  ],
  [
    "continue on error",
    workflow.replace("timeout-minutes: 30", "timeout-minutes: 30\n    continue-on-error: true")
  ],
  [
    "forced state",
    workflow.replace("npx prisma migrate deploy", "npx prisma migrate deploy --force")
  ]
];
for (const [label, mutated] of workflowCases) {
  rejected(label, () => validateProductionWorkflow(mutated));
}

console.log(
  `Android API production release contract passed: accepted=3 rejected=${dispatchCases.length + configCases.length + workflowCases.length}`
);
