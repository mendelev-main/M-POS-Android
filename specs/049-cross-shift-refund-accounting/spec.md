# 049 — Shift accounting and cross-shift refunds

Authorized by the user's request to check/fix the documented defect and continue the migration. This correction supersedes the deliberately preserved defect in 048. No schema/key/backup v13 change.

A sale belongs to its original shift. A refund belongs to returnedShiftId; legacy receipts without it fall back to the original shift. Refunding a closed shift's receipt must not remove that sale from the original shift's totals/count. A refund-only shift can have negative net revenue. Refund cash/card attribution uses historical payment parts, with legacy method fallback.

Cash balance = opening cash + net cash receipts + deposits - withdrawals + matched refund withdrawals. Matching each return to at most one withdrawal uses the persisted return timestamp and cash amount (tolerance 0.001). This avoids subtracting the same return twice, supports older receipts without a movement, and never cancels an unrelated refund withdrawal. No new movement IDs or flags are needed.

Kotlin MPosShiftAccounting owns the native payment/delivery cash check. The Android runtime adapter mirrors the calculation for current JS returns, shifts and report payloads; reviewed iPad source stays unchanged. This is a partial P8 domain migration, not completion of P8 or native return persistence. Opening/closing shifts, return transaction, pricing and loyalty remain in their existing engines.

Compatibility/rollback: original JSON receipts/movements are unchanged; removing the adapter restores the reviewed runtime. Reverting this change restores the old defective cross-shift calculation and is not a financially safe normal operation. Historical backups without returnedShiftId cannot identify a later return shift reliably; do not guess from dates. Duplicate return timestamps/amounts are matched once each. Inconsistent imported movements remain visible; no automatic data repair.

Acceptance: shared synthetic fixtures must pass in JS and Kotlin for same/cross shift cash/card/split, legacy missing attribution/movement, orphan withdrawals and repeated timestamps. Reports/UI must use the corrected totals without modifying receipts. Real Room payment test must reject delivery when the corrected post-refund drawer is insufficient, without writing stock/receipt/movements. Full JS/JVM suites, lint and debug/release builds required. Physical tests deferred per user instruction.

Current native delivery check reads canonical receipt payloads to include cross-shift returns; large-archive performance remains a deferred tablet check. A later projection optimization must retain the same accounting fixtures.
