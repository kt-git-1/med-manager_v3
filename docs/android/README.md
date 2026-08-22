# Android Port Documentation

This directory is the source of truth for the Android port. Android work is isolated on `android-dev` until the merge-to-main gate passes.

## Current baseline

- Current iOS/API release reference: `staging@e9ec0d3c6b10`; published Production: `main@432b34c064d7`
- Android Phase 0 source: rebaselined through merge `b75dedf`; Phase 1 implementation RC `9ae1fc0`; machine-readable release-gate checkpoint: current implementation checkpoint: C124
- Roadmap update date: 2026-08-23
- Current action: Phase 0/1 are complete; proceed to the owner-controlled Play Organization/signing prerequisites in Phase 2. Store-listing assets remain provisional until the final UI is frozen.

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
- [Pre-Phase 0 product additions](./pre-phase-0-product-additions.md)
- [Play first-release roadmap](./play-first-release-roadmap.md)
- [Pinned source baseline and change control](./source-baseline.md)
- [API and session contracts](./api-contracts.md)
- [UI and screen contracts](./ui-screen-contracts.md)
- [Ordered execution backlog](./execution-backlog.md)
- [Parity requirements matrix](./parity-requirements.md)
- [UI fidelity specification](./ui-fidelity-spec.md)
- [Current gap audit](./current-gap-audit.md)
- [Firebase Analytics verification](./firebase-analytics.md)
- [Staging / Production flavor contract](./environment-flavors.md)
- [Physical-device verification matrix](./physical-device-matrix.md)
- [Staging QA report (2026-08-22)](./staging-qa-2026-08-22.md)
- [Staging RC rebaseline (2026-08-23)](./staging-rc-2026-08-23.md)
- [Play release runbook](./play-release-runbook.md)
- [Play developer account onboarding](./play-developer-account-onboarding.md)
- [Canonical residual release gates](./release-gates.json)
- [Phase 0 foundation](./phase-0-foundation.md)
- [Phase 1 session/API notes](./phase-1-session-api.md)
- [Phase 2 patient-mode notes](./phase-2-patient-mode.md)
- [Phase 3 caregiver-mode notes](./phase-3-caregiver-mode.md)
- [Phase 4 analytics and privacy notes](./phase-4-analytics-privacy.md)
- [Guided caregiver onboarding](./guided-caregiver-onboarding.md)
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
