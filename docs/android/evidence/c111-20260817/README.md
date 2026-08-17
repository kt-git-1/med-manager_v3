# C111 signed schema-v2 release evidence integration

**Date:** 2026-08-17

**Product baseline:** published iOS 1.0.6 Build 51, `main@432b34c`

## Result

The C110 store-listing hash logic now passes an end-to-end synthetic integration instead of relying only on pure fixtures. The contract creates an isolated Git repository and disposable PKCS12 key, signs an AAB-shaped ZIP with `jarsigner`, reads its certificate through the production `keytool -printcert -jarfile` path, and calls the production `generate_report` implementation.

The resulting schema-v2 ledger proves, in one execution:

- the full committed source SHA and clean release-input tree;
- production package/version identity;
- the actual and expected single AAB signer identity;
- base-only manifest/DEX/native structure;
- nonempty dependency lock and SDK inventory;
- Japanese listing, source map, icon, feature graphic and ordered eight screenshot hashes.

The same integrated fixture then changes the committed listing input without committing it and confirms generation fails with the dirty-input policy. The disposable private key, certificate, synthetic AAB and repository exist only inside the test temporary directory and are removed on exit.

## Verification

- Release evidence policy: 3 accepted, 13 rejected.
- Signed schema-v2 integration: passed.
- Atomic JSON write/idempotency: passed.
- Existing C110 handoff contract remains 1 accepted/idempotent and 11 rejected.

## Boundaries

This proves the production ledger-generation path with a disposable synthetic signer. It is not a release-owner key, production AAB, Play upload, app-signing identity or Console preview. `RG-005` remains unchecked until the owner-controlled Organization/signing/handoff procedure completes.
