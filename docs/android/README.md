# Android Port Documentation

This directory is the source of truth for the Android port. Android work is isolated on `android-dev` until the merge-to-main gate passes.

## Current baseline

- Reference product: `main@1cf8aef`
- Android baseline merge: `android-dev@1b38208`
- Baseline date: 2026-07-14
- Current action: execute the Gate I physical-device and Play Internal/Closed release runbook; C42–C44 complete the current-runtime UI-101 light exceptional states and UI-102 dose-detail matrix, while live Firebase, physical devices and release-owner signing remain external prerequisites

## Authority order

When sources disagree, use this order:

1. Pinned backend behavior and API tests
2. Pinned shipping iOS behavior and iOS tests
3. Pinned `Localizable.strings` for Japanese copy
4. Product specs under `specs/`
5. Android contracts, master plan and parity matrix in this directory
6. Existing Android behavior and older phase notes

An Android shortcut never overrides a backend rule or an intentional iOS product behavior.

## Required documents

- [Master development plan](./android-port-master-plan.md)
- [Pinned source baseline and change control](./source-baseline.md)
- [API and session contracts](./api-contracts.md)
- [UI and screen contracts](./ui-screen-contracts.md)
- [Ordered execution backlog](./execution-backlog.md)
- [Parity requirements matrix](./parity-requirements.md)
- [UI fidelity specification](./ui-fidelity-spec.md)
- [Current gap audit](./current-gap-audit.md)
- [Phase 0 foundation](./phase-0-foundation.md)
- [Phase 1 session/API notes](./phase-1-session-api.md)
- [Phase 2 patient-mode notes](./phase-2-patient-mode.md)
- [Phase 3 caregiver-mode notes](./phase-3-caregiver-mode.md)
- [Phase 4 analytics and privacy notes](./phase-4-analytics-privacy.md)
- [Play release runbook](./play-release-runbook.md)
- [Play Console declaration worksheet](./play-console-declarations.md)
- [Japanese Play store listing and screenshot handoff](./play-store-listing-ja.md)
- [Production App Links evidence and completion steps](./evidence/i03-app-links-20260715.md)
- [Android launcher icon render evidence](./evidence/i05-launcher-20260715/README.md)

## Status vocabulary

- `NOT_STARTED`: no meaningful Android implementation.
- `SCAFFOLDED`: route/model/UI shell exists but is not contract-complete.
- `PARTIAL`: a real path works, but required states or parity items are missing.
- `IMPLEMENTED`: code and automated tests satisfy the requirement.
- `VERIFIED`: implemented and visually/behaviorally verified on emulator and physical device.
- `RECHECK_REQUIRED`: it was previously implemented, but the pinned iOS/API behavior changed or the previous evidence is no longer sufficient.
- `BLOCKED`: external input or unavailable environment prevents progress.

Only `VERIFIED` counts as complete for a release phase.

## Required work-unit header

Every Android implementation work unit records:

- baseline SHA
- parity IDs
- iOS/API/test references
- contract and required UI states
- automated and device evidence
- intentional Android differences

If the source behavior changes, follow `source-baseline.md`; do not silently preserve old Android behavior.
