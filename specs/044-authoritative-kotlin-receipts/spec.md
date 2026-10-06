# Spec 044 — Authoritative Kotlin paid-receipt persistence

## Scope and benefit

Move `orders` document ownership to Kotlin/Room. Reuse existing `order_projection`, `order_line_projection` and `payment_projection` tables with native document/index transaction commits. A failed line/payment insert must not leave a partially persisted receipt. Authorization, payment/return calculations, UI, loyalty retries, availability gate and v13 format remain unchanged.

## Contract

- First access atomically imports the current legacy orders document over any stale shadow with durable `mpos_orders_authority_v1` marker. Later access never reimports secondary cache.
- Persist full original JSON (including original sale, split payments, historical stockConsumption, return/loyalty fields, unknown data and array order). Indexes are supporting data and cannot reconstruct the full document.
- Preserve absent/null/empty-array distinctions. Reject malformed/trailing/non-array JSON without replacing committed data.
- Document, receipt headers, lines and payments commit in one Room transaction. Missing/non-finite numeric index fields use zero only in diagnostic indexes; authoritative financial JSON is unchanged. No destructive schema migration.
- Native status/bootstrap/read/write/remove use existing bounded FIFO. Ignore generic stale put/remove after authority.
- Capture JS write payload before awaiting; await native acknowledgement before cache update. Cache errors cannot undo native commits. A lost acknowledgement is an uncertain commit, recovered through absolute-snapshot journal replay.
- Existing critical journal remains responsible for multi-document payment/return/backup recovery. It is not a single cross-domain SQL transaction.
- The retained JS model supports one full return, blocks repeated returns and stores returnedAt/returnedShiftId/returnAmount on the original receipt. Card refunds remain external terminal actions. Do not invent partial returns, bank acknowledgements or new status semantics.
- Rollback: restore previous routing only with verified fresh compatibility cache or v13 backup; retain native markers/documents until recovery is verified.

## Validation

Ten file-backed native SQLite tests cover bootstrap, FIFO, missing/null/removal, stale mirrors, failed migration/write/delete rollback, raw JSON preservation, reopen, nested line/payment failures and original sale plus return/loyalty preservation.

Executed JS tests cover delayed native commit, immutable split-payment snapshot, payment-journal replay after failed receipt save, actual v13 restore and actual full-return stock/cash update plus duplicate-return prevention. Existing reviewed-source parity remains required. Comprehensive physical testing is deferred by user.

## Limit and next step

The current compatibility API still reads/writes the full orders array and refreshes all supporting indexes. This stage improves persistence ownership and atomic durability, not asymptotic archive performance. A later native receipt repository should write one receipt and its related rows per operation, query paginated history and export/import v13 through an adapter. That requires explicit business-command parity and must not be claimed implemented here.
