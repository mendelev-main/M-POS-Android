# Native migration review

## Current boundary

**User decision 2026-10-06 / spec 040:** manual acceptance is deferred to the end; per-domain native cutovers are authorized after automated checks. Products, category layout, navigation, employees and shifts persistence/reads are now authoritative in Kotlin/Room (040–048). Other Room domains and SSE remain shadows by implementation, rather than a blanket prohibition on cutover. Business engines/UI still use reviewed JS. Previous shadow-only descriptions below are historical context.

The Android app already contains a native tablet shell and native printer, image, backup, PDF/XLSX/share and Telegram transport boundaries. The reviewed bundled POS remains responsible for payments, shifts, warehouse business rules and operational network triggers. Room projections and OkHttp SSE are comparison/diagnostic boundaries; implementations are not evidence of an accepted authority cutover.

Specs 002–030 establish platform settings, Room projections, networking and recovery groundwork. Spec 031 starts a 200-entry metadata breadcrumb trail. The parity matrix still requires physical recovery, printer and tablet interaction evidence. Proceed by completing a useful boundary, rather than replacing working business logic solely because Kotlin is available.

## Sectors and order

| Sector | Concrete Kotlin benefit | Conditions and preserved behavior | Decision |
|---|---|---|---|
| Diagnostic report/export (P5) | Inspect printer/storage/network failures after restart, sanitize report centrally, save through Android document permissions; no backend needed | No business data or secrets; diagnostics never gate sales; manual export | Implement spec 032 now |
| Local persistence authority (P2), one domain at a time | Room transactions, consistent writes and queries, recovery outside WebView lifecycle | Existing JSON/backup v13 compatibility, migration/rollback, automated restart/recovery evidence and deferred physical large-data checks; products/layout/navigation already native; migrate other domains after automated compatibility checks | Highest reliability priority; continue domain cutovers under user-authorized policy |
| SSE/transport (P3) | Kotlin owns connection cancellation and lifecycle, bounded reconnect, platform networking diagnostics | Compare delivery/interruption semantics automatically and retain physical cases for final acceptance; no duplicate business event delivery, no automatic catalog sync | Current shadow observation remains; implement tested transport delivery/recovery next |
| Durable outbox/retry (P4) | Native persistence can retain operation state across WebView/process loss and coordinate idempotent sends | One retry authority, local commit before network, backend idempotency; WEB acceptance/ready, loyalty and replaceable availability have different semantics | Migrate one operation type after Room/transport acceptance |
| Update infrastructure (P5) | Android installer integration and explicit package/signature/version checks reduce accidental incompatible updates | Requires defined trusted artifact source and stable production signing; preserve installed POS data | Separate specification once release policy is defined |
| Cart/workspace UI (P6) | Native touch/long-press, adaptive tablet layout and fewer browser interaction quirks | State/domain contract must be stable first; totals, modifiers, discounts, held checks retain current semantics | Later, after data boundary |
| Payment, shifts, recipes/warehouse, loyalty (P7–P11) | Testable domain engines and transactional state can improve reliability | Characterize existing business behavior before extraction; payment/stock/loyalty side effects are coupled, so no broad rewrite | Separate domain specifications and compatibility suites |
| Analytics (P12) | Room aggregate queries avoid scanning whole JSON receipt histories | Native accepted source and exactly preserved totals, returns, time boundaries and rounding | Last |

## Implemented slice

Spec 032 completes native diagnostic export and hardens the existing breadcrumb recorder. Kotlin builds a sanitized, versioned JSON report, includes app/API metadata and only the most recent 200 events, and writes it on IO after a user-selected Android document destination. The Android adapter supplies a settings button and an export command; it does not calculate business state.

The report sanitizes existing saved records as well as new ones, dropping arbitrary fields and replacing unknown category/event text. This avoids exporting old accidental payloads. Recording failures are best-effort and cannot stop the existing printer/storage/network callbacks. Storage projection failures are visible in diagnostics without changing the callback or storage authority.

Rollback removes the export adapter/action and Kotlin report/export classes; no data migration or business rollback is required. Physical export/recreation/provider tests remain pending until a tablet is available.

Spec 035 completes the next P2 foundation: one bounded Kotlin FIFO processes native shadow commands, and Room commits raw JSON and structured projections in the same transaction. Forced SQLite insert/delete failures prove rollback; requested/committed versions prevent failed or rejected updates from producing a green catalog comparison. The authoritative local write remains independent. Room authority and physical recovery/backup/large-history acceptance remain pending.

Spec 036 moves document-provider byte ingestion into a tested bounded Kotlin reader. The existing 500 MB check now runs during reading, before accumulation exceeds the limit; v13 bytes and restoration rules are preserved. Interrupted input cannot return a partial backup. Accepted large-file memory usage and physical provider/restore acceptance remain unresolved technical limits, not new business decisions.

Spec 037 extracts existing native photo packaging/staging into a testable Kotlin recovery module. It preserves v13 business fields and image mapping, while failed preparation attempts to remove every successfully staged file and preserves the original error. The manager retains provider IO and shared JS confirmation/restoration. Existing export deduplication and separate per-product import files are retained; physical recovery acceptance is pending.

Spec 038 completes a bounded Kotlin SSE message reader for P3 diagnostics. It corrects payload whitespace and line/empty-frame handling to match the legacy message listener, with native-only resource bounds. No WEB business event, ACK, availability or synchronization trigger moves. Physical stream acceptance is still required.

Spec 039 makes the native printer/notification settings mirror ordered and disk-confirmed in Kotlin. Checked IO commits replace immediate apply() acknowledgements; bounded capacity, immutable capture and cancellation are tested. The legacy save remains independent and authoritative. No printer routing, notification trigger or business calculation changes.

## Business questions for a separate decision

These are unresolved policy questions, not changes made by this implementation.

1. **Availability retry — resolved by user.** After an unsuccessful send, retry only after the next successfully persisted payment. Start/online/foreground must not retry. Example: offline payment changes 10 portions to 9, publication fails, and the website keeps its previous snapshot until a subsequent local payment triggers a fresh snapshot. Spec 033 implements this Android policy while retaining the reviewed shared source. Normal pre-existing stock-change/manual-sync publications remain supported while no failure is pending, but cannot bypass the post-failure gate. No automatic catalog synchronization is added.
2. **Reservation settlement bound.** Availability snapshots include at most 100 settled WEB-order IDs, including IDs recovered from durable paid receipts. If more than 100 payments need settlement after an outage, does the backend reconcile the older reservations independently? Backend code/evidence is not present in this checkout, so this is an integration question, not a confirmed defect. Do not increase the bound or change retry semantics without confirming the backend contract.
3. **Shift report format — resolved by user.** Telegram must receive a receipt image as on iPad. Spec 034 implements native PNG generation and sendPhoto while retaining local close/notification triggers. The iPad receipt counts all receipts (including returned ones) while sales totals exclude returns; this existing distinction is preserved and flagged for a possible future refactor.

No payment, stock, catalog, backup or authorization calculations are refactored as part of diagnostics. Availability retry behavior is changed separately under the explicit user decision in spec 033.

## Automated validation

- `node --test tests/*.test.cjs`: 78 passed, no failures or skips. Includes reviewed source SHA-256 parity, executed adapter/request/shift-close tests and repeatable source-sync fixture checks.
- `./gradlew --no-daemon --max-workers=4 testDebugUnitTest lintDebug assembleDebug assembleRelease`: successful.
- JVM: 72 executed tests, no failures/errors/skips. Covers diagnostics isolation, shift receipt values/multipart/native PNG, FIFO/backpressure, real Room/SQLite rollback/reopen, bounded backup input, native photo round-trip/rollback, SSE framing and checked settings persistence/cancellation.
- Android lint: no errors; 15 warnings (including KTX style suggestions for explicit checked settings commits; KTX edit does not expose the commit success boolean). Debug APK and minified unsigned release APK produced. Production signing, actual Telegram delivery and physical tablet acceptance are not established by these checks.

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

## Incremental receipt rows and history (047)

Canonical receipt archives now use complete per-receipt JSON in existing header rows; positions/payments are related rows. Snapshot reconciliation touches changed/deleted receipts only. Native full-array compatibility read/export is retained; noncanonical archives retain full-document mode. Secondary full archive cache is invalidated. Lazy migration/rollback, revision-checked individual upsert and SQL pagination are automated. Android history uses pages of 50 beyond the previous 300-receipt limit, with original detail/print/return renderer and stale-response protection.

Limits: shared business runtime still loads/submits full arrays, native reconciliation scans them and critical journal still stores full snapshots. No claim of constant-cost complete payment or reduced startup memory. Final tablet checks include 325+ receipts, tied dates, page controls, returns/print, new sales on history tab, restart and v13 export/import. Noncanonical fallback retains prior history presentation.

## Atomic local payment command (048)

Actual payment finalization now uses Kotlin validation/stock deduction/delivery cash checks and one Room transaction for catalog, shifts, one receipt, cleared session and idempotency marker. No full order archive is sent or copied to a payment journal. Exact retry is locally idempotent; stale/conflicting commands fail. Pricing, discounts/rewards and recipe expansion remain reviewed JS; external effects run after native acknowledgement. Nonpayment journal recovery remains unchanged. This partially implements P7, not the whole financial engine. Physical cash/card/split/delivery/reward/force-stop and v13 cases remain pending.

Business issue for separate refactor: existing cross-shift refund drawer calculation (100 opening minus 20 refunded from prior shift reports 100 instead of physical 80) is reproduced and retained, including native delivery cash parity. Return attribution and historical reports need a coordinated business fix.
