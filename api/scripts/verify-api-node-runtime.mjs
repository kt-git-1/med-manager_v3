import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const EXPECTED_NODE = "22.x";
const EXPECTED_PRISMA = "7.9.1";
const EXPECTED_STREAMS = "0.1.11";
const EXPECTED_NODE_TYPES = "22.20.1";
const EXPECTED_FIREBASE_ADMIN = "14.2.0";
const ACTION_PINS = {
  checkout: "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
  setupNode: "actions/setup-node@820762786026740c76f36085b0efc47a31fe5020",
  uploadArtifact: "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"
};
const WORKFLOW_NODE_COUNTS = new Map([
  [".github/workflows/api-ci.yml", 3],
  [".github/workflows/api-e2e.yml", 1],
  [".github/workflows/android-api-production-release.yml", 1]
]);
const WORKFLOW_ACTION_COUNTS = new Map([
  [".github/workflows/api-ci.yml", { checkout: 3, setupNode: 3, uploadArtifact: 0 }],
  [".github/workflows/api-e2e.yml", { checkout: 1, setupNode: 1, uploadArtifact: 2 }],
  [
    ".github/workflows/android-api-production-release.yml",
    { checkout: 1, setupNode: 1, uploadArtifact: 1 }
  ]
]);

export class ApiNodeRuntimeError extends Error {}

function requireCondition(condition, message) {
  if (!condition) throw new ApiNodeRuntimeError(message);
}

function exactKeys(value, expected, label) {
  requireCondition(
    value && typeof value === "object" && !Array.isArray(value),
    `${label} must be an object`
  );
  requireCondition(
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...expected].sort()),
    `${label} fields drifted`
  );
}

function lockedPackage(lock, name) {
  const value = lock?.packages?.[`node_modules/${name}`];
  requireCondition(value && typeof value === "object", `Lock is missing ${name}`);
  return value;
}

export function validateApiNodeRuntime({ packageJson, lock, workflows }) {
  exactKeys(packageJson.engines, ["node"], "package engines");
  requireCondition(
    packageJson.engines.node === EXPECTED_NODE,
    "API Node engine must be exactly 22.x"
  );
  requireCondition(
    lock?.packages?.[""]?.engines?.node === EXPECTED_NODE,
    "Root lock Node engine must match package.json"
  );
  requireCondition(
    packageJson.devDependencies?.prisma === `^${EXPECTED_PRISMA}` &&
      packageJson.dependencies?.["@prisma/client"] === `^${EXPECTED_PRISMA}` &&
      packageJson.dependencies?.["@prisma/adapter-pg"] === `^${EXPECTED_PRISMA}`,
    "Declared Prisma package ranges must match the reviewed baseline"
  );
  requireCondition(
    packageJson.devDependencies?.["@types/node"] === `^${EXPECTED_NODE_TYPES}`,
    "Node type range must match the Node 22 baseline"
  );
  requireCondition(
    packageJson.dependencies?.["firebase-admin"] === `^${EXPECTED_FIREBASE_ADMIN}`,
    "Firebase Admin range must match the Node 22 baseline"
  );

  for (const name of ["prisma", "@prisma/client", "@prisma/adapter-pg"]) {
    requireCondition(
      lockedPackage(lock, name).version === EXPECTED_PRISMA,
      `${name} must resolve to ${EXPECTED_PRISMA}`
    );
  }
  const streams = lockedPackage(lock, "@prisma/streams-local");
  requireCondition(streams.version === EXPECTED_STREAMS, "Prisma streams helper version drifted");
  requireCondition(
    streams.engines?.node === ">=22.0.0",
    "Prisma streams helper must retain its Node 22 floor"
  );
  requireCondition(
    lockedPackage(lock, "@types/node").version === EXPECTED_NODE_TYPES,
    "Node type lock must match the Node 22 baseline"
  );
  requireCondition(
    lockedPackage(lock, "firebase-admin").version === EXPECTED_FIREBASE_ADMIN,
    "Firebase Admin lock must match the reviewed baseline"
  );

  exactKeys(workflows, WORKFLOW_NODE_COUNTS.keys(), "runtime workflows");
  let setupCount = 0;
  let actionCount = 0;
  for (const [path, expectedCount] of WORKFLOW_NODE_COUNTS) {
    const text = workflows[path];
    requireCondition(typeof text === "string" && text.length > 0, `Workflow is empty: ${path}`);
    const versions = [...text.matchAll(/node-version:\s*([^\s#]+)/g)].map((match) => match[1]);
    requireCondition(versions.length === expectedCount, `Node setup count drifted: ${path}`);
    requireCondition(
      versions.every((version) => version === "22"),
      `Node major drifted: ${path}`
    );
    requireCondition(
      text.includes("permissions:\n  contents: read") && !/^\s+[a-z-]+:\s*write\s*$/m.test(text),
      `Workflow permissions must remain contents-read-only: ${path}`
    );
    const uses = [...text.matchAll(/uses:\s*([^\s#]+)/g)].map((match) => match[1]);
    const expectedActions = WORKFLOW_ACTION_COUNTS.get(path);
    requireCondition(expectedActions, `Action baseline is missing: ${path}`);
    const expectedUses = Object.entries(expectedActions).flatMap(([name, count]) =>
      Array(count).fill(ACTION_PINS[name])
    );
    requireCondition(
      JSON.stringify(uses.sort()) === JSON.stringify(expectedUses.sort()),
      `Workflow action pins or counts drifted: ${path}`
    );
    requireCondition(
      text.split("persist-credentials: false").length - 1 === expectedActions.checkout,
      `Checkout credentials must never persist: ${path}`
    );
    if (path === ".github/workflows/api-ci.yml") {
      requireCondition(
        text.split('".github/workflows/api-e2e.yml"').length - 1 === 2,
        "API CI must run for API E2E workflow drift"
      );
    }
    setupCount += versions.length;
    actionCount += uses.length;
  }
  return {
    node: EXPECTED_NODE,
    prisma: EXPECTED_PRISMA,
    firebaseAdmin: EXPECTED_FIREBASE_ADMIN,
    workflows: workflowsCount(workflows),
    setups: setupCount,
    actions: actionCount
  };
}

function workflowsCount(workflows) {
  return Object.keys(workflows).length;
}

export async function readApiNodeRuntime(repositoryRoot) {
  const packageJson = JSON.parse(
    await readFile(new URL("api/package.json", repositoryRoot), "utf8")
  );
  const lock = JSON.parse(await readFile(new URL("api/package-lock.json", repositoryRoot), "utf8"));
  const workflows = {};
  for (const path of WORKFLOW_NODE_COUNTS.keys()) {
    workflows[path] = await readFile(new URL(path, repositoryRoot), "utf8");
  }
  return { packageJson, lock, workflows };
}

async function main() {
  const repositoryRoot = new URL("../../", import.meta.url);
  const summary = validateApiNodeRuntime(await readApiNodeRuntime(repositoryRoot));
  console.log(
    `API Node runtime verified: node=${summary.node} prisma=${summary.prisma} ` +
      `firebaseAdmin=${summary.firebaseAdmin} ` +
      `workflows=${summary.workflows} setups=${summary.setups} actions=${summary.actions}`
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    if (error instanceof ApiNodeRuntimeError || error instanceof SyntaxError) {
      console.error(`API Node runtime rejected: ${error.message}`);
    } else {
      console.error("API Node runtime verification failed safely.");
    }
    process.exitCode = 1;
  });
}
