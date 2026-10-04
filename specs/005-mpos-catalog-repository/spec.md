# Feature Specification: M POS catalog repository and parity diagnostics

## Goal

Introduce `MPosCatalogRepository` as the native read boundary over structured Room catalog projections and prove that its data matches the legacy mirrored `products` JSON before any authoritative cutover.

## Naming rule

All new code in this feature uses M POS naming. `PrilavokCore` and the `prilavok_` prefix are treated only as temporary compatibility surfaces for the still-bundled parity runtime and backup schema v13.

## Functional requirements

- Add native `MPosCatalogRepository`.
- Read ordered products/categories from Room projection tables.
- Compare Room projections with the mirrored legacy `products` JSON.
- Report product/category counts, missing IDs, extra IDs and field mismatches.
- Expose diagnostics through the existing secure storage bridge action `catalogParity`.
- Add `MPosCore.Storage` as the Android runtime namespace.
- Keep `PrilavokCore.Storage` only as a temporary compatibility alias so unchanged shared JS continues to function.
- Do not switch any POS business read path to Room in this stage.

## Acceptance

- Parity report returns `matches: true` when projection and mirrored source agree.
- Parity report is explicitly non-authoritative.
- New Android-specific runtime code uses `MPosCore`.
- Source parity with shared iPad runtime remains intact.
- Node tests, lint and debug build pass.
- Authoritative catalog cutover stays blocked until physical parity/restart checks are accepted.
