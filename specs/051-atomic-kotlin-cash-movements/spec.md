# 051 — Atomic Kotlin manual cash movements

Authorized continuation after 050: migrate manual deposits and withdrawals, retain current business rules and backup v13. Immediate cutover after automated checks; physical acceptance deferred.

## Boundary

Reviewed submitCashMovement UI remains unchanged. An Android adapter intercepts only cash-movement commits and sends shift ID, one new movement, expected shifts and proposed shifts. Kotlin requires initialized native shifts/orders/recovery, no pending journal, an unchanged shift snapshot, open target shift and unique movement ID. Native accounting reads canonical receipts and applies 049 cross-shift refund attribution. Both types require a finite nonnegative drawer; withdrawal cannot exceed it by more than the existing 0.0001 tolerance. Amount must be finite and positive; retain the original value without currency rounding. Manual movements have only id/type/amount/timestamp/note: refund/delivery subtypes remain exclusive to their dedicated commands.

Kotlin reconstructs exactly one appended movement, compares the full proposed document and preserves all existing records/extension fields. One Room transaction saves shifts/projections and the request marker; failures roll back all writes. Orders and critical journal are untouched. No network, stock, printing or Telegram effects added. JS state/modal/render change only after durable acknowledgement.

## Replay and compatibility

Marker uses movement ID/time and a SHA-256 hash of the exact request. Exact replay requires the movement still present once in its original shift; changed request rejects. If an imported pre-operation backup removed the movement, old replay rejects. A fresh operation with a new timestamp can commit. A reused movement ID already present in any shift rejects, protecting the global projection ID. Native protocol retries the identical request once only after uncertain acknowledgement, then blocks subsequent critical actions until reload if still uncertain.

Removing the adapter restores the existing journal path; all persisted JSON shapes/keys remain compatible with v13. Source refresh preserves the adapter and hashes of reviewed business JS. Native shift open/close and screen migration remain future work.

## Preserved business cases for later review

- Negative drawer blocks deposits as well as withdrawals; current UI therefore cannot repair a negative balance through a deposit. Do not silently allow it during migration.
- HTML requests two currency decimals, but the current handler accepts positive fractions such as 0.001 without rounding. Preserve this until a dedicated currency precision decision.
- Manual deposit/withdrawal stores an optional comment but no reason enum or separate acting employee. Preserve the record, do not invent attribution.

## Validation

Real SQLite: deposit/withdrawal and unknown fields, fractional amount, negative drawer, invalid amount/type/subtype, stale snapshot, closed shift, pending journal, cross-shift refund, duplicate ID, exact replay/reopen, backup restore, and synthetic failures at shifts/movements/marker writes.

Actual shared JS handler: acknowledgement ordering, busy guard, unchanged modal/state on failure, exact timeout retry and recovery block, comma decimals, invalid/insufficient amounts, refund-limited cash, and delegation of other operation types. Full JS/JVM suites, source sync/hash, lint and both builds required. Tablet physical cases remain pending.

Native drawer calculation currently scans canonical receipt payloads; large-archive performance remains a pending tablet check, not an accepted benchmark.
