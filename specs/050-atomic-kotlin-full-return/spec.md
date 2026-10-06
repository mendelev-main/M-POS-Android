# 050 — Atomic Kotlin full return

User authorization: continue the documented migration after 049; automated gates now, comprehensive tablet acceptance at the end. No schema/key/backup v13 changes.

## Native boundary

For receipts with stockConsumption, Android sends one original receipt, one returned receipt, expected product/shift snapshots, archive count and an optional cash refund movement. No full receipt archive is transmitted or written into a critical recovery journal.

MPosReturnCommand validates durable source snapshots, an unreturned matching receipt, an open target shift, pending-journal absence, return amount/cash and drawer availability. Historical stockConsumption v1 restores original simple products, even if noStockTracking has since changed. Products must still exist, quantities be positive/unique and restored balances finite. Kotlin independently recomputes the proposed stock and returned receipt; unrelated fields survive. Return keeps original shiftId and sets returnedShiftId, returnedAt, returnAmount, plus pending loyaltyReversal when a customer is present. Cash movement retains the existing shape/note/time. Card refunds remain manual through the bank terminal.

One Room transaction saves catalogue, shift/movement, one existing receipt and command marker. Failure rolls back everything. Receipt position and payment/line projections remain compatible. JS applies in-memory state and invokes existing loyalty/availability gate only after durable acknowledgement. No new availability trigger or retry is introduced.

## Replay and restored data

Marker key includes receipt ID and return timestamp; SHA-256 identifies the exact serialized request. A single uncertainty retry reuses identical payload. After two uncertain responses critical actions block until reload. Exact replay is acknowledged only if the receipt is still returned at that timestamp; changed requests reject. After importing a pre-return backup, the old marker cannot acknowledge the unreturned receipt; a new return timestamp can commit. These are local guarantees; no external payment-terminal or loyalty exactly-once guarantee is claimed.

## Compatibility / rollback

Receipts without historical stockConsumption use the reviewed legacy return/journal path. Do not invent historical recipes: legacy restoration still uses current product/recipe definitions and may differ after edits. Malformed historical consumption is rejected, not silently sent to legacy fallback. Removing the return adapter restores the prior journal entry point; native records require no conversion. UI, loyalty reversal transport, return confirmation and catalogue synchronization remain their existing implementations.

## Acceptance

Real SQLite tests: fractional historical stock, unknown fields, noStockTracking change, mixed/card return and cross-shift balance; reopening/replay, changed command/double return, restored backup; synthetic INSERT failures at product/shift/payment/marker boundaries; stale snapshots, pending journal, closed shift, insufficient drawer, missing product, malformed consumption and SQL protection against rewriting unrelated receipts.

Actual shared processFullReturn JS tests: compact command with 501 receipts, acknowledgement before state/effects, native failure leaves state untouched, identical timeout retry and recovery block, second return rejected, legacy restoration/journal and insufficient cash. Existing full suites, source hash/sync, lint and both APK builds required. Physical acceptance remains pending.

Native cash guard currently reads canonical receipt payloads to account for cross-shift refunds; archive-scale performance remains a tablet check and future projection optimization.
