# Spec 039 — Durable ordered Kotlin platform settings mirror

## P1 boundary and benefit

`MPosSettingsStore` now owns an ordered native platform snapshot boundary using the existing bounded Kotlin command queue. Previously apply() returned success before a disk write completed, with no storage-failure signal. Checked commit() on an IO worker now returns success only after the platform confirms persistence, without blocking the WebView bridge/UI.

## Contract

- Existing SharedPreferences name `mpos_native_settings`, key `platform_settings_snapshot`, schema version 1 and JSON envelope remain compatible. No migration or destructive reset.
- Preserve printer/notification/unknown settings fields, existing adapter actions and request IDs. Capture serialized settings at submission; later caller mutations cannot change the queued write.
- Replace/get/clear share one FIFO with 64 buffered commands plus executing work. Read follows accepted prior writes. Full/closed queue explicitly returns ok:false without blocking the caller.
- Replace/clear use checked commit(), not apply(). False/throwing commits return generic metadata-only errors and never acknowledge persistence success. The worker continues after failed/invalid operations.
- SharedPreferences can update its own in-memory cache even when a disk commit fails; this boundary does not promise cache rollback. ok:false indicates persistence uncertainty, not a durable transaction guarantee.
- Response adds authoritative:false; existing JS/local platform settings remain the primary source and backup compatibility cache. A native mirror failure cannot change the already completed legacy save or business state.
- Activity closes its queue. Cancellation suppresses late success and failure callbacks after an in-progress synchronous commit; the disk operation may still complete. Buffered diagnostic commands are not a durable outbox; existing startup mirror resends the current local settings.
- No payment/order/shift/stock/authorization rule, printer routing rule, notification trigger, catalog sync or backend call changes.

## Validation and rollback

The shared command worker checks cancellation before reporting an exception, preventing a failed disk commit from calling back into a closed Activity.

Seven API 28 tests exercise replace/clear/read ordering and fresh native-store reads, delayed commit/no premature acknowledgement, immutable capture, false commit, invalid commands, corrupt snapshot repair, capacity/closure and cancellation during commit. Three executed JS tests prove a failed/unavailable native mirror cannot fail completed local saves, and failed local saves are not mirrored.

Full JS/JVM suite, lint and debug/minified unsigned release builds are required. Tests on fresh store instances use the same SharedPreferences; they do not prove physical process-death durability. Tablet printer/notification changes, restart/process kill, failed storage and UI/native parity remain physical acceptance gates. Native settings authority cutover is not enabled.

Rollback restores the previous mirror implementation and constructor wiring; retained preferences remain readable without a data migration.
