# Native Migration Roadmap

## Purpose

Move M POS Android toward a native Kotlin/Jetpack architecture only where native ownership materially improves reliability, data safety, device integration, performance or maintainability.

This is an incremental migration, not a rewrite. Until a migrated boundary is accepted, the existing reviewed HTML/JavaScript implementation remains the parity reference.

## Migration rules

1. One native boundary or cohesive module at a time.
2. Preserve offline-first behavior and existing business semantics.
3. Preserve `prilavok_` JSON compatibility and backup schema v13 until a dedicated data migration is accepted.
4. Every stage must define compatibility/rollback behavior.
5. User decision 2026-10-06 authorizes immediate native authority cutovers after automated verification; comprehensive manual Android testing is deferred to the end. Keep physical evidence pending and preserve business/backup compatibility.
6. UI should not be moved to Compose before its state/domain boundary is stable unless the UI itself is the primary problem.
7. Do not move business logic merely to increase the percentage of Kotlin.
8. All new/reworked modules use `M POS` / `MPos` naming. `PrilavokCore` and `prilavok_` are compatibility-only legacy surfaces and are removed as the corresponding module is migrated.

## Status legend

- ✅ Native and retained
- 🟢 Approved / in progress
- 🟡 Candidate after dependencies
- ⚪ Later candidate
- ⛔ Keep as-is unless requirements change

## Existing native boundaries

| Area | Status | Notes |
|---|---|---|
| Android activity/lifecycle shell | ✅ | Trusted local WebView host |
| LAN ESC/POS transport and raster output | ✅ | Physical printer parity still required |
| Product photo picker/storage | ✅ | Physical acceptance pending |
| Backup file import/export | ✅ | Schema v13 compatibility must remain |
| PDF/XLSX/share | ✅ | Visual/physical checks remain |
| Telegram transport | ✅ | Business trigger logic still lives in shared runtime |

## Current authority and acceptance policy

Spec 040 supersedes historical manual cutover gates below: `products` reads/writes now use Kotlin/Room with one-time durable migration; `room` is the catalog default. Full JSON remains the native source; product/category indexes are supporting structures. Spec 041 also moves layout/category ordering and posNavigation documents to Kotlin/Room. Employees and shifts are also authoritative (042–043); other business storage remains legacy. Historical descriptions of false catalog flags below describe the prior stages, not current runtime configuration. Physical checks are deferred to final comprehensive acceptance by user authorization.

## Planned migration order

### P1 — Android platform settings boundary
**Status: 🟢 Implemented, physical acceptance pending**

Move Android-specific/platform settings behind a Kotlin-owned persistence boundary while keeping the current JSON representation as a compatibility cache for backup and parity.

Initial scope:
- printer configuration mirror;
- POS notification settings mirror;
- native request/response channel for future Android settings ownership.

This stage must not change payment, order, stock or receipt persistence.

Spec 039 makes the native platform mirror ordered and disk-confirmed: a bounded Kotlin IO queue preserves accepted command order, checks commit results and closes with the Activity. Existing preferences/schema and legacy settings authority are retained. Failed mirror persistence does not fail a completed local settings save; physical restart/printer/notification parity remains pending.

### P2 — Core local persistence (Room)
**Status: 🟢 In progress — products document authoritative in Kotlin/Room (040); employees/shifts/layout/navigation also authoritative (041–043); remaining domains are shadows**

Introduce a transactional Kotlin persistence layer using Room/SQLite.

Target domains, migrated incrementally:
- products/categories;
- employees;
- shifts and cash movements;
- receipts/orders and line items;
- stock movements;
- parked/draft operational data.

Requirements:
- explicit migration from existing localStorage payloads;
- backup v13 import/export compatibility;
- idempotent migration and rollback strategy;
- no network dependency.

Current first step: Room receives a non-authoritative shadow copy of existing `prilavok_` storage writes and initial localStorage contents. The WebView/local storage contract remains authoritative.

Second step: the `products` shadow is projected into structured native `product_projection` and `category_projection` tables. These tables are still non-authoritative and exist to validate the future native catalog repository.

Third step: `MPosCatalogRepository` reads the structured projection and produces automated parity diagnostics against the mirrored legacy JSON before any authoritative cutover.

Fourth step: a parity-protected native catalog snapshot contract is exposed through `MPosCore.Catalog`, but `nativeReadsEnabled` remains `false` by default until physical acceptance.

Fifth step: `MPosCore.CatalogCutover` introduces `legacy` and `compare` modes. `compare` is the default and continuously treats legacy as the active source while allowing Room parity checks. `room` mode is explicitly blocked until physical acceptance.

Sixth step: employees are projected into structured Room storage through `employee_projection`, with `MPosEmployeeRepository` parity diagnostics. Employee UI, admin-password rules and shift logic remain on the current runtime until this projection is physically accepted.

Seventh step: shifts and nested cash movements are projected into `shift_projection` and `cash_movement_projection`, with `MPosShiftRepository` parity diagnostics. Opening/closing shifts, drawer calculations, cash deposit/withdrawal and report printing remain authoritative in the existing runtime.

Eighth step: paid receipts/orders are projected into `order_projection`, `order_line_projection` and `payment_projection`, with `MPosOrderRepository` parity diagnostics. Payment, returns, printing, stock consumption and loyalty side effects remain authoritative in the existing runtime.

Ninth step: held/parked checks are projected into `parked_order_projection` and `parked_order_line_projection`, including customer/web-order/kitchen-print state. Resume/delete/current-cart restoration and kitchen printing remain authoritative in the existing runtime.

Tenth step: warehouse stock-event history is projected from the existing `receivings` and `inventoryHistory` sources into `stock_event_projection` and `stock_event_line_projection`. The legacy POS has no standalone `stockMovements` ledger; sale-side movement evidence is already covered by order lines. Product stock remains authoritative in the existing runtime until the warehouse domain is migrated.

Under the user decision of 2026-10-06, Room may become authoritative per domain after automated migration/recovery checks; comprehensive physical testing is deferred to the end.

Spec 035 adds the transactional native storage foundation: one bounded Kotlin FIFO processes bridge operations; raw shadow and projections commit or roll back together; reads follow writes in Room transactions. Failed/rejected/pending shadow changes are explicitly reflected in catch-up metadata, and cannot produce healthy catalog diagnostics. Local POS storage remains authoritative; physical restart, large-history and backup recovery evidence is still required.

Spec 036 strengthens the backup recovery boundary: Kotlin enforces the existing 500 MB input limit during document-provider reads, before JSON parsing or image staging. Byte representation and v13 restoration semantics remain unchanged. Accepted-file memory usage and physical provider/restore testing remain open acceptance work.

Spec 037 isolates Kotlin backup image preparation from Activity/file-picker orchestration. Actual PNG decoding, v13 round-trip fields, missing/shared image behavior and staged-file rollback are automated; existing JS restoration/confirmation and Room authority remain unchanged. Physical import/cancel/finish and restart acceptance is still required.

### P3 — Native network/SSE infrastructure
**Status: 🟢 In progress — non-authoritative OkHttp transport boundary**

Move network transport from WebView to Kotlin/OkHttp while retaining existing trigger rules.

Candidates:
- WEB order SSE;
- reconnect/ACK/retry infrastructure;
- availability publication;
- media upload;
- manual catalogue synchronization transport;
- loyalty/backend transport.

Native networking must not imply automatic catalogue synchronization.

Current first step: Android owns a non-authoritative OkHttp transport boundary exposed through `MPosCore.Network`. It can describe/probe the configured HTTPS backend. Second step adds opt-in shadow SSE observation with bounded exponential reconnect backoff and event hashes/counters; observed payloads are not forwarded into WEB-order business handlers. Legacy `EventSource` remains authoritative until parity/reconnect tests are complete. Android diagnostics now fingerprint the raw legacy `event.data` and native shadow frame with SHA-256 so parity can be observed without replaying the event into business logic. Shadow SSE is paused when the Activity backgrounds and resumes from its saved diagnostic configuration on foreground; lifecycle resume preserves the diagnostic event/reconnect counters and last hash so physical parity observation remains continuous. This lifecycle behavior does not control the legacy EventSource.

#
**P3 automated gate:** specs 013–017 are now build/lint/source-test verified. Native SSE remains diagnostic-only and non-authoritative. The remaining P3 gates are physical same-stream parity, network interruption/reconnect, and background/foreground verification on an Android device; these physical cases are deferred to final acceptance; a future SSE cutover must first implement and automatically verify delivery/recovery compatibility.

Spec 038 extracts bounded Kotlin SSE framing with EventSource-compatible whitespace, line endings, empty data and default/message filtering. Only diagnostic observation changes; legacy business delivery remains authoritative. Native line/frame limits prevent unbounded accumulation. Same-stream/reconnect/physical gates remain open.

## P4 — Operational outbox and recovery
**Status: 🟢 In progress — legacy recovery journal shadow projection**

After Room + network transport:

Implemented automated recovery groundwork:
- WEB acceptance keeps the existing `prepared → local → confirmed` journal authoritative; Room shadows it and exposes ID/stage parity diagnostics.
- `currentOrderSession` is shadow-projected so split-payment/current-order restart state can be compared before any payment authority cutover.
- WEB ready now has a dedicated durable legacy journal. The backend ready endpoint is idempotent, startup/reconnect retry stays in shared JS, and Room shadows the journal for restart evidence.
- loyalty sale/reversal recovery statuses are projected from paid orders; existing idempotent JS/backend retry remains authoritative.
- native recovery projections are diagnostic-only: they do not send ACK/ready/loyalty requests or mutate operational order state.

Automated source/build coverage is complete through specs 018–030. WEB ready additionally uses a two-phase `prepared → pending → confirmed` journal so backend confirmation cannot start until the local current-order session and retry state are durably stored; physical offline/restart/online recovery scenarios remain required for final comprehensive acceptance; per-domain automated migration checks govern immediate cutovers.

**Availability policy correction (spec 033):** user decision supersedes automatic start/online/foreground publication from spec 030 on Android. After an unsuccessful or interrupted send, only the next successfully persisted payment permits another attempt. Existing stock-change/manual-sync triggers cannot bypass that gate. The Android adapter preserves shared-source checksums and existing snapshot/revision/settlement contracts; it adds no automatic catalog sync.

- durable pending network operations;
- retry for explicitly allowed operations;
- process/restart recovery;
- idempotency tracking.

### P5 — Diagnostics and update infrastructure
**Status: 🟢 Native diagnostic export implemented; physical acceptance pending. Updates remain a candidate.**

Spec 032 completes the spec 031 breadcrumb foundation with an allowlisted, metadata-only JSON report saved manually through Android's document picker. Kotlin owns filtering, bounded snapshots and IO; the Android settings adapter only requests export. No business/storage authority changes. See [migration review](NATIVE_MIGRATION_REVIEW.md) for sector priorities and unresolved business questions.

Native diagnostics:
- printer/network/storage events;
- crash/recovery breadcrumbs;
- exportable diagnostic bundle.

Native update support:
- version metadata;
- release APK update flow;
- signing/versionCode validation.

### P6 — Workspace + cart in Jetpack Compose
**Status: ⚪ Later**

Move the highest-frequency touch UI after its state boundary is stable:
- product/category tiles;
- folder navigation;
- cart presentation;
- comments/modifiers/discount interaction;
- held orders entry points.

Benefits:
- native touch/long-press behavior;
- predictable drag/drop;
- adaptive tablet layouts;
- removal of browser text-selection/context behavior.

### P7 — Payment domain + Compose payment screen
**Status: ⚪ Later / high risk**

Extract a tested PaymentEngine before moving UI.

Must preserve:
- cash/card/split;
- cash given/change;
- delivery;
- loyalty;
- stock validation/consumption;
- receipt persistence;
- printing triggers;
- restart safety.

### P8 — Shift domain + screen
**Status: 🟢 Partial (049)**

Move shift/cash movement calculations and UI after orders/payments persistence is native.

### P9 — Product/recipe domain
**Status: ⚪ Later**

Consolidate product catalogue, recipe/components, modifiers and stock-unit models in Kotlin.

### P10 — Warehouse domains
**Status: ⚪ Later**

Separate specs:
- stock core;
- purchasing;
- receiving;
- inventory.

Do not migrate this as one large rewrite.

### P11 — Loyalty
**Status: ⚪ Later**

Move only after customers/orders/products and backend transport have stable native boundaries.

### P12 — Analytics
**Status: ⚪ Later**

Move last. Prefer Room aggregate queries over scanning large JSON order arrays in JavaScript.

## Areas intentionally not targeted now

- Rewriting the entire POS in Compose at once — ⛔
- Replacing working ESC/POS native transport — ⛔
- Introducing Flutter/React Native/another cross-platform runtime — ⛔
- Changing catalogue sync semantics as part of native migration — ⛔
- Breaking iPad backup compatibility without an explicit migration project — ⛔

## Workspace persistence (041)

Kotlin owns layout and posNavigation documents through independent atomic migration markers. The common JS facade routes products/layout/navigation to native reads/writes and updates secondary compatibility caches after commit. Existing UI/normalization remains JS; Compose and financial engines are not implied by storage ownership. Final physical acceptance is deferred by user.

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

## Shift accounting correction and partial P8 migration (049)

MPosShiftAccounting now supplies native delivery cash validation. Android shift totals, return cash guards, history and print/Telegram report payloads use the same tested rules through an adapter. Sales stay in their sale shift; refunds reduce the execution shift. Full-return persistence and shift open/close commands remain JS/journal boundaries. Next: atomic Kotlin full return; retain historical stockConsumption and documented legacy fallback. Spec 049 corrects the cross-shift defect intentionally preserved in 048.

## Atomic full return (050)

Historical stockConsumption receipts now return through MPosReturnCommand: native snapshot/receipt/cash validation, historical stock restoration and one transactional product/shift/receipt/marker commit. Exact retries do not duplicate the return; restored pre-return archives reject stale acknowledgements. Legacy receipts retain the reviewed journal/current-recipe path. P7 remains partial: price/discount/recipe expansion, legacy returns, loyalty transport and UI are still outside this command.

Next boundary: native deposit/withdrawal commands, then shift open/close. Keep the 049 accounting fixtures and v13 compatibility throughout.
