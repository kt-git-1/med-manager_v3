import assert from "node:assert/strict";

import {
  ApiNodeRuntimeError,
  readApiNodeRuntime,
  validateApiNodeRuntime
} from "./verify-api-node-runtime.mjs";

const repositoryRoot = new URL("../../", import.meta.url);
const source = await readApiNodeRuntime(repositoryRoot);

function copy(value) {
  return structuredClone(value);
}

function rejected(label, mutate) {
  const fixture = copy(source);
  mutate(fixture);
  assert.throws(() => validateApiNodeRuntime(fixture), ApiNodeRuntimeError, label);
}

const summary = validateApiNodeRuntime(source);
assert.deepEqual(summary, {
  node: "22.x",
  prisma: "7.9.1",
  firebaseAdmin: "14.2.0",
  workflows: 3,
  setups: 5
});

const cases = [
  ["missing package engine", (value) => delete value.packageJson.engines],
  ["broad package engine", (value) => (value.packageJson.engines.node = ">=22")],
  ["lock engine drift", (value) => (value.lock.packages[""].engines.node = ">=20")],
  ["declared Prisma drift", (value) => (value.packageJson.devDependencies.prisma = "^7.3.0")],
  [
    "declared Node types drift",
    (value) => (value.packageJson.devDependencies["@types/node"] = "^25.1.0")
  ],
  [
    "declared Firebase Admin drift",
    (value) => (value.packageJson.dependencies["firebase-admin"] = "^13.10.0")
  ],
  ["Prisma CLI drift", (value) => (value.lock.packages["node_modules/prisma"].version = "7.9.0")],
  [
    "Prisma client drift",
    (value) => (value.lock.packages["node_modules/@prisma/client"].version = "7.9.0")
  ],
  [
    "Prisma adapter drift",
    (value) => (value.lock.packages["node_modules/@prisma/adapter-pg"].version = "7.9.0")
  ],
  [
    "streams helper drift",
    (value) => (value.lock.packages["node_modules/@prisma/streams-local"].version = "0.1.10")
  ],
  [
    "streams engine drift",
    (value) => (value.lock.packages["node_modules/@prisma/streams-local"].engines.node = ">=20")
  ],
  [
    "Node type lock drift",
    (value) => (value.lock.packages["node_modules/@types/node"].version = "25.1.0")
  ],
  [
    "Firebase Admin lock drift",
    (value) => (value.lock.packages["node_modules/firebase-admin"].version = "13.10.0")
  ],
  [
    "API CI Node 20",
    (value) =>
      (value.workflows[".github/workflows/api-ci.yml"] = value.workflows[
        ".github/workflows/api-ci.yml"
      ].replace("node-version: 22", "node-version: 20"))
  ],
  [
    "missing E2E cross-trigger",
    (value) =>
      (value.workflows[".github/workflows/api-ci.yml"] = value.workflows[
        ".github/workflows/api-ci.yml"
      ].replaceAll('      - ".github/workflows/api-e2e.yml"\n', ""))
  ],
  [
    "E2E Node 20",
    (value) =>
      (value.workflows[".github/workflows/api-e2e.yml"] = value.workflows[
        ".github/workflows/api-e2e.yml"
      ].replace("node-version: 22", "node-version: 20"))
  ],
  [
    "production Node 20",
    (value) =>
      (value.workflows[".github/workflows/android-api-production-release.yml"] = value.workflows[
        ".github/workflows/android-api-production-release.yml"
      ].replace("node-version: 22", "node-version: 20"))
  ],
  [
    "extra setup",
    (value) => (value.workflows[".github/workflows/api-e2e.yml"] += "\nnode-version: 22\n")
  ],
  ["missing workflow", (value) => delete value.workflows[".github/workflows/api-e2e.yml"]]
];

for (const [label, mutate] of cases) rejected(label, mutate);

console.log(`API Node runtime contract passed: accepted=1 rejected=${cases.length}`);
