# 056 — Native cash deposit / withdrawal input forms

Authorized continuation after the user confirms first opening and backup import on Android. This boundary migrates the visible forms into Kotlin Android Views without new dependencies. Closing/opening/authentication/report forms stay on their existing path.

## Native presentation

MPosCashMovementDialog uses platform AlertDialog with amount, optional comment, explicit confirm/cancel and native field errors. Decimal comma is accepted; positive fractional amounts are not rounded or restricted to two decimal places, matching reviewed handler rules. NaN/infinite/nonpositive values reject. No new financial amount policy introduced.

The existing secure shiftScreen channel carries generation token, operation type/current shift ID to show the dialog, and typed amount/comment back on confirm. Kotlin trims the comment, locks inputs/confirm/cancel while acknowledgement is pending, and ignores obsolete results. A known rejection unlocks editing; uncertain commit status locks confirmation until reload while allowing close. Android Back/outside/cancel follows existing form cancellation when idle. Activity destruction dismisses the native window without inventing a transaction result.

## Compatibility boundary

Android adapter calls the original openCashMovementModal to create real compatibility fields, then hides its modal overlay while the native dialog owns visible editing. Typed native values are applied to those fields and submitted through the original submitCashMovement, native-cash-movement-command adapter and MPosCashMovementCommand. This is a presentation migration, not a claim that the remaining JS command construction/orchestration moved to Kotlin. No fake DOM, source rewrite or duplicated pricing/cash algorithm.

The existing native command remains the authoritative cash/state/replay validator and Room commit owner. Current shift ID is checked again before invoking the original handler. Successful state/render/flash still occur only after native acknowledgement. The original close is deferred while busy, then closes both surfaces once. Failed save leaves state unchanged and form visible. Existing exact retry/recovery block, 049 return accounting and 0.0001 withdrawal tolerance stay intact; negative drawer still blocks deposit as documented in 051. No new network/catalogue/availability action.

Replaced/closed modals invalidate tokens and dismiss the native form; detached replies cannot target a new form. A busy form rejects duplicate submission. MPosNativeCashFormsEnabled=false restores visible original forms for the session. Bridge send failure also leaves the original form visible. Removing only the adapter restores the original entrypoints; JSON/schema/v13/native domain command authority stays unchanged.

## Verification

Actual JS adapter tests exercise typed inputs, deferred close, duplicate/stale/current-shift/busy guards, known/uncertain failure, cancellation, rollback and replacement/load order. Integration tests run the original submitCashMovement with the real native storage adapter for deposit/withdrawal and verify pre/post-ack state, fractional value, comment, command count and no journal fallback. Existing cash command SQLite replay/rollback/restore tests remain required.

Robolectric tests run real dialog controls after Android main-loop delivery, native amount checks, locked fields/buttons, retry/cancel, stale acknowledgements and uncertain status. Full JS/JVM suites, lint, debug and minified unsigned release builds required. Physical keyboard/decimal/back/rotation/font size and update acceptance remains pending.
