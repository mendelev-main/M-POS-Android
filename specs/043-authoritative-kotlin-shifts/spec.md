# Spec 043 — Authoritative Kotlin shifts persistence

## Scope and benefit

Kotlin/Room owns the complete `shifts` JSON document. Atomic durable writes and indexed projections survive WebView restarts; failed native writes cannot be presented as successful legacy saves. This moves persistence ownership, not business calculations, permissions or screens.

## Contract

- One-time durable authority marker imports the current legacy document over stale shadow data. Later startup never reimports the cache.
- Preserve full v13-compatible JSON, unknown fields, order, missing/null/empty-array distinctions and original employee roles/credentials or shift financial values. Never reconstruct records from lossy indexes.
- Document, marker and existing typed indexes commit atomically. Shifts include cash movements in the same transaction. Projection mappings are retained; missing/non-finite numeric values use zero only in diagnostic indexes because SQLite NOT NULL cannot store NaN. Original JSON remains unchanged and is used for business calculations.
- Reject malformed, trailing and non-array/non-null JSON without replacing committed data. Native reads require initialization.
- Existing bounded FIFO serializes native operations. Ignore obsolete generic shadow writes/removes after ownership.
- JS captures a write before waiting, requires native acknowledgement and only then updates the secondary compatibility cache. Cache failure cannot undo a committed save. Timeout means uncertain commit, not rollback.
- Existing critical-storage journal replays absolute snapshots after partial v13 import. Cross-domain journal remains recoverable, not a single native transaction.
- No schema change, destructive migration, financial/authorization changes, automatic catalog sync or availability retry changes. Existing source-parity checks remain required.

## Validation and rollback

Real file-backed Room/native SQLite tests cover migration, FIFO, removal, null, stale writes, insertion/deletion failures, transaction rollback, invalid JSON, full payload and reopen. Executed JS tests exercise actual backup application and journal recovery after native write failure.

Rollback requires restoring the previous provider with a verified fresh compatibility cache or v13 backup; cache may be stale after a cache failure. Preserve Room documents/markers until recovery is verified. User defers comprehensive tablet acceptance until the end.
