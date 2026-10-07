# 093 — Receiving arithmetic and atomic confirmation

## Scope and parity

MPosReceivingEngine reconstructs reviewed invoiceReceivingItems, receivingStockUpdates and receivingDiscrepancy. Packing/unit conversion uses 092; empty/negative/non-finite quantity/cost, non-simple/deleted products and zero quantity with a nonzero sum remain invalid. Zero-cost positive receipts remain allowed. Duplicate product lines are processed sequentially. Weighted cost uses max(0, previous stock), while stock itself may remain negative after receiving. Untracked products retain stock; each positive line replaces cost, so the last positive line supplies cost for repeated untracked items. This preserves current policy. Stock rounds to three decimal places with the reviewed epsilon; invoice total rounds to cents without that epsilon. Null expected quantity means shortage only when actual quantity is zero; numeric differences use the reviewed 16-epsilon tolerance. Short delivery closes the order without automatically creating another order.

MPosReceivingCommand reads authoritative products/orders/history/suppliers inside one Room transaction, rejects stale expected products/orders/history, recalculates from invoice draft and validates the exact reviewed candidate. Preview updates are never inputs to valuation. Stock/cost, received order and history/projections commit together. Standalone receiving clears receivingDraft in the same transaction. Critical recovery journal must be empty; duplicate/stale prepared commands cannot append the same receipt again. No additional role gate is introduced. The existing JS confirmation/preview/form presentation remains; memory/cart/modal update only after native acknowledgement.

## Storage and compatibility

Supply ownership extends to receivingDraft object/null/absence through one-time legacy initialization. All existing JSON keys, unknown fields and backup v13 shapes remain. No Room schema change. Stale shadow writes/removes are ignored after ownership; draft read failure propagates. 094 still owns draft business editing/restoration; this stage establishes document ownership required for atomic clearing only. Import and existing draft saves use the same compatible native storage facade.

MPosNativeReceivingCommandsEnabled=false delegates to reviewed journal-based confirmation while retaining native storage ownership. Uncertain acknowledgement blocks critical operations until restart. Source publishAvailability() without a persisted-payment ticket remains a no-op under 090; receiving never sends stock or retries a failed availability publication. No catalogue synchronization is added. No local APK assembly.

## Verification and pending acceptance

Independent fixtures are generated from actual reviewed receiving source; JS checks compare fixtures again and exercise stock changes after preview, native acknowledgement before memory changes, failed commit, repeated tap/received order and rollback. JVM compares unit/invoice/stock/cost/shortage fixtures including repeated lines, negative stock, untracked products, zero rows and rounding. Room tests cover standalone/ordered/untracked commit, tampered cost, stale stock, duplicate command and injected projection failure rolling back products/order/history/draft. File-backed supply ownership tests cover draft initialization, obsolete mirrors and reopen.

Physical acceptance remains pending: receiving after a sale while preview is open, restart/backup v13, partial delivery, mixed units, repeated product lines, untracked cost, offline receiving and failure retention. Automatic evidence is recorded after the full suites finish.

Verified: 396 JS / 320 JVM tests passed, zero failures/errors/skips. lintDebug passed: zero errors / 15 existing warnings. No local APK assembly; physical acceptance remains pending.
