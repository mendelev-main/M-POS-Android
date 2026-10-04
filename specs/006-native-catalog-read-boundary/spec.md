# Feature Specification: Controlled native catalog read boundary

## Goal

Prepare the catalog for a future authoritative Room cutover without changing current POS behavior.

The native repository may expose a catalog snapshot only when its structured Room projection matches the mirrored legacy source. The Android runtime receives a new `MPosCore.Catalog` read contract, but native reads remain disabled by default.

## Functional requirements

- Add `MPosCatalogRepository.snapshot()`.
- Refuse a native snapshot when parity is not confirmed.
- Return products in original catalog order and categories in projection order.
- Expose secure bridge action `catalogSnapshot`.
- Add promise-based request/response handling for M POS native storage calls.
- Expose `MPosCore.Catalog.getNativeSnapshot()`.
- Expose `MPosCore.Catalog.parity()`.
- Keep `MPosCore.Catalog.nativeReadsEnabled=false` by default.
- Do not change `loadAll()`, product editor, payment, inventory or any other business read path in this stage.

## Acceptance

- Native snapshot refuses to return catalog data when parity fails.
- Native snapshot reports `source: room-projection` and `authoritative: false`.
- Default feature flag remains off.
- Shared runtime remains source of truth.
- Source-parity tests, lint and debug build pass.
- Physical acceptance is required before a later spec may turn native reads on.
