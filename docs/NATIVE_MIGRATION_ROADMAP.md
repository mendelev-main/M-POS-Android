# Native Migration Roadmap

## Purpose

Move M POS Android toward a native Kotlin/Jetpack architecture only where native ownership materially improves reliability, data safety, device integration, performance or maintainability.

This is an incremental migration, not a rewrite. Until a migrated boundary is accepted, the existing reviewed HTML/JavaScript implementation remains the parity reference.

## Migration rules

1. One native boundary or cohesive module at a time.
2. Preserve offline-first behavior and existing business semantics.
3. Preserve `prilavok_` JSON compatibility and backup schema v13 until a dedicated data migration is accepted.
4. Every stage must define compatibility/rollback behavior.
5. Automated build/tests do not replace physical Android acceptance.
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

## Planned migration order

### P1 — Android platform settings boundary
**Status: 🟢 Implemented, physical acceptance pending**

Move Android-specific/platform settings behind a Kotlin-owned persistence boundary while keeping the current JSON representation as a compatibility cache for backup and parity.

Initial scope:
- printer configuration mirror;
- POS notification settings mirror;
- native request/response channel for future Android settings ownership.

This stage must not change payment, order, stock or receipt persistence.

### P2 — Core local persistence (Room)
**Status: 🟢 In progress — shadow persistence foundation**

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

Room must become authoritative only after migration and physical restart/recovery tests pass.

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

### P4 — Operational outbox and recovery
**Status: 🟡 Candidate**

After Room + network transport:
- durable pending network operations;
- retry for explicitly allowed operations;
- process/restart recovery;
- idempotency tracking.

### P5 — Diagnostics and update infrastructure
**Status: 🟡 Candidate**

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
**Status: ⚪ Later**

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
