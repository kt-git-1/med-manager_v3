import process from "node:process";
import { pathToFileURL } from "node:url";

const EXPECTED_REPOSITORY = "kt-git-1/med-manager_v3";
const EXPECTED_DEFAULT_BRANCH = "main";
const EXPECTED_WORKFLOW_NAME = "Android API Production Release";
const EXPECTED_WORKFLOW_PATH = ".github/workflows/android-api-production-release.yml";
const EXPECTED_ENVIRONMENT = "android-api-production";
const EXPECTED_SECRETS = new Set([
  "ANDROID_PRODUCTION_DIRECT_URL",
  "PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS",
  "VERCEL_TOKEN"
]);
const EXPECTED_VARIABLES = new Set([
  "ANDROID_API_PRODUCTION_RELEASE_ENABLED",
  "ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED",
  "VERCEL_ORG_ID",
  "VERCEL_PROJECT_ID"
]);
const FULL_SHA = /^[0-9a-f]{40}$/;
const SAFE_ARM = "safe";
const ARMED = "armed";

export class ControlPlaneError extends Error {}

function assert(condition, message) {
  if (!condition) throw new ControlPlaneError(message);
}

function object(value, label) {
  assert(value && typeof value === "object" && !Array.isArray(value), `${label} must be an object`);
  return value;
}

function exactNames(items, expected, label) {
  assert(Array.isArray(items), `${label} must be an array`);
  const names = items.map((item) => {
    object(item, `${label} row`);
    assert(
      typeof item.name === "string" && /^[A-Z][A-Z0-9_]*$/.test(item.name),
      `${label} name is malformed`
    );
    return item.name;
  });
  assert(new Set(names).size === names.length, `${label} names are duplicated`);
  assert(
    JSON.stringify([...names].sort()) === JSON.stringify([...expected].sort()),
    `${label} names do not match the reviewed set`
  );
  return names;
}

function exactCount(response, rows, label) {
  assert(
    Number.isInteger(response.total_count) && response.total_count >= 0,
    `${label} total_count is malformed`
  );
  assert(response.total_count === rows.length, `${label} response is incomplete or count-drifted`);
  assert(response.total_count <= 100, `${label} response exceeds the bounded single page`);
}

export function validateRepositoryState(repository, branch, expectedMainSha) {
  object(repository, "repository response");
  object(branch, "branch response");
  assert(FULL_SHA.test(expectedMainSha), "expected main SHA must be a lowercase full Git SHA");
  assert(repository.full_name === EXPECTED_REPOSITORY, "repository identity drifted");
  assert(repository.default_branch === EXPECTED_DEFAULT_BRANCH, "default branch must remain main");
  assert(
    repository.archived === false && repository.disabled === false,
    "repository is archived or disabled"
  );
  assert(branch.name === EXPECTED_DEFAULT_BRANCH, "branch response is not main");
  object(branch.commit, "branch commit");
  assert(
    branch.commit.sha === expectedMainSha,
    "live main SHA differs from the reviewed release SHA"
  );
  assert(branch.protected === true, "main branch must be protected before production dispatch");
  return { mainSha: expectedMainSha };
}

export function validateWorkflowState(response) {
  object(response, "workflow response");
  const workflows = response.workflows;
  assert(Array.isArray(workflows), "workflow response must contain an array");
  exactCount(response, workflows, "workflow");
  const matching = workflows.filter((workflow) => workflow.path === EXPECTED_WORKFLOW_PATH);
  assert(
    matching.length === 1,
    "production workflow must be registered exactly once on the default branch"
  );
  const workflow = object(matching[0], "production workflow");
  assert(workflow.name === EXPECTED_WORKFLOW_NAME, "production workflow name drifted");
  assert(workflow.state === "active", "production workflow is not active");
  return { workflowState: workflow.state };
}

export function validateEnvironmentState(environment) {
  object(environment, "environment response");
  assert(environment.name === EXPECTED_ENVIRONMENT, "production environment identity drifted");
  assert(Array.isArray(environment.protection_rules), "environment protection rules are missing");
  const reviewerRules = environment.protection_rules.filter(
    (rule) => rule.type === "required_reviewers"
  );
  assert(reviewerRules.length === 1, "environment must have exactly one required-reviewer rule");
  const rule = object(reviewerRules[0], "required-reviewer rule");
  assert(rule.prevent_self_review === true, "environment must prevent self-review");
  assert(
    Array.isArray(rule.reviewers) && rule.reviewers.length >= 1,
    "environment requires at least one reviewer"
  );
  assert(
    rule.reviewers.every(
      (reviewer) =>
        reviewer &&
        typeof reviewer === "object" &&
        ["User", "Team"].includes(reviewer.type) &&
        reviewer.reviewer &&
        typeof reviewer.reviewer === "object"
    ),
    "environment reviewer rows are malformed"
  );
  const branchPolicy = object(environment.deployment_branch_policy, "environment branch policy");
  assert(
    branchPolicy.protected_branches === true && branchPolicy.custom_branch_policies === false,
    "environment must deploy only from protected branches"
  );
  return { reviewerCount: rule.reviewers.length };
}

export function validateSecretState(response) {
  object(response, "environment secrets response");
  assert(Array.isArray(response.secrets), "environment secrets response must contain an array");
  exactCount(response, response.secrets, "environment secret");
  exactNames(response.secrets, EXPECTED_SECRETS, "environment secret");
  return { secretCount: response.secrets.length };
}

export function validateVariableState(response, armState) {
  object(response, "environment variables response");
  assert(Array.isArray(response.variables), "environment variables response must contain an array");
  exactCount(response, response.variables, "environment variable");
  exactNames(response.variables, EXPECTED_VARIABLES, "environment variable");
  assert(armState === SAFE_ARM || armState === ARMED, "arm state must be safe or armed");
  const values = new Map(
    response.variables.map((item) => {
      assert(typeof item.value === "string", "environment variable value is malformed");
      return [item.name, item.value];
    })
  );
  assert(
    values.get("ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED") === "true",
    "required-reviewer attestation must be true"
  );
  const expectedReleaseEnabled = armState === ARMED ? "true" : "false";
  assert(
    values.get("ANDROID_API_PRODUCTION_RELEASE_ENABLED") === expectedReleaseEnabled,
    `production release arm must be ${expectedReleaseEnabled}`
  );
  for (const name of ["VERCEL_ORG_ID", "VERCEL_PROJECT_ID"]) {
    const value = values.get(name);
    assert(
      typeof value === "string" &&
        value === value.trim() &&
        value.length >= 3 &&
        value.length <= 160,
      `${name} is missing or malformed`
    );
  }
  return { variableCount: response.variables.length, armState };
}

export function validateControlPlane(responses, { expectedMainSha, armState = SAFE_ARM }) {
  object(responses, "control-plane responses");
  const repository = validateRepositoryState(
    responses.repository,
    responses.branch,
    expectedMainSha
  );
  const workflow = validateWorkflowState(responses.workflows);
  const environment = validateEnvironmentState(responses.environment);
  const secrets = validateSecretState(responses.secrets);
  const variables = validateVariableState(responses.variables, armState);
  return { ...repository, ...workflow, ...environment, ...secrets, ...variables };
}

async function requestJson(path, token) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);
  let response;
  try {
    response = await fetch(`https://api.github.com${path}`, {
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${token}`,
        "User-Agent": "med-manager-android-production-control-plane",
        "X-GitHub-Api-Version": "2022-11-28"
      },
      redirect: "error",
      signal: controller.signal
    });
  } catch (error) {
    throw new ControlPlaneError(`GitHub API request failed for ${path.split("?")[0]}`);
  } finally {
    clearTimeout(timeout);
  }
  assert(
    response.status === 200,
    `GitHub API rejected ${path.split("?")[0]} with status ${response.status}`
  );
  const contentType = response.headers.get("content-type") ?? "";
  assert(
    contentType.toLowerCase().startsWith("application/json"),
    "GitHub API response is not JSON"
  );
  try {
    return await response.json();
  } catch {
    throw new ControlPlaneError("GitHub API response body is malformed JSON");
  }
}

export async function fetchLiveControlPlane(token) {
  assert(
    typeof token === "string" && token.length >= 20 && token.length <= 512 && !/\s/.test(token),
    "GITHUB_CONTROL_PLANE_TOKEN is missing or malformed"
  );
  const encodedEnvironment = encodeURIComponent(EXPECTED_ENVIRONMENT);
  const paths = {
    repository: `/repos/${EXPECTED_REPOSITORY}`,
    branch: `/repos/${EXPECTED_REPOSITORY}/branches/${EXPECTED_DEFAULT_BRANCH}`,
    workflows: `/repos/${EXPECTED_REPOSITORY}/actions/workflows?per_page=100`,
    environment: `/repos/${EXPECTED_REPOSITORY}/environments/${encodedEnvironment}`,
    secrets: `/repos/${EXPECTED_REPOSITORY}/environments/${encodedEnvironment}/secrets?per_page=100`,
    variables: `/repos/${EXPECTED_REPOSITORY}/environments/${encodedEnvironment}/variables?per_page=100`
  };
  const entries = await Promise.all(
    Object.entries(paths).map(async ([key, path]) => [key, await requestJson(path, token)])
  );
  return Object.fromEntries(entries);
}

function parseArguments(argv) {
  const arguments_ = { armState: SAFE_ARM, expectedMainSha: "" };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--expected-main-sha") arguments_.expectedMainSha = argv[++index] ?? "";
    else if (argument === "--arm-state") arguments_.armState = argv[++index] ?? "";
    else throw new ControlPlaneError(`unrecognized argument: ${argument}`);
  }
  return arguments_;
}

async function main() {
  const arguments_ = parseArguments(process.argv.slice(2));
  const responses = await fetchLiveControlPlane(process.env.GITHUB_CONTROL_PLANE_TOKEN ?? "");
  const result = validateControlPlane(responses, arguments_);
  console.log(
    `Android API production control plane verified: repository=${EXPECTED_REPOSITORY} ` +
      `main=${result.mainSha.slice(0, 12)} workflow=${result.workflowState} ` +
      `environment=protected reviewers=${result.reviewerCount} secrets=${result.secretCount} ` +
      `variables=${result.variableCount} arm=${result.armState}`
  );
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    const message =
      error instanceof ControlPlaneError
        ? error.message
        : "unexpected control-plane verification failure";
    console.error(`Android API production control plane failed: ${message}`);
    process.exitCode = 1;
  });
}
