# Spec 046 — Authoritative Kotlin session and recovery persistence

## Scope and benefit

Move currentOrderSession and criticalStorageJournal document ownership to Kotlin/Room. Native journal durability must precede all critical business writes; native clear must follow all of them. Preserve existing JS command calculations and v13 structures. This moves persistence, not the journal replay engine or payment commands.

## Contract

- Whitelist only currentOrderSession and criticalStorageJournal with separate mpos_recovery_authority_v1:<key> markers. Import current legacy data once over stale mirrors, atomically with document/index/marker; no schema/destructive migration.
- Keep original object JSON, nested write snapshots/order, split drafts, modifiers, customer, delivery, WEB and printing fields, unknown data and absence/null distinctions. Existing projections are supporting indexes only.
- Document and singleton projection change atomically. Null/absence clears projection. A failed journal clear retains original pending writes for replay.
- Native commands share bounded FIFO. Ignore obsolete generic shadow writes/removes after ownership. Cache updates follow native acknowledgement; failed cache cannot undo native commit or become a fallback source.
- Critical journal read errors MUST propagate even when an error callback is supplied. Existing JS recovery catches the error, marks recovery pending and blocks later critical commits. A read failure must never mean empty journal.
- Existing commitCriticalStorage still writes journal first, then business snapshots, then null journal. Existing recovery replays absolute snapshots before loadAll. Native multi-document operations remain several transactions with journal recovery, not one SQL transaction.
- Preserve current session backup v13 import/export. Critical journal remains local internal recovery metadata, not a new backup schema field. No financial/authorization/printing/network policy changes.
- Lost acknowledgements remain uncertain commits; do not claim rollback. Rollback requires a verified fresh secondary cache or v13 data restore, retaining native recovery records until resolved; v13 itself does not contain pending journal metadata.

## Validation

Eight file-backed native SQLite tests cover both-key one-time migration/stale writes, null/remove, marker/document rollback, wrong keys/shapes, reopen, projection failure and failed journal clear. Four executed JS tests cover failed journal creation before business writes, unreadable journal blocking new payments, clear failure plus native replay without legacy cache and complete session/payment recovery when all caches fail. Existing actual park/resume, return, payment-journal and v13 tests continue through the native journal.

Final physical acceptance is deferred by user. Next stage: individual receipt/order commands and paginated queries while preserving journal and command parity.
