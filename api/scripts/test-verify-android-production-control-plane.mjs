import assert from "node:assert/strict";

import {
  ControlPlaneError,
  validateControlPlane,
  validateEnvironmentState,
  validateRepositoryState,
  validateSecretState,
  validateVariableState,
  validateWorkflowState
} from "./verify-android-production-control-plane.mjs";

const SHA = "a".repeat(40);
const secretNames = [
  "ANDROID_PRODUCTION_DIRECT_URL",
  "PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS",
  "VERCEL_TOKEN"
];
const variableValues = {
  ANDROID_API_PRODUCTION_RELEASE_ENABLED: "false",
  ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED: "true",
  VERCEL_ORG_ID: "org_fixture",
  VERCEL_PROJECT_ID: "project_fixture"
};

function fixture() {
  return {
    repository: {
      full_name: "kt-git-1/med-manager_v3",
      default_branch: "main",
      archived: false,
      disabled: false
    },
    branch: { name: "main", protected: true, commit: { sha: SHA } },
    workflows: {
      total_count: 2,
      workflows: [
        {
          name: "Android CI",
          path: ".github/workflows/android-ci.yml",
          state: "active"
        },
        {
          name: "Android API Production Release",
          path: ".github/workflows/android-api-production-release.yml",
          state: "active"
        }
      ]
    },
    environment: {
      name: "android-api-production",
      protection_rules: [
        {
          type: "required_reviewers",
          prevent_self_review: true,
          reviewers: [{ type: "Team", reviewer: { slug: "release-owners" } }]
        },
        { type: "branch_policy" }
      ],
      deployment_branch_policy: {
        protected_branches: true,
        custom_branch_policies: false
      }
    },
    secrets: {
      total_count: secretNames.length,
      secrets: secretNames.map((name) => ({ name }))
    },
    variables: {
      total_count: Object.keys(variableValues).length,
      variables: Object.entries(variableValues).map(([name, value]) => ({ name, value }))
    }
  };
}

function clone(value) {
  return structuredClone(value);
}

function rejected(label, operation) {
  assert.throws(operation, ControlPlaneError, `Rejected fixture unexpectedly passed: ${label}`);
}

const safe = validateControlPlane(fixture(), { expectedMainSha: SHA, armState: "safe" });
assert.equal(safe.armState, "safe");
assert.equal(safe.secretCount, 3);
assert.equal(safe.variableCount, 4);

const armedFixture = fixture();
armedFixture.variables.variables.find(
  (item) => item.name === "ANDROID_API_PRODUCTION_RELEASE_ENABLED"
).value = "true";
const armed = validateControlPlane(armedFixture, { expectedMainSha: SHA, armState: "armed" });
assert.equal(armed.armState, "armed");

validateRepositoryState(fixture().repository, fixture().branch, SHA);
validateWorkflowState(fixture().workflows);
validateEnvironmentState(fixture().environment);
validateSecretState(fixture().secrets);
validateVariableState(fixture().variables, "safe");

const cases = [
  ["bad expected SHA", (value) => validateControlPlane(value, { expectedMainSha: "A".repeat(40) })],
  [
    "wrong repository",
    (value) => (
      (value.repository.full_name = "other/repo"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "wrong default branch",
    (value) => (
      (value.repository.default_branch = "master"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "archived repository",
    (value) => (
      (value.repository.archived = true),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "disabled repository",
    (value) => (
      (value.repository.disabled = true),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "wrong branch",
    (value) => (
      (value.branch.name = "android-dev"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "wrong live SHA",
    (value) => (
      (value.branch.commit.sha = "b".repeat(40)),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "unprotected main",
    (value) => (
      (value.branch.protected = false),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "missing workflow",
    (value) => (
      (value.workflows.workflows = value.workflows.workflows.slice(0, 1)),
      (value.workflows.total_count = 1),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "duplicate workflow",
    (value) => (
      value.workflows.workflows.push(clone(value.workflows.workflows[1])),
      (value.workflows.total_count = 3),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "workflow count drift",
    (value) => (
      (value.workflows.total_count = 3),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "inactive workflow",
    (value) => (
      (value.workflows.workflows[1].state = "disabled_manually"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "wrong workflow name",
    (value) => (
      (value.workflows.workflows[1].name = "Production"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "wrong environment",
    (value) => (
      (value.environment.name = "Production"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "missing reviewer rule",
    (value) => (
      (value.environment.protection_rules = []),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "duplicate reviewer rule",
    (value) => (
      value.environment.protection_rules.push(clone(value.environment.protection_rules[0])),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "self review allowed",
    (value) => (
      (value.environment.protection_rules[0].prevent_self_review = false),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "no reviewer",
    (value) => (
      (value.environment.protection_rules[0].reviewers = []),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "malformed reviewer",
    (value) => (
      (value.environment.protection_rules[0].reviewers = [{ type: "Bot", reviewer: {} }]),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "unprotected deployment branch",
    (value) => (
      (value.environment.deployment_branch_policy.protected_branches = false),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "custom deployment branches",
    (value) => (
      (value.environment.deployment_branch_policy.custom_branch_policies = true),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "missing secret",
    (value) => (
      value.secrets.secrets.pop(),
      (value.secrets.total_count -= 1),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "extra secret",
    (value) => (
      value.secrets.secrets.push({ name: "EXTRA_SECRET" }),
      (value.secrets.total_count += 1),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "duplicate secret",
    (value) => (
      (value.secrets.secrets[1].name = value.secrets.secrets[0].name),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "secret count drift",
    (value) => (
      (value.secrets.total_count = 99),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "missing variable",
    (value) => (
      value.variables.variables.pop(),
      (value.variables.total_count -= 1),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "extra variable",
    (value) => (
      value.variables.variables.push({ name: "EXTRA_VARIABLE", value: "x" }),
      (value.variables.total_count += 1),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "duplicate variable",
    (value) => (
      (value.variables.variables[1].name = value.variables.variables[0].name),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "reviewer attestation false",
    (value) => (
      (value.variables.variables.find(
        (item) => item.name === "ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED"
      ).value = "false"),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "safe arm true",
    (value) => (
      (value.variables.variables.find(
        (item) => item.name === "ANDROID_API_PRODUCTION_RELEASE_ENABLED"
      ).value = "true"),
      validateControlPlane(value, { expectedMainSha: SHA, armState: "safe" })
    )
  ],
  [
    "armed arm false",
    (value) => validateControlPlane(value, { expectedMainSha: SHA, armState: "armed" })
  ],
  [
    "unknown arm state",
    (value) => validateControlPlane(value, { expectedMainSha: SHA, armState: "unknown" })
  ],
  [
    "empty Vercel org",
    (value) => (
      (value.variables.variables.find((item) => item.name === "VERCEL_ORG_ID").value = ""),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ],
  [
    "padded Vercel project",
    (value) => (
      (value.variables.variables.find((item) => item.name === "VERCEL_PROJECT_ID").value =
        " project "),
      validateControlPlane(value, { expectedMainSha: SHA })
    )
  ]
];

for (const [label, operation] of cases) rejected(label, () => operation(fixture()));

console.log(
  `Android API production control-plane contract passed: accepted=7 rejected=${cases.length}`
);
