# 095 — Inventory recount, differences and atomic application

## Native scope

MPosInventoryCommand reconstructs the reviewed inventory-fix and inventory-complete candidates from authoritative Room catalogue/draft/history/config documents. Fix requires an unfixed draft row, a finite nonnegative actual quantity and a simple stock-tracked product. It takes current saved stock (not initial draft expected stock), applies the reviewed three-decimal epsilon rounding, calculates difference, timestamps the row, and commits product stock and draft together. Failure rolls back both. Expected snapshots reject stale/tampered stock/draft candidates.

Completion requires every nonempty draft item to be fixed. It computes estimatedLoss from negative rounded differences and current product costs; historical items retain the reviewed raw draft fields, with unknown extensions. The audit is prepended, scheduled completion updates lastCompletedAt, adhoc completion leaves it unchanged, and draft clears in the same transaction as history/projection/config/catalogue. Completion preserves current catalogue stock, including sales after fixation; it does not reapply counted stock or change costs. Duplicate/stale completion cannot append a second audit. Native critical journal must be clear.

Config loader defaults and array normalization are reconstructed for comparison. The reviewed UI frequency selector can remain an unsaved choice in memory; completion already persisted that choice, so a valid captured weekly/monthly/quarterly frequency remains accepted. Other persisted config fields/extensions cannot be overwritten by a stale candidate.

## Compatible ownership and remaining presentation

MPosInventoryStorage owns existing inventoryConfig/inventoryDraft/inventoryHistory object/object/array documents, null/absence and projections. One-time imports keep raw JSON and backup v13; obsolete shadow writes/removes cannot replace owned documents. No Room schema change. Native read errors propagate through the inventory load adapter rather than substituting empty documents.

Existing start/form/input/pause/cancel/calendar/reminder/settings handlers remain reviewed JS, using the native document storage facade. This stage moves recount/difference/loss and authoritative fix/completion, not the full inventory UI/calendar. Synchronous summary is still a JS preview; committed loss is validated in Kotlin. Native bridge freezes candidates before initialization; source memory/rendering changes after acknowledgement. MPosNativeInventoryCommandsEnabled=false restores reviewed critical-journal commands with native storage ownership. Uncertain commit blocks critical operations until restart.

Fix and completion do not publish availability under 090 without a persisted payment; no synchronization/retry trigger is added. Existing role split is unchanged: fixation/completion have no new admin gate, cancellation remains the source admin gate.

## Business ambiguity retained

Fixing 10 l to 8 l changes stock immediately. Cancelling later removes the draft, but leaves stock at 8 l; the existing cancellation modal claims fixed data will not be saved. An explicit question with this example was sent to the user for later policy/text clarification. Current behavior and text remain unchanged while the question is pending. Reversing stock could also overwrite sales after fixation and must not be introduced silently.

## Verification and acceptance

Independent actual-source complete candidates checked in JS/JVM for fixation, scheduled/adhoc completion, loader defaults and unsaved frequency. JS checks acknowledgement before state, current stock, repeated operation, failure/input retention, untracked/null/fixed refusal, later sale preservation and rollback. Native protocol verifies frozen count input/initialization. Room verifies exact records, loss/projection, stale/tampered/null/untracked/fixed data, unfinished/duplicate completion, and injected final-write rollback for fixation/completion. Inventory ownership tests exercise FIFO, obsolete mirrors and file-backed reopen. Physical acceptance is pending. Automatic evidence is recorded after suites finish.

Verified: 409 JS / 336 JVM tests passed, zero failures/errors/skips. lintDebug passed with zero errors / 15 existing warnings. Physical acceptance pending; no local APK assembly.
