# Feature Specification: Native catalog projection

## Goal

Create structured Room tables for products and categories as a non-authoritative projection of the existing `products` storage payload.

This prepares a native catalog repository and future Compose UI without changing what the POS reads today.

## Safety boundary

- Existing `PrilavokCore.Storage` remains authoritative.
- The POS continues reading products/categories from the existing runtime state.
- Projection failures never roll back an already successful WebView/local storage write.
- Backup schema v13 remains unchanged.
- Shared iPad business modules remain untouched.

## Native projection

`product_projection` stores:
- product id;
- name;
- normalized category;
- product type;
- original array order;
- full original product JSON;
- projection timestamp.

`category_projection` stores:
- category name;
- derived order of first appearance;
- product count;
- projection timestamp.

Blank product categories are normalized exactly like the current POS UI to `Без категории`.

## Database migration

Room schema moves from version 1 to version 2 with an explicit 1→2 migration. Existing `legacy_storage_shadow` data must be preserved.

## Acceptance

- Existing v1 Room installs migrate to v2 without destructive fallback.
- Every successful mirrored `products` write refreshes both projection tables transactionally.
- Removing the `products` key clears both projections.
- Storage diagnostics report shadow row count, product projection count and category projection count.
- Room remains explicitly non-authoritative.
- Node/source-parity tests, lint and debug build pass.
