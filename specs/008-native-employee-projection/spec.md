# Feature Specification: Native employee projection

## Goal

Prepare employee data for native ownership using a structured Room projection and parity diagnostics while preserving all current POS behavior.

## Scope

Mirror the existing `employees` JSON into `employee_projection` with:
- id;
- full name;
- phone;
- role;
- original order;
- original JSON payload;
- update timestamp.

Add `MPosEmployeeRepository` to compare the Room projection with the mirrored legacy source.

## Safety boundary

- Existing employee runtime remains authoritative.
- Employee creation/edit/delete continues through current UI and storage path.
- Admin password rules remain unchanged.
- Shift opening and employee selection remain unchanged.
- Projection failure cannot roll back a successful local employee save.
- New code uses M POS / MPos naming only.

## Database migration

Room schema moves from v2 to v3 with an explicit 2→3 migration. Existing catalog/shadow data must be preserved.

## Acceptance

- v2 installs migrate to v3 without destructive fallback.
- Every mirrored `employees` write refreshes the projection transactionally.
- Removing the `employees` key clears the projection.
- Parity diagnostics report missing/extra/mismatched employee IDs.
- Room remains non-authoritative.
- Node tests, lint and debug build pass.
- Physical employee edit/restart verification remains required before cutover.
