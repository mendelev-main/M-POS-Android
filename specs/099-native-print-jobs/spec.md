# 099 — Native print routing, bounded dispatch and job outcomes

## Scope

Move receipt/kitchen/shift-close routing, category filters, automatic flags, kitchenPrinted suppression, copy expansion and job management to Kotlin. Keep reviewed payment/park/WEB/shift entry points and their local-persistence-before-print timing. Source receipt payloads and existing ESC/POS rendering are retained; typography/receipt redesign and platform setting authority are outside this stage. Printer settings are captured from the current authoritative settings snapshot, not the asynchronous native settings mirror (authority cutover is 100).

## Queue and compatibility

Native requests produce frozen jobs and persist queued/sending/sent/failed/uncertain/cancelled outcomes in a bounded internal Room journal before/after network work. Each printer endpoint is FIFO; at most four endpoints transmit concurrently. Admission is bounded, whole-batch rejection prevents partial copy admission. No automatic retry at startup/reconnect/foreground or after TCP error. A successful TCP flush means bytes were sent, not a confirmed physical print. Interrupted sends are uncertain; queued jobs after restart are cancelled. Manual receipt printing remains a new explicit request; no deduplication of deliberate reprints.

The internal journal stores identifiers/status metadata only, not another copy of customer/receipt contents. Recovery runs before the first explicit print admission in a new service; it never initiates network work. Internal job history is not part of v13 backup and is never replayed by import; POS business documents/keys/flags remain unchanged. Keep rollback routing via MPosNativePrintJobsEnabled=false, still using bounded native direct transport. Physical printing/status recovery requires final tablet/printer acceptance.

## Preserved business rules

Only enabled !== false and nonempty IP qualify. Kitchen selection uses printOrders === true; receipt selection uses printReceipts !== false. Manual requests ignore automatic flags. completed suppresses kitchen when kitchenPrinted is truthy, then adds automatic receipts. kitchen-now uses automatic kitchen only. Empty category selection means all items; nonempty categories filter exact item category and skip empty kitchen documents. Copy loop preserves max(1,Number(copies)||1), including positive fractional values expanding to ceiling. Normal settings expose one to three copies; nonfinite/unbounded corrupt batches are refused.

Shift-close printing ignores autoPrintReceipt, routes to every receipt printer and uses report.closedAt || now. No receipt printers preserves the existing notification. No new payment, stock, catalogue or availability trigger. Completed automatic receipt requests use one indexed full-JSON receipt lookup in canonical Room archives, avoiding a full archive serialization. Legacy noncanonical documents retain first-match/numeric-ID compatibility. Completed automatic receipt requests use persisted Room receipt authority; manual bills retain supplied snapshots. The existing business callback remains the trigger; this stage adds no recovery callback that could reprint kitchen jobs. User-initiated reprints must be checked physically, and an external printer has no exactly-once acknowledgement. Kitchen quantity deltas and printedItems remain the reviewed/native parked/WEB context boundaries; this stage does not reinterpret quantity-reduction rules.

## Verification

26 independent actual-source print planning fixtures, including null versus missing category and object-reference strictness; JVM parity for every routed payload/copy/filter/flag; Room outcome transitions, retention/restart/no retries and read-only business documents; bounded queue concurrency/order/overflow/close/error tests; JS immutable dispatch, rollback, disabled/empty settings and persisted-before-print trigger tests. Full JS/JVM/lint, no local APK assembly. Physical cases pending.

## User decision

The user explicitly confirmed that automatic repeat after a connection break must be excluded. Uncertain TCP results require operator inspection and a deliberate manual reprint. Restart, reconnect and foreground never replay jobs. The existing kitchenPrinted/printedItems policy is retained, including the no-configured-kitchen-printer case; it is not silently changed into physical acknowledgement.

Verified: 435 JS / 368 JVM tests passed with zero failures/errors/skips; lintDebug zero errors / 15 existing warnings. The full suites also cover the authoritative single-receipt lookup and legacy duplicate/numeric IDs, unchanged unrelated receipt rows, all admission/sending/final-write failures, bounded retention and actual loopback TCP bytes. No local APK assembly. Physical Android/printer acceptance remains pending.
