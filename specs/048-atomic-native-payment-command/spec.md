# Spec 048 — Atomic native local payment completion

## Scope and benefit

Android payment finalization delegates its local commit to Kotlin/Room. Send one new complete receipt, expected/current product and shift snapshots, prepared result snapshots, empty session and optional delivery movement. Do not send the entire orders array or write a full payment journal snapshot. Native transaction owns stock deduction, payment validation, delivery cash checks, receipt numbering/conflicts and atomic persistence.

Pricing, discount/reward allocation, recipe expansion, UI/card-terminal confirmation, and all external side effects remain reviewed JS parity inputs. This is a partial native payment domain, not a complete financial/pricing engine rewrite.

## Contract

- Android-only wrapper intercepts exactly commitCriticalStorage('payment', four payment documents). Validate one appended receipt and unchanged prior archive; other critical operations retain the existing journal provider. Shared business source files remain byte-identical.
- Initialize products/shifts/orders/session/critical-journal authority before command. Capture JSON before awaiting. Reject pending critical recovery; native transaction also checks durable journal is empty.
- Compare expected product/shift snapshots semantically with committed native data, total receipt count, shift-open/employee and per-shift receipt number. Reject stale state rather than overwriting it.
- Validate nonempty receipt, cash/card/split amounts, sum/total tolerance, cash tender/change and card null fields. Mirror reviewed rounding/tolerances; permit zero-total reward sales.
- Apply historical stockConsumption version 1 (recipe expansion remains JS). Check unique positive quantities, tracked simple ingredients, finite availability with JS tolerance and stock rounding including Number.EPSILON. Compare computed result with JS prepared products.
- Mirror existing cashDrawerBalance arithmetic for delivery: opening cash + active cash sales + deposits - withdrawals + refundCashMovements. Validate delivery availability, append exactly the supplied delivery withdrawal and compare resulting shifts with prepared snapshot. Preserve existing business semantics, including known cross-shift refund issue recorded below.
- One outer Room transaction commits catalog/projections, shifts/movements, one receipt/lines/payments, cleared session and mpos_payment_command_v1:<receiptId> hash marker. Any failure rolls back all parts; no cross-document payment recovery journal is needed.
- appendPayment never reconstructs or writes the whole archive; retain canonical/full-read/v13 behavior. Noncanonical archive requires repair rather than silently losing records.
- Hash marker makes exact same command idempotent after lost acknowledgement/reopen. Changed request with same ID is rejected; a removed committed receipt cannot receive a false success. This guarantees local idempotency, not exactly-once external messages or terminal operations.
- Retry identical command once only for uncertain timeout. A second uncertain timeout blocks subsequent critical saves until native reload. Definitive failure preserves UI cart/stock/history and starts no external effect.
- Invalidate secondary caches after native success. Existing finalized UI update, availability gate, loyalty and print callbacks follow native acknowledgement unchanged. No additional network action or automatic catalog sync.
- Room schema 12 and backup v13 retained. Rollback requires current native full export/verified v13 data and previous adapter routing; old legacy caches are not valid authority. Idempotency metadata stays local.

## Validation

Seven file-backed native SQLite cases cover split commit and unknown fields, reopen/replay/conflicting IDs, forced failure at five transaction boundaries, stale data/sequence/pending journal, invalid totals/change/card/stock, delivery cash and fractional stock/binary currency/zero reward sale. Four executed JS tests run actual finalizePayment and native adapter with a 500-receipt archive: compact payload, acknowledgement-before-effects, failure retention, identical timeout retry/recovery guard and retained nonpayment provider. Existing v13/return/receipt/session/source-parity suites apply.

Final physical acceptance remains deferred: cash/card/split/reward/delivery sales, reboot after interrupted commit, quantity precision, printer/availability/loyalty timing and v13 restore.

## Business issue preserved for separate decision

Current JS cashDrawerBalance cancels all refund withdrawals with refundCashMovements. A refund of a prior-shift receipt from the current shift (opening cash 100, refund 20, no current sales) returns 100 instead of physical 80. Reproduced directly against existing shifts.js. Native delivery cash check mirrors it for parity; fix return attribution, historical shift reports and drawer calculation together in a separate business change, not silently inside this migration.
