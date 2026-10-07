# 109 — Native runtime authority

Status: in_progress. Engineering completion remains 107/110.

## Scope and sequence

1. Read coherent Room documents without DOM, writes, initialization or invented defaults.
2. Replace JS session/bootstrap/navigation with native state and explicit domain authority checks.
3. Build screen models from domain repositories instead of mounted HTML.
4. Dispatch native actions directly to command repositories; preserve roles, recovery gates and persisted-before-effects ordering.
5. Replace post-commit orchestration for printing, loyalty, WEB and availability; availability retry only after the next saved payment, no automatic print retry.
6. Replace remaining settings/authentication, import/export and lifecycle handlers without changing backup v13 or exposing credentials.
7. Remove active WebView, JavascriptInterface and DOM adapters only after all preceding boundaries pass parity tests.

## Current increment

MPosRuntimeSnapshot reads an explicitly requested set of raw Room documents in one transaction. Missing documents stay absent; unknown fields and original bytes are preserved. It does not initialize authority or repair corrupt JSON. It is now used by the production native session restore repository; full JS runtime replacement is still pending.

## Acceptance

Automated: exact payload retention, missing versus stored JSON null, read-only behavior, invalid key rejection and multi-document retrieval. Concurrent atomic multi-document writers/readers are covered by a Room test. Subsequent native bootstrap must additionally verify per-domain authority and critical recovery journals before allowing mutations.

Physical acceptance stays in 110: offline startup, employee/shift restore, payment and refund, restart, import/export, all screens and performance. Do not mark 109 done while a hidden WebView still constructs models or dispatches actions.

## Verification of bootstrap reader increment

525/525 JS tests and 415/415 JVM tests passed (no failures/errors/skips); lint: 0 errors, 15 existing warnings. No APK assembly and no physical tablet acceptance. At that first increment (2d89c02), the reader was not connected to production startup; the following increment connects it for current-order restoration.


## Session restoration authority increment

The current-order load block now awaits MPosSessionRestoreEngine via the storage queue. Kotlin projects cart record filtering, customer defaults/extensions, delivery, labels/comments, WEB identity, loyalty metadata and prior kitchen-print marks. It is read-only and has no print/network/payment trigger. The existing split-draft validator runs afterward with the restored cart total, preserving paid parts and invalid-draft warnings.

Rollback: MPosNativeSessionRestoreEnabled=false or a failed/unsupported native read selects the byte-preserved reviewed restoration block. Unusual legacy coercions (non-finite fees, non-string customer identity) intentionally retain that path. No Room schema, v13 shape, authority initialization or write semantics change. The source hash check strips only the exact reviewed native hook and still verifies all other original HTML bytes.

Navigation and full bootstrap recovery remain pending. This increment removes session field projection from active JS authority for supported saved sessions; it does not remove WebView or JS lifecycle orchestration.


The production session read now uses MPosSessionRestoreRepository: it checks existing current-session authority and reads MPosRuntimeSnapshot within one transaction. A missing or changed document rejects native projection and retains the reviewed compatibility path; no authority is created implicitly. Tests cover owned/unowned documents, newer/removal conflicts, preservation of stored paid metadata and concurrent paired snapshot generations. Critical journal replay and navigation remain existing runtime responsibilities.


## Internal checklist (does not add engineering task IDs)

- [x] Transactional read-only document snapshot and concurrent consistency test.
- [x] Production current-order field projection from owned Room data, with reviewed rollback.
- [ ] Native session/shift selection, navigation and complete bootstrap state orchestration.
- [ ] Domain read models replacing DOM screen extraction.
- [ ] Direct native command dispatch replacing mounted JS handlers.
- [ ] Post-commit and recovery orchestration without JS lifecycle.
- [ ] Remaining settings/authentication/import lifecycle.
- [ ] Remove WebView and legacy bridges after parity verification.

## Verification of production session increment

530/530 JS tests, 422/422 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. Shared fixtures exercise the actual reviewed restore block and Kotlin projection; paid split restart, invalid draft warnings, bridge rollback, owned Room data/conflicts and concurrent multi-document snapshots are covered. No local APK assembly; physical acceptance remains pending in 110.
