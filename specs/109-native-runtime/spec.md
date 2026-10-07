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
- [x] Native workspace category/folder/Back/edit transition decisions.
- [ ] Native session/shift selection, remaining navigation and complete bootstrap state orchestration.
- [ ] Domain read models replacing DOM screen extraction.
- [ ] Direct native command dispatch replacing mounted JS handlers.
- [ ] Post-commit and recovery orchestration without JS lifecycle.
- [ ] Remaining settings/authentication/import lifecycle.
- [ ] Remove WebView and legacy bridges after parity verification.

## Verification of production session increment

530/530 JS tests, 422/422 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. Shared fixtures exercise the actual reviewed restore block and Kotlin projection; paid split restart, invalid draft warnings, bridge rollback, owned Room data/conflicts and concurrent multi-document snapshots are covered. No local APK assembly; physical acceptance remains pending in 110.


## Workspace route authority increment

MPosWorkspaceRouteEngine now decides open category, open normalized folder, Back precedence and edit-mode toggles. It reuses the reviewed Kotlin folder normalization from 082. Production native-navigation routes these calls through the existing storage queue; native workspace captures category/folder/edit promises before releasing its action lock. No cart, payment, shift, stock or stored navigation writes occur.

Back preserves exact precedence: close the modal folder first; otherwise clear a legacy inline folder without leaving its category/edit mode; otherwise return to the root and reset search/edit mode. Folder opening does not invent IDs or permissions. Edit-mode entry retains the existing delayed drag initialization. Search filtering, tab navigation and rendering remain JS responsibilities for now.

View requests are FIFO. Each reply is checked against the current route, tab/payment page, query, folder modal, mounted modal identity and navigation data. Stale replies cannot override a newer view. A failed/unsupported pure read falls back to the reviewed function only if the view is still current. MPosNativeWorkspaceRouteEnabled=false provides independent rollback without changing native persisted navigation authority.

Shared fixtures compare actual reviewed navigation functions and the Kotlin transition engine. Integration tests cover delayed/stale replies, order-data retention, malformed replies, FIFO category/Back and explicit rollback. This is another part of 109, not an additional completed task or removal of WebView.

## Verification of workspace route increment

536/536 JS tests and 425/425 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. No APK assembly; physical route/keyboard/drag checks are documented for 110. Overall engineering completion remains 107/110.
