# Feature Specification: Native order, line item and payment projection

## Goal

Prepare paid receipts/orders for native Room ownership without changing the payment or return flow.

## Structured projection

`order_projection` stores receipt-level identity, shift/employee linkage, payment method, totals, order type/label, delivery fee, source/web-order identifiers, timestamps and return state.

`order_line_projection` stores each purchased line item separately with product/category, quantity, price/cost, discount fields and comment.

`payment_projection` stores each split/cash/card payment part separately with amount, cash given and change.

Original JSON payloads remain stored on every projection row for compatibility and forensic parity.

## Safety boundary

- Existing payment flow remains authoritative.
- Split payments remain unchanged.
- Receipt numbering remains unchanged.
- Stock consumption and return restoration remain unchanged.
- Returns and cash refund movements remain unchanged.
- Receipt printing and auto-print remain unchanged.
- Loyalty settlement remains unchanged.
- Projection failure cannot roll back an already successful local payment write.

## Database migration

Room schema moves from v4 to v5 using explicit migration 4→5. No destructive fallback.

## Acceptance

- `orders` shadow writes refresh orders, lines and payment parts transactionally.
- Removing `orders` clears all three projection tables.
- Parity diagnostics compare order identities/counts plus normalized line/payment identities.
- Room remains non-authoritative.
- Node tests, lint and debug build pass.
- Physical cash/card/split/payment/return/restart tests are required before authoritative cutover.
