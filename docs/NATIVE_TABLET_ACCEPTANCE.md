# Native tablet acceptance evidence

The user defers comprehensive physical testing until the end (2026-10-06). Specs 040–046 already enable products/layout/posNavigation/employees/shifts/orders/parked/currentOrderSession/criticalStorageJournal Room authority after automated checks; settings/SSE remain mirrors by implementation. Record the APK commit, tablet model, Android version, scenario, expected/actual result and pass/fail. The complete [Russian status report and user checklist](MIGRATION_STATUS_RU.md) includes current scope and metrics. Use synthetic data on a test tablet. Do not commit backup documents, tokens, device keys, photos or production records as evidence.

## P2 catalog and recovery

1. Start with a synthetic catalog containing Unicode names, several categories, products without a category, recipes/modifiers and photo references. Record counts and expected ordering.
2. Add, edit and delete products/categories through the existing UI. Wait for initial mirroring and compare after each completed operation. In the debug WebView console, `await MPosCore.CatalogCutover.health()` exposes mode, source and aggregate comparison. Expect `comparison.ok:true`, `comparison.matches:true`, equal counts, `activeSource:'room'` and `roomCutoverAllowed:true`.
3. Force-stop and reopen the app. Expect unchanged UI data and ordering; repeat comparison after mirroring. A merely green comparison is not proof of snapshot/order/business parity: inspect product data and category ordering against the test fixture too.
4. Export a synthetic v13 backup, restore on a clean test installation, reopen and verify fields, recipe/modifier data, photos and catalog comparison. Check iPad-to-Android compatibility separately when an iPad is available.
5. Exercise rapid edits, a large synthetic history, process exit during native work, and then restart/re-mirror. A known failed/pending native write must not report healthy parity; legacy data must stay usable. Record how failure was induced rather than claiming an unperformed failure test passed.

Record these results for final acceptance; catalog/workspace authority is already implemented by specs 040–046, including migration and rollback. Projection comparison now checks the Room document against its indexes; separately compare full fields against the fixture/backup.

## P1 printer/notification settings

1. Change printer and notification settings through the UI, verify their native snapshot, reopen and compare again. Keep all existing printer routing/notification expectations.
2. Repeat during offline operation. Local saves must not depend on backend availability.
3. Exercise storage failure and Activity destruction during native writes where practical. Native errors must not undo a successful legacy save; no native callback should target a destroyed Activity. Record tests not performed as pending.

## P3 diagnostic SSE

1. Observe the same test stream with legacy EventSource and native shadow enabled explicitly. Compare known message payload fingerprints and counts, including Unicode, multiline/empty data and permitted whitespace.
2. Verify named events are excluded from both message observers. Disconnect/reconnect and background/foreground; shared business events must not be delivered twice.
3. Send a test frame over the native diagnostic line/frame bound. The shadow should reconnect without affecting authoritative browser dispatch. Do not infer native reconnect/Last-Event-ID business equivalence from parser tests alone.

## Backup image staging

Verify export/import/confirm/cancel, missing/corrupt photographs, shared image references, low storage and restart. Confirm cancellation removes staged files without pruning active photos; final pruning happens only after the existing finishImport protocol. Keep the 500 MB document and 2 MB decoded-image limits unchanged.

## Report template

- APK commit / variant:
- Tablet / Android version:
- Test fixture description (no attached private data):
- Scenarios actually run and results:
- Failures and reproduction steps:
- Scenarios not run:

A report must distinguish observed physical behavior from automated assertions. Physical evidence remains pending until final acceptance; products already use native authority, while other domains retain their documented source.

## Workspace ownership (041)

Verify category order/colors/symbols/WEB flags and tiles/folders after edits, force-stop and restore. These documents now use Kotlin/Room; their UI rules remain shared. Confirm that a stale legacy cache cannot replace native layout on restart. Include these cases in the final comprehensive acceptance rather than blocking the next migration stage.

## Employee and shift ownership (042–043)

Employees and shifts (including cash movements) now use authoritative Kotlin/Room persistence, alongside products/layout/posNavigation/employees/shifts/orders/parked/currentOrderSession/criticalStorageJournal. Full compatible documents remain the source; typed indexes are supporting structures. Atomic migrations and failed-write rollback, stale shadow protection, restart, actual v13 import and journal recovery are covered automatically. Employee authorization and shift financial engines remain reviewed JS. Other business documents remain on legacy storage; final physical checks remain pending.

## Paid receipt ownership (044)

Orders now use Kotlin/Room document authority with transactional receipt/line/payment indexes. Original sale, split payments, historical consumption and full-return/loyalty fields remain compatible. Actual JS payment-journal recovery, v13 import and full return are tested. Financial engines remain reviewed JS; full-array writes/index refresh remain a performance limitation. See [receipt storage model](RECEIPT_STORAGE_RU.md) for the proposed per-receipt repository and return ledger, which are not yet implemented. Final physical acceptance remains pending.

## Parked-order ownership (045)

Parked orders now use authoritative Kotlin/Room persistence. Complete delivery/customer/modifier/WEB/printing JSON is retained; document and header/line indexes commit atomically. Actual park/resume flows, delayed acknowledgement and failed-resume journal replay are automated. CurrentOrderSession and recovery journal remain legacy at this boundary; no single cross-document SQL transaction is claimed. Business commands and physical printing semantics are unchanged. Full-array refresh remains a limit.

Final tablet cases: park a delivery/WEB order with modifiers/customer and kitchen-printed items, force-stop, resume and compare fields; resume must disappear from parked history without losing the current cart after restart; verify delete and v13 round-trip. Final physical evidence remains pending.

## Session and critical recovery ownership (046)

CurrentOrderSession and criticalStorageJournal now use authoritative Kotlin/Room persistence and atomic singleton indexes. Legacy data is imported only once; secondary cache cannot override native recovery. Journal read failure propagates so existing JS recovery blocks new critical operations. Failed journal clear retains pending snapshots. Existing payment/return/park/resume replay and v13 tests now run through this boundary, including absent or failed caches. Replay orchestration and business commands remain JS; cross-document transactions are still journal-coordinated.

Final tablet cases: restart with unfinished cart, delivery/modifiers/WEB and printing state; restart after interrupted payment/park/resume, verify receipt/stock/shift/cart consistency and no duplicate operation. Native storage failure must prevent critical saves; backup v13 must preserve session. Physical results remain pending.
