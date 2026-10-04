# Feature Specification: Native shift and cash movement projection

## Goal

Prepare cash shifts and their cash movements for native Room ownership without changing the current operational shift flow.

## Structured projection

`shift_projection` stores:
- shift id/status;
- employee id/name/phone;
- opened/closed timestamps;
- opening and counted cash;
- source order;
- original shift JSON.

`cash_movement_projection` stores each nested movement separately:
- movement id;
- parent shift id;
- deposit/withdrawal type;
- subtype;
- amount;
- timestamp;
- note;
- original movement JSON.

## Safety boundary

- Existing shift JS remains authoritative.
- Opening/closing shifts remains unchanged.
- Cash drawer calculations remain unchanged.
- Deposit/withdrawal validation remains unchanged.
- Delivery/refund movements remain unchanged.
- Shift printing and Telegram reporting remain unchanged.
- Projection failure never rolls back an already successful local shift write.

## Database migration

Room schema moves from v3 to v4 through explicit migration 3→4. Existing shadow/catalog/employee data must be preserved.

## Acceptance

- No destructive migration.
- `shifts` shadow writes refresh shifts and movements transactionally.
- Removing `shifts` clears both projection tables.
- `MPosShiftRepository` reports shift and movement parity independently.
- Room remains non-authoritative.
- Node/source tests, lint and debug build pass.
- Physical open/deposit/withdraw/close/restart checks remain required before cutover.
