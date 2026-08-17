# C112 generated-ledger to Play handoff integration

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

The C111 ephemeral signed-AAB integration now continues through the production `prepare_handoff` implementation using the exact schema-v2 report returned by `generate_report`.

The integrated path proves:

1. an isolated clean Git tree and disposable signer produce the schema-v2 ledger;
2. the ledger and its exact signed AAB create the version/code/commit-named handoff;
3. the handoff contains exactly the renamed AAB, byte-identical `play-release-evidence.json` and `SHA256SUMS`;
4. re-running the same input accepts the existing handoff idempotently;
5. changing the store icon after ledger generation makes a new handoff fail on `icon512Sha256` mismatch;
6. changing the committed listing still makes ledger generation fail on dirty release input.

The Gradle evidence-policy contract now declares both the generator and handoff implementation as inputs, so hosted CI reruns this bridge whenever either side changes.

## Verification

- Combined release-evidence contract: 4 accepted, 14 rejected.
- Generated-ledger handoff: passed.
- Changed store input after generation: rejected.
- Atomic JSON and idempotent handoff: passed.

## Boundaries

All signing material and artifacts are synthetic and temporary. This proves compatibility between the production generator and handoff code, but it does not supply a release-owner key, production AAB, Organization account, Play upload/app-signing certificate or Console evidence. `RG-005` remains unchecked.
