# C110 signed artifact and Play listing binding

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

The signed-release evidence ledger is upgraded to schema v2. In addition to the exact AAB/source/version/certificate/dependency identity, it now records SHA-256 values for:

- the Japanese Play listing;
- the screenshot source map;
- the 512 px store icon;
- the feature graphic;
- all eight phone JPEGs in their required listing order.

`preparePlayReleaseHandoff` receives the repository root and re-hashes those exact files before creating or accepting the three-file AAB/evidence/checksum handoff. A missing `storeListing` object, schema downgrade, field drift, valid-looking hash substitution, missing asset or screenshot reorder therefore fails before the handoff can be used.

## Verification

- Release evidence policy: 2 accepted, 12 rejected; atomic JSON retained.
- Play release handoff: 1 accepted/idempotent, 11 rejected.
- Existing Play listing contract: 1 accepted, 20 rejected.
- Existing screenshot/listing/assets verification: four text blocks, eight screenshots, eight source mappings and five public URLs pass; deterministic renderer bytes still match.

## Boundaries

This binds the current source-controlled store handoff to the exact signed-release ledger. It does not prove the release owner signed the final AAB, that Play received the matching files, that Console preview is identical, that public URLs are deployed, or that declarations were submitted. `RG-009` and `RG-010` remain external and unchecked.
