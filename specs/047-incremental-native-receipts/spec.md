# Spec 047 — Incremental native receipts and paginated history

## Scope

Canonical orders arrays now use full per-receipt JSON in existing order_projection rows as authority, with related line/payment rows. Add native metadata mpos_orders_rows_v1 (mode/revision), atomic lazy migration from the previous owned full document, incremental reconciliation, revision-checked individual upsert and paged history. Room schema stays 12; business shape/v13 stays unchanged.

## Persistence contract

- Canonical arrays require distinct nonblank trimmed string IDs. Each row retains the entire object, including original prices, modifiers, split payments, stock consumption, returns, loyalty and unknown fields; sortIndex retains compatible array order.
- Noncanonical arrays (duplicate/missing/non-string IDs or non-object values) stay in compatible full-document mode. Null/absence remain distinct. Never silently drop incompatible records to adopt row ownership.
- Full snapshot writes compare current payload/position and update only changed receipts and their line/payment groups; removed IDs are deleted. Unchanged receipts are untouched, including replay. Reordering may update more rows.
- Full-read/v13 export reconstructs canonical arrays in original order. JSON values/shapes are preserved; serialization whitespace need not match. Primary full document is removed only in the transaction that establishes row metadata/data.
- Old native ownership migrates lazily from Room document, not stale localStorage. Failure rolls back document/index/metadata together; retry is allowed. Generic legacy shadow writes remain ignored after ownership.
- Individual orderUpsert requires expectedRevision; reject stale revision or noncanonical mode. It writes one full receipt and its related rows atomically. Currently exposed as native API; shared payment/return code still submits full snapshots through incremental reconciliation.
- Native orderPage validates offset/limit (1..100), queries LIMIT/OFFSET with timestamp DESC, sortIndex ASC, id ASC, returns total and revision. Queries are snapshot-consistent transactions.
- Full archive secondary cache is invalidated after native commits, not rewritten. No runtime rollback to stale legacy data; rollback must materialize a verified current full snapshot or v13 backup.
- Existing critical journal still contains full absolute snapshots; replay remains idempotent. Do not claim total removal of full-array serialization, startup memory, journal size or CPU scanning.

## History UI

Android-only adapter wraps the reviewed renderer synchronously with a page of 50 receipts, restores the complete business archive in finally, and adds previous/next/retry controls. Original receipt details/print/return functions remain intact. Load only on history tab; invalidate on saves, ignore superseded responses and clamp deleted final pages. On page error show retry and retain prior compatible history presentation. Noncanonical fallback archive retains existing full-document/300-row presentation rather than losing records.

Source refresh must preserve/install the new adapter before loadAll; reviewed business source hashes remain unchanged.

## Validation and deferred work

Five additional real SQLite tests cover unchanged-row protection under SQL trigger, individual revision CAS, 325-receipt pagination/ties/bounds, lazy migration and failed migration rollback. Existing receipt failure/return/reopen, payment/park/session/journal/v13 suites continue. Four JS UI tests cover bounded pages, business-state preservation, stale responses, retry, offset clamp and inactive screen. Existing write test now asserts no legacy full-cache write.

Final physical checks remain deferred: pages beyond 300, new sale/return refresh, page selection/print/return, force-stop and v13 round-trip. Startup/financial calculations still need full archive in JS; per-receipt business commands and compact native recovery transactions are later work.
