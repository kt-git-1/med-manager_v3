# Medication Regimen (001)

This directory contains the SDD artifacts for feature 001.

## Documents

- Spec: `spec.md`
- Plan: `plan.md`
- Tasks: `tasks.md`
- Research: `research.md`
- Data model: `data-model.md`
- Contracts: `contracts/openapi.yaml`
- Quickstart: `quickstart.md`
- Domain policy: `../000-domain-policy/spec.md`

## API Error Matrix (summary)

| Status | When |
| --- | --- |
| 401 | Missing/invalid auth token |
| 403 | Forbidden for role (patient write attempts) |
| 404 | Access to other patient data is masked |
| 409 | Update conflict (optimistic concurrency) |
| 422 | Validation errors (times/start/end/daysOfWeek) |

## Domain Policy Linkage

All schedule generation and boundary rules follow `../000-domain-policy/spec.md`.
