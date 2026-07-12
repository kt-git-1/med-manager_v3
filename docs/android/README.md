# Android Port Documentation

This directory is the source of truth for the Android port. Read and update these documents before changing implementation scope.

## Authority order

When sources disagree, use this order:

1. Current backend behavior and API tests
2. Current shipping iOS behavior and iOS tests
3. Product specs under `specs/`
4. Android master plan and parity matrix in this directory
5. Older phase notes

An Android shortcut never overrides a backend rule or an intentional iOS product behavior.

## Required documents

- [Master development plan](./android-port-master-plan.md)
- [Parity requirements matrix](./parity-requirements.md)
- [UI fidelity specification](./ui-fidelity-spec.md)
- [Current gap audit](./current-gap-audit.md)
- [Phase 0 foundation](./phase-0-foundation.md)
- [Phase 1 session/API notes](./phase-1-session-api.md)
- [Phase 2 patient-mode notes](./phase-2-patient-mode.md)

## Status vocabulary

- `NOT_STARTED`: no meaningful Android implementation.
- `SCAFFOLDED`: route/model/UI shell exists but is not contract-complete.
- `PARTIAL`: a real path works, but required states or parity items are missing.
- `IMPLEMENTED`: code and automated tests satisfy the requirement.
- `VERIFIED`: implemented and visually/behaviorally verified on emulator and physical device.
- `BLOCKED`: external input or unavailable environment prevents progress.

Only `VERIFIED` counts as complete for a release phase.
