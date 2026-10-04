# Feature Specification: Catalog cutover controller

## Goal

Prepare an explicit, observable catalog source switch without changing production behavior.

The controller supports only safe pre-cutover modes:
- `legacy`: use the existing catalog source only;
- `compare`: keep legacy active while allowing Room parity checks.

A `room` mode is intentionally rejected until physical acceptance is recorded in the migration specs.

## Functional requirements

- Add Android-only `MPosCore.CatalogCutover`.
- Default to `compare`.
- Always report `activeSource: legacy` in this stage.
- Allow on-demand parity comparison and expose the last comparison result.
- Expose a compact health report for diagnostics.
- Reject attempts to set mode to `room`.
- Do not modify `loadAll()`, product editing, payment, stock, inventory, backup or sync behavior.
- Keep all new naming under M POS / MPos.

## Acceptance

- Default mode is `compare`.
- Room cutover is programmatically blocked.
- Legacy remains the active catalog source.
- A parity failure cannot change the active source.
- Source parity, Node tests, lint and debug build pass.
- Physical acceptance is still required before a future spec can introduce a real Room mode.
