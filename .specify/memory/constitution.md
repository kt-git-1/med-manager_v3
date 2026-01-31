<!--
Sync Impact Report
- Version change: N/A (template) -> 1.0.0
- Modified principles: N/A (new constitution)
- Added sections: Purpose & Scope, Definition of Done, Change Management & Compatibility, Tools & CI Gates
- Removed sections: None
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
- Follow-up TODOs: None
-->
# Med Manager v3 Constitution

## Core Principles

### I. Code Quality & Clarity
We prioritize readability, maintainability, and safety over cleverness. Functions and
responsibilities stay small, inputs/outputs are explicit, and implicit side effects are
avoided. Formatting and linting are enforced in CI to reduce style disputes in PRs.
Type safety is mandatory (TypeScript strict when possible; Swift uses safe typing).
Use of `any` or unsafe casts is prohibited unless documented with rationale and impact.
Expected failures (validation, auth, missing resources) are modeled as domain outcomes;
unexpected failures must have clear boundaries and be observable for investigation.
Secrets and PII must never appear in logs; use structured logs with correlation IDs when
needed.

### II. Test Strategy & Reliability
Tests must be deterministic, isolated, and independent of execution order. CI must not
call external services; use fakes/mocks/fixtures. Bug fixes always add regression tests.
Prefer black-box tests that validate observable behavior over implementation details.
Test names must describe expected behavior in natural language (or equivalent).
Maintain a practical test pyramid:
- Unit: domain logic, validation, pure functions
- Integration: DB/IO boundaries, repositories, critical workflows
- Contract: API request/response contracts plus authn/authz behavior
- iOS UI: smoke-level coverage of critical flows
Coverage targets are not mandatory globally, but high-risk areas (authn/authz,
medication safety, data integrity) require deep test coverage.

### III. UX Standards (Patient/Family App)
Use shared components and patterns to reduce learning cost and improve consistency.
Ensure accessibility: Dynamic Type, sufficient contrast, VoiceOver labels, and focus
order. Error UX must explain what happened and provide next actions (retry, settings
guidance). Avoid blank loading states; use progress or skeletons and prevent double
submissions. For slow/unstable networks, show safe cached data when possible and provide
clear retry and failure reasons. Prepare for i18n by avoiding hardcoded strings.

### IV. Performance Guardrails
Define performance budgets where feasible and detect regressions via CI or measurement.
Web API: maintain p95 latency targets, avoid N+1 queries, and design indexes for critical
queries. iOS: prioritize perceived launch and navigation speed, smooth scrolling, and
avoid main-thread blocking. Optimize after measuring; track p95/p99 and error rates.
Changes suspected to impact performance must include before/after metrics or rationale
in the PR.

### V. Security & Privacy
Enforce least privilege across APIs, DB access, and internal modules. Health and
medication data is sensitive: collect and retain only what is necessary; protect in
transit with TLS; protect at rest using platform-standard mechanisms. Authentication and
authorization are mandatory with deny-by-default behavior. Validate inputs and render
outputs safely following common vulnerability guidance (e.g., OWASP). Secrets must never
be committed; rotations are assumed for leaks. Logs must mask PII/tokens and capture
auditable events. Dependencies must be updated regularly; critical known vulnerabilities
block CI.

### VI. Documentation & Traceability
Specs in `specs/` are the single source of truth for behavior. Every change must link to
a spec, acceptance criteria, and tests, or explicitly document the exception with
rationale. Record important design decisions as ADRs with context, decision, alternatives,
and impact. Each module must document how to run and test, updated in the same PR as the
change. Operational procedures (migrations, incident response, feature flags, rollback)
must be captured in runbooks.

### [PRINCIPLE_2_NAME]
<!-- Example: II. CLI Interface -->
[PRINCIPLE_2_DESCRIPTION]
<!-- Example: Every library exposes functionality via CLI; Text in/out protocol: stdin/args → stdout, errors → stderr; Support JSON + human-readable formats -->

### [PRINCIPLE_3_NAME]
<!-- Example: III. Test-First (NON-NEGOTIABLE) -->
[PRINCIPLE_3_DESCRIPTION]
<!-- Example: TDD mandatory: Tests written → User approved → Tests fail → Then implement; Red-Green-Refactor cycle strictly enforced -->

### [PRINCIPLE_4_NAME]
<!-- Example: IV. Integration Testing -->
[PRINCIPLE_4_DESCRIPTION]
<!-- Example: Focus areas requiring integration tests: New library contract tests, Contract changes, Inter-service communication, Shared schemas -->

### [PRINCIPLE_5_NAME]
<!-- Example: V. Observability, VI. Versioning & Breaking Changes, VII. Simplicity -->
[PRINCIPLE_5_DESCRIPTION]
<!-- Example: Text I/O ensures debuggability; Structured logging required; Or: MAJOR.MINOR.BUILD format; Or: Start simple, YAGNI principles -->

## Purpose & Scope

- This repository is developed using Spec-Driven Development (SDD). The spec is the
  single source of truth.
- Target modules are `web-api` and `iOS`.
- All changes must be tied to a spec, acceptance criteria, and tests. If an exception is
  necessary, document the reason, scope, and risk in the PR.

## Definition of Done

A change is considered complete only when all of the following are true:
- The spec is updated for behavior changes and acceptance criteria are unambiguous.
- Automated tests are added/updated and all CI tests pass.
- Lint, format, and type-check pass according to project standards.
- Error handling and user-facing copy in critical flows are verified.
- Security/privacy checks are completed (authorization, log redaction, etc.).
- Required docs are updated in the same PR (specs/README/runbook/ADR as applicable).

## Change Management & Compatibility

- Avoid breaking changes. If required, document migration plans, backward compatibility,
  and deprecation timelines in the spec.
- Database migrations must prioritize safety and be tested; include rollback or staged
  rollout when feasible.
- High-risk changes should prefer feature flags and keep safe defaults.

## Tools & CI Gates

- CI is the source of truth: changes that fail lint/format/type-check/tests do not merge.
- Auto-format on save is recommended; avoid format-only diffs without purpose.
- Build and run scripts must remain consistent and documented.

## Governance

- This constitution supersedes other development guidance in this repository.
- Amendments require a PR that updates this file and any dependent templates; include a
  summary of changes and impact.
- Versioning follows semantic versioning:
  - MAJOR: backward-incompatible governance or principle changes/removals
  - MINOR: new principles or materially expanded guidance
  - PATCH: clarifications or non-semantic refinements
- Reviewers must verify compliance for every PR. Deviations require an explicit,
  time-bounded exception with rationale, scope, and mitigations documented in the PR.

**Version**: 1.0.0 | **Ratified**: 2026-01-31 | **Last Amended**: 2026-01-31
