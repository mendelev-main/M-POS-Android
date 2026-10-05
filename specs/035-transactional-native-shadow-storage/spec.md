# Spec 035 — Transactional ordered native shadow storage

## Goal and concrete benefit

Continue P2 by making Kotlin/Room a consistent storage foundation before any authority cutover. Previously each bridge command launched an independent IO coroutine: a raw JSON write and its table projection were separate commits, reads could overlap them, and coroutine scheduling could reorder writes/deletes. A projection failure could leave the new JSON next to older structured tables.

One FIFO Kotlin worker now processes accepted bridge commands. Raw JSON and all associated projection tables commit together or roll back together. Diagnostic reads follow earlier queued writes and use a Room read transaction.

## Contract

- Preserve shared POS business code, local storage keys/JSON, backup v13, Room schema version 12 and explicit migrations. Local POS storage remains authoritative; no network call and no payment/stock/loyalty/WEB trigger changes.
- `MPosStorageMirror` captures immutable command fields before enqueue. All supported reads, puts and deletes share `MPosStorageQueue`; no per-command IO launch. The bridge never waits for Room.
- At most 64 buffered commands plus the executing command. A full/closed queue explicitly replies `ok:false`; it never blocks local persistence, drops work silently or buffers unlimited commands. The bound is a command count, not a total byte budget.
- Put atomically commits raw shadow and domain projection; failure returns `ok:false, projectionOk:false` and preserves the previous consistent native state. This deliberately replaces the previous raw-success/projection-failure shadow response; it does not change the result of the authoritative local write.
- Delete atomically removes raw shadow and every related table row. SQL/projection failure rolls everything back.
- Preserve all existing projection mapping/calculations and non-authoritative response flags. No new schema or destructive migration.
- Track requested and committed per-key versions. A failed/rejected/newer queued write cannot be hidden by completion of an older write. Replies add metadata-only `shadowCaughtUp` and `pendingShadowKeys`.
- Failed/incomplete required keys prevent a green parity/snapshot response. Android catalog consumers also refuse snapshots or comparisons reporting `shadowCaughtUp:false`; source remains legacy and Room activation remains blocked.
- Cancellation propagates through Room, rather than being swallowed by diagnostic error handling. Activity destruction closes the queue and discards unexecuted shadow work; later WebView startup uses the existing local-storage mirror to rebuild. This queue is not a durable business outbox.
- Version/catch-up metadata is instance-local, not proof that unseen local-storage contents match Room after process restart. Initial mirroring plus physical recovery/parity evidence remain necessary before cutover.

## Automated acceptance

- FIFO test holds a write suspended and verifies later writes/reads cannot overtake it.
- Full queue rejects immediately, command failure does not kill the worker, closure cancels active/discards buffered work.
- Native SQLite/Room at API 28: ordered put/remove/put/snapshot; forced SQL insert/delete trigger failures roll back raw plus projections; malformed input retains last consistent state; later success repairs catch-up; persisted data survives file-backed database reopen.
- Executed JS tests: failed native mirror does not fail a completed local write; failed local write is not mirrored; stale snapshots/green comparisons are refused; Room authority stays disabled.
- All existing Node/Kotlin/graphics tests, Android lint, debug and minified unsigned release builds.

## Physical acceptance and rollback

Still pending: rapid real-tablet operations, large histories/backpressure, process termination during projection, restart from legacy data, backup v13 restore and comparisons across all projected domains. Room cannot become authoritative from automated results alone.

Rollback restores the former mirror dispatch/projection behavior and catalog diagnostic guards. No business data migration; native tables and legacy keys remain compatible. Preserve the new atomic behavior unless a specific regression justifies reverting it.
