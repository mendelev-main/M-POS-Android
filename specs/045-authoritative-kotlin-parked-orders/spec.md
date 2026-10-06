# Spec 045 — Authoritative Kotlin parked-order persistence

## Scope and benefit

Kotlin/Room owns the complete `parked` document. Durable native commit acknowledgements protect parked carts across WebView restarts; native transactions keep headers and lines consistent. Existing parked-order business code, kitchen printing, customer selection, delivery, loyalty and backup v13 remain unchanged.

## Contract

- First access imports current legacy data over stale shadows once, atomically with `mpos_parked_authority_v1` marker and existing header/line indexes. No schema change/destructive migration.
- Retain complete original JSON, modifiers, customer, delivery selection/fee, source/web identifiers/status, printedItems/kitchenPrinted, unknown fields/order and missing/null/empty distinctions. Supporting indexes never reconstruct the business document.
- Document, headers and lines commit together. Missing/non-finite numeric values are normalized only in supporting indexes; original data remains untouched.
- Native commands use existing bounded FIFO. Reject invalid shapes/trailing JSON. Ignore obsolete generic shadow put/remove after ownership; exclude owned keys from startup legacy mirrors.
- JS captures snapshots before waiting and updates secondary legacy cache only after commit. Cache failure cannot undo native commit. Timeout means uncertain commit; existing absolute-snapshot journal handles recovery.
- Preserve actual park/resume/delete sequences. Parking clears cart only after journal writes finish; resume first persists currentOrderSession, then removes parked record, then updates UI. A failure retains journal for replay. CurrentOrderSession remains legacy in this stage: multi-document recovery is not a single SQL transaction.
- Do not change payment/return/stock rules or printing guarantees. Flags preserve existing meaning; a transport call is not evidence of paper output.
- Rollback requires previous routing plus verified current secondary cache or v13 backup, retaining native documents/markers until verified.

## Validation

Nine real Room/native SQLite tests cover first migration, FIFO, absence/null/remove, stale writes, failed initialization/write/delete, full raw preservation/reopen and nested-line rollback. Four executed JS regressions exercise actual park/resume commands, delayed acknowledgement/no early cart clearing, failed resume with journal replay and v13 import/restart despite cache failure. Reviewed-source parity checks remain required. Final comprehensive physical testing is deferred by user.

## Limit and next boundary

The API still snapshots full parked arrays and refreshes all indexes. Individual order commands need a later repository migration. Stabilizing currentOrderSession and criticalStorageJournal native ownership is the next recovery boundary before replacing shared business commands.
