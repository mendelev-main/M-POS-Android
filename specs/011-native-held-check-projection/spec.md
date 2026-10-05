# Feature Specification: Native held-check projection

## Goal

Prepare held/parked checks for native Room ownership without changing their operational behavior.

## Structured projection

Store held check identity, totals, label/type, delivery fee, comment, web-order state, employee, customer identity, creation time and kitchen-print state. Store each held line separately with product, category, quantity, price and comment. Preserve full original JSON payloads.

## Safety boundary

- Existing held-check runtime remains authoritative.
- Park, resume and delete behavior remain unchanged.
- Restoring a held check into the current cart remains unchanged.
- Customer/loyalty restoration remains unchanged.
- Kitchen delta printing and printedItems state remain unchanged.
- Projection failure cannot roll back a successful local critical-storage commit.

## Database migration

Room schema moves from v5 to v6 using explicit migration 5→6. No destructive fallback.

## Acceptance

- `parked` shadow writes refresh held checks and lines transactionally.
- Removing `parked` clears both projection tables.
- `MPosParkedOrderRepository` reports parity.
- Room remains non-authoritative.
- Node tests, lint and debug build pass.
- Physical park/resume/delete/restart/kitchen-print checks remain required before cutover.
