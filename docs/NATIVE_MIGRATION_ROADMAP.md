# Native Migration Roadmap

> Детализация 109 (08.10.2026): **8/20 — 40,00%**, осталось 12. Общий детальный план: **115/129 — 89,15%**, осталось 14. Крупные этапы: **107/110 — 97,27%**. [Подэтапы, критерии и правила подсчёта](../specs/109-native-runtime/tasks.md). Проценты относятся к количеству задач; физическая приёмка 110 ещё не выполнена.


## Актуальный план и счётчик

[Реестр задач](KOTLIN_MIGRATION_TASKS.md) — **107/110 выполнено (97,27%)**
после 108. Source of truth: `kotlin-migration-tasks.json` и
`python scripts/migration-progress.py`. Это инженерные этапы; физическая
приёмка и полный переход UI ещё впереди. Исторические промежуточные gates ниже
читаются с учётом последующих спецификаций и current authority policy.

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

Spec 040 supersedes historical manual cutover gates below: `products` reads/writes now use Kotlin/Room with one-time durable migration; `room` is the catalog default. Full JSON remains the native source; product/category indexes are supporting structures. Spec 041 also moves layout/category ordering and posNavigation documents to Kotlin/Room. Employees/shifts (042–043), receipts/parked (044–045) and current session/critical journal (046) are also authoritative. Warehouse/customer and remaining operational storage still require separate boundaries. Historical descriptions of false catalog flags below describe the prior stages, not current runtime configuration. Physical checks are deferred to final comprehensive acceptance by user authorization.

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
**Status: 🟢 Partial (075)**

Atomic settlement, product-discount arithmetic, gift allocation and configured unit-price
formation and settlement recipe expansion are native. Cart additions await Room stock
preflight, quantity edits use native decisions/Room preflight, and payment entry/cash/card/split
confirmation reads stock from Room. Payment entry/confirmation also awaits a combined
Kotlin pricing/reward quote. External card confirmation is native Android UI (068); split cash input/change
calculation is native (069). Ordinary tender input and confirmation arithmetic
are native (070); initial split plans and count redistribution are native (072–073).
Split amount editing arithmetic/parser (074) and normalization/restart draft validation (075)
are native, with reviewed restart validation fallback on read/cache failure; the main payment shell still renders in WebView.
Main cart/payment UI and invalidated-preview recomputation,
online gift eligibility and stock availability display
remain reviewed JS; physical acceptance is pending.

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
**Status: 🟢 Native shift commands/report/screen/forms (052–058, 071); physical acceptance pending**

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

## Atomic manual cash movements (051)

MPosCashMovementCommand owns deposit/withdrawal drawer validation and a transactional shift/movement/marker commit. Manual movements preserve amounts, notes and existing JSON. Exact retry/reopen/backup guards prevent duplicate writes or false acknowledgements. UI stays reviewed JS; no new network effects. Current negative-drawer deposit restriction and arbitrary fractional amounts are documented for future business review. Next: shift open/close lifecycle, then native screen.

## Atomic shift lifecycle (052)

MPosShiftLifecycleCommand owns opening carryover/employee snapshot validation, closing drawer/count/counting validation and shift/projection/marker transaction. Opening uses last closed counted cash; closure accepts either sign of cash difference. Exact replay and restore guards protect lifecycle writes. Password UI, report payload generation, Telegram/print triggers and shift screen remain reviewed runtime boundaries; no new auth or network policy. Next: native shift summary/report read model, then Compose screen.

## Native shift summary / report data (053)

MPosShiftReportRepository builds compatible report/summary from one persisted Room snapshot. Active Telegram image, LAN receipt, PDF print and report dialog use native reads; pending automatic outputs coalesce and subsequent manual requests remain fresh. Existing formatters, printer copies/routing and notification gates are preserved. Scalar numeric conversion is shared with native accounting; source records stay untouched. The main shift dashboard and administrator authentication remain JS. Next: native shift screen with explicit navigation/callback/rollback integration.

## Native shift screen (054)

Kotlin Android Views now render current shift summary/movements and latest 20 history from a transactional Room model. Geometry-only WebView adapter retains navigation and delegates approved forms; no new dependency, Compose or authentication migration claimed. Obsolete replies cannot reopen a hidden screen; failed reads offer retry/explicit session rollback. P8 remains partial. Next: native cash movement and closing forms; preserve administrator opening verification and post-commit report rules.

## First-run correction (055)

Resolve absent/null shift document parity before further form migration. Lifecycle previously assumed a populated/explicit [] document; readRecords now supplies compatible empty-state semantics across native shift readers. Safe lifecycle errors and Android-only visible storage warning added. Comprehensive physical acceptance remains pending.

## Native cash input forms (056)

Visible deposit/withdrawal UI moves to Kotlin platform dialogs; typed inputs rejoin the original submitCashMovement/native command path through hidden real compatibility fields. Confirm/cancel/inputs lock pending acknowledgement, generation tokens guard replacements and unknown status requires reload. No new financial policy or network effects. JS command orchestration remains; session rollback preserves legacy forms. User reports successful first shift and backup restoration after 055. Next: closing form.

## Native shift closing input (057)

Kotlin reads a minimal current-open drawer model from Room and renders counted-cash input/loading/error/retry/fallback. Zero/fractions/difference policy retained; existing lifecycle commit and post-ack outputs remain. Cash/close/report wrappers coexist through token/dismissal guards. P8 remains partial: original submission orchestration and opening/admin UI retained. Next: review remaining P8 opening/auth boundary and native business command orchestration before declaring the domain complete.

## Native opening / employee input (058)

Room supplies minimal sorted staff and last-closed counted carryover without receipt reconstruction; native dialog owns selection/conditional transient password input. Existing administrator verifier and native lifecycle commit remain on their accepted path; no verifier/credential store added. Missing/null first-run and shared scalar carryover covered. Cash/close/open visible forms are native; P8 is still partial because shared auth/submission orchestration remains. Next high-value candidate: review P7 pricing/discount/cart totals with golden fixtures before native cutover. Physical acceptance remains pending.


059: исправлено оформление нативного экрана смены: палитра POS, карточки,
акценты и переключение light/dark. Бизнес-логика не изменена.
Спецификация: `specs/059-native-shift-theme/spec.md`. На планшете проверить
обе темы, читаемость и кнопки смены; физическая проверка пока ожидается.


060: Kotlin пересчитывает цены позиций, товарные скидки и итог перед
сохранением оплаты; расхождение откатывает транзакцию без внешних действий.
Отображение корзины, формирование цены модификаторов и распределение подарков
пока JS. Совместимость v13 сохранена; rollback: MPosNativePricingEnabled=false.
Спецификация и наблюдения по бизнес-правилам: `specs/060-native-settlement-pricing/spec.md`.
На планшете ожидаются скидки/доставка/подарки/смешанная оплата/импорт.


061: распределение подарков и снимок лояльности пересчитываются Kotlin
в транзакции оплаты перед проверкой итоговой цены. Порядок программ,
самый дешёвый товар, целые единицы и правила округления сохранены.
Сетевой guard/публикация/возвраты и UI пока прежние; v13 без изменений.
Откат: MPosNativeLoyaltyRewardsEnabled=false. Спецификация:
`specs/061-native-loyalty-reward-allocation/spec.md`.
Физические проверки пересекающихся программ, подарка со скидкой/доставкой,
отсутствия сети, возврата и импорта ожидаются.


062: цена новой позиции (база, доплаты модификаторов, ручной ввод/округление)
формируется Kotlin до добавления в корзину. Оплата проверяет базовый снимок
и доплаты; старые позиции без basePrice не переоцениваются. При ожидании
ответа добавления последовательны, оплата ждёт; отменённые/устаревшие ответы
не меняют корзину. Формы выбора/preview, merge orchestration и рецептуры ещё JS.
v13 без изменений. Откат: MPosNativeConfiguredPricesEnabled=false.
Спецификация: `specs/062-native-configured-unit-prices/spec.md`; физические
кейсы ручной цены, модификаторов, повторных нажатий/отмены/остатков/импорта ожидаются.


063: Kotlin разворачивает рецептуры/модификаторы по каталогу Room внутри
транзакции оплаты, проверяет исторический снимок и использует свой расчёт
при списании. Общие ингредиенты/дроби/порядок/допуск/циклы сохранены.
Предварительные проверки корзины, доступность и себестоимость пока JS.
v13 без изменений; rollback: MPosNativeRecipeConsumptionEnabled=false.
Спецификация: `specs/063-native-recipe-consumption/spec.md`.
Проверки на планшете ожидаются. Локальные APK не собираются по указанию
пользователя; сборку после коммита выполняет GitHub.


064: добавление обычной/модифицированной/ручной позиции и слияние количества
ожидают проверки Kotlin по Room. Нехватка сообщает ингредиент; устаревшие
ответы при изменении корзины/остатков/состава/отмене не добавляют позицию.
Проверка read-only, списание только при оплате. Степпер количества и прочие
canFulfillCart caller/preview пока JS. v13 сохранён; rollback:
MPosNativeStockPreflightEnabled=false. Спецификация:
`specs/064-native-cart-stock-preflight/spec.md`. Физические кейсы ожидаются.
Локальные APK не собираются, тесты и lint обязательны.


065: qty + delta, удаление по <=0 и проверка всей корзины выполняются
Kotlin/Room. Изменения количества и добавление идут в одной FIFO; оплата ждёт.
Просроченные/отклонённые ответы не меняют строки. Контекст заказа после
удаления последней строки степпером сохранён, как в исходнике; спорные
правила уменьшения/legacy ключей описаны в спецификации. v13 без изменений.
Rollback: MPosNativeCartQuantityEnabled=false.
`specs/065-native-cart-quantity/spec.md`; физические кейсы ожидаются.
Локальная APK не собирается; обязательны тесты и lint.


066: проверки остатков при открытии оплаты, подтверждении наличных/карты и
оплате части переведены на Kotlin/Room. Подарки и подтверждение карты сохраняют
порядок; отменённые/устаревшие ответы не запускают оплату. Проверка read-only,
финальное списание по-прежнему в атомарной транзакции. Backup v13 без изменений.
Rollback: MPosNativePaymentPreflightEnabled=false.
Спецификация: `specs/066-native-payment-preflight/spec.md`.
Автотесты и lint обязательны; локальная APK не собирается.


067: MPosCartTotalsEngine объединяет скидки/подарки/доставку в единый read-only
quote. Открытие и подтверждение оплаты/части ждут Kotlin после stock preflight;
исходные рендереры используют его только для совпадающего снимка. До quote и
после изменений общие render callers ещё JS; полная миграция UI не заявляется.
Суммы финальной транзакции проверяются теми же движками Kotlin. Backup v13
не меняется. Rollback: MPosNativeCartTotalsEnabled=false.
Спецификация: `specs/067-native-payment-totals/spec.md`.


068: подтверждение внешней оплаты картой (включая смешанную часть) теперь
нативный MPosCardConfirmationDialog. HTML этого окна не строится. Android
Views/AlertDialog, светлая/тёмная тема, отмена/Back, один callback на token.
Основной payment UI пока WebView; Compose не подключён. Запись оплаты и
внешние эффекты остаются на прежнем проверенном пути. Backup v13 без изменений.
Rollback: MPosNativeCardConfirmationEnabled=false.
`specs/068-native-card-confirmation/spec.md`; физические проверки ожидаются.


069: окно наличной части смешанной оплаты заменено MPosSplitCashDialog;
ввод/номиналы/preview/округление и проверка tender выполняются Kotlin локально,
без моста на каждое нажатие. Paid=true только после прежнего progress commit.
Backup v13 без изменений. Основной наличный keypad и экран оплаты пока WebView.
Rollback: MPosNativeSplitCashEnabled=false.
`specs/069-native-split-cash/spec.md`; физическая приёмка ожидается.


070: основной cash keypad открывает нативный редактор суммы (Done не оплачивает).
Tender и сдача обычной оплаты рассчитаны Kotlin в существующем cartTotalsRead,
без дополнительного запроса. Сохраняются zero total/epsilon 0.0001 и gift guard;
после обновления подарка выполняется свежий quote. Shell оплаты/inline quick
buttons пока WebView, Compose не подключён. Backup v13 без изменений.
Rollback: MPosNativeCashPaymentEnabled=false.
`specs/070-native-ordinary-cash/spec.md`; физическая приёмка ожидается.


071: по запросу дизайна подключены repo frontend-design и mpos-native-design
(AGENTS.md). MPosNativeTheme централизует палитру, offline Manrope, веса, кнопки
и окна. Переработаны текущая смена, история, выбор сотрудника; cash/card/close
forms получили тот же стиль. Presentation-only: финансовые/auth/storage/v13
контракты сохранены. `docs/NATIVE_DESIGN_SYSTEM.md` и
`specs/071-native-pos-design/spec.md`; физическая приёмка ожидается.

072: начальное деление смешанной оплаты — Kotlin quote (две части без
дополнительного запроса). Далее перенести normalize/edit/count с точным
сохранением оплаченных частей; затем нативный экран оплаты в общей теме.

073: количество mixed parts — native read-only FIFO, paid invariants;
074/075: ввод суммы и normalize. После каждого этапа обновлять task registry.

074: amount parser/redistribution — Kotlin, shared count/amount FIFO;
075 остаётся следующим normalize/resume draft boundary. UI keypad ещё JS.

075: normalize/draft restart validation native, compatibility validator on
read/quote/cache failure; 076 — all-cart-change totals previews. Physical pending.

076: reactive cart totals previews using 067 engine; coalesced/shared request,
matching-only money node updates; synchronous source preview fallback retained.
077/078 follow delivery/context. Physical response/performance pending.

077: delivery selection/type/gate native with shared quote status and ordered
context commands. Rate CRUD (100), customer backend (084/085) remain pending;
078 follows local order settings fields. Physical acceptance pending.

### 078 — завершён локальный контекст формы

Одно сохранение → native command → state/session/close/render. Identity/loyalty
не пересвязываются; тип/доставка завершены 077, comment только сохраняется.
Следующий 079 — hold/resume/delete; 084/085 отдельно покрывают backend клиентов.

### 079 — lifecycle commands отложенных заказов

Native append/remove/print patch + atomic Room parked/session. Backup v13 и
полные JSON/проекции сохраняются. Hold builder/kitchen delta остаются source;
printing triggers — 099, customer loyalty — 084/085. Следующий 080: товары/категории.

## Этап 080

080: правила типов/возвратов/dependency и category patch завершены. Recipe/modifier rules далее 081; формы/окончательный delete остаются 102/083.

## Этап 081

081: recipe cycle/quantities/first-error + modifier normalization/check/yield gate завершены. Editor source builder/units/UI ещё 102; следующий 082 — folders/order/move/navigation commands.

## Этап 082

082: бизнес-команды navigation/layout завершены с 52 source fixtures, UTF-16 и ack/stale/failure tests. Workspace UI/query rendering остаются 101; следующий 083 — staff/roles/auth policy.

## Этап 083 — команды сотрудников

Создание, редактирование и удаление проходят через Kotlin/Room: проверка ожидаемого списка, разрешённых изменений и актуальной смены, атомарная запись JSON и индексов. Обработчики обновляют экран только после подтверждения сохранения. Импорт v13 и откат сохранены. Проверка пароля остаётся в исходном обработчике; маркер шлюза не является самостоятельной нативной авторизацией. Завершение авторизации и нативного интерфейса сотрудников остаётся в 100 перед 109.

Сохранены правила: можно снять права с последнего администратора; сохранение сотрудника не требует смены; удаление другого обычного сотрудника требует открытой смены и исходного пароля. Физическая приёмка ожидается. Следующий этап — 084, команды клиентов. См. [спецификацию 083](../specs/083-native-employee-commands/spec.md).

## Этап 084 — клиент текущего заказа

Выбор/удаление/применение профиля рассчитываются Kotlin, сессия сохраняется через Room до обновления памяти и запроса бонусов. Серверный справочник клиентов не дублируется новой таблицей; создание/поиск пока используют действующий backend путь. Совместимость v13, WEB metadata и оплаченных split частей сохранена. 333 JS и 266 JVM тестов прошли; lint 0 ошибок, 15 прежних предупреждений. Физическая приёмка pending.

Сохранено исходное правило: выбор нового клиента оставляет прежний адрес/дополнительные поля, удаление клиента оставляет адрес, остальные поля очищает. Следующий этап — 085, нативная онлайн-проверка подарка и действующая offline policy. [Спецификация](../specs/084-native-customer-context/spec.md).

## Этап 085 — онлайн-проверка подарка

Профиль бонусов читается через OkHttp: HTTPS, deadline 5 секунд, отмена и отсутствие автоматического retry. Kotlin сравнивает запрос подарков с программами; поздний ответ для изменённого заказа/клиента не разрешает оплату. Исходный offline диалог сохраняет отмену или явное продолжение без подарка. Другие API-маршруты и запись оплаты не изменены. 343 JS / 271 JVM тестов прошли; lint 0 ошибок / 15 прежних предупреждений. Следующий 086 — начисление/отмена и durable retry. [Спецификация](../specs/085-native-loyalty-eligibility/spec.md).

Бизнес-вопрос: проверка не резервирует подарок на сервере; нечисловое truthy rewards в исходном сравнении NaN не приводит к отказу. Сохранено для parity, строгая backend схема/атомарная резервация требует отдельного решения. Физическая приёмка pending.

## Этап 086 — начисление/отмена и журнал лояльности

Kotlin строит payload и управляет claim/finish/recover; Room подтверждает sending до POST и итог до изменения памяти. OkHttp отправляет sale/reversal с deadline/cancel и без автоматического повтора. Частичное обновление отдельного чека сохраняет текущий возврат, финансовые поля и остальные чеки; для каждого send не сериализуется вся история. Четыре worker ограничивают параллельную нагрузку. Живые попытки защищены от reconnect recovery; прерванные sending становятся durable pending.

Существующие триггеры лояльности и backend idempotency сохранены. Они не меняют правило доступности 033: остатки повторяются только после следующей сохранённой оплаты. Backup v13 и статусы внутри чеков сохранены. 353 JS / 278 JVM тестов прошли; lint 0 ошибок / 15 прежних предупреждений. Физическая приёмка pending. Следующая граница — 087 WEB acceptance/ready журнал и ACK. [Спецификация](../specs/086-native-loyalty-journal/spec.md).

Сохранённый бизнес-вопрос: прямой вызов начисления может повторно отправить уже synced чек, тогда как reversal synced пропускается; обычный retry выбирает только разрешённые состояния. Не меняем эту политику и серверную идемпотентность молча. JS ещё отвечает за исходные триггеры/оркестрацию до native UI/runtime cutover.

## Этап 087 — WEB журнал и подтверждения

webOrderAcceptances/webOrderReadyJournal стали основными документами Room, с одноразовым импортом и защитой от устаревших shadow writes. Kotlin проверяет durable local/pending перед ACK и сетевое подтверждение перед confirmed; изменения разных записей объединяются без потери соседних. Ready статус сессии и pending intent сохраняются одной транзакцией. ACK идёт через OkHttp с отменой/ограничением ожидания, без автоматического transport retry.

Исправлен failure-window очистки: готовность удаляется только при confirmed в Room; события приёма очищаются по свежему Room журналу, а не по изменённому лишь в памяти объекту после failed save. V13/исходные printing/estimate/stock rules и retry triggers сохранены. Приём WEB items, кухня, UI и расписание восстановления ещё используют reviewed runtime; SSE/event ownership — следующий 088. 364 JS / 287 JVM тестов прошли; lint 0 ошибок / 15 прежних предупреждений. Физическая приёмка pending. [Спецификация](../specs/087-native-web-journals/spec.md).

Сохранённый бизнес-вопрос: кухня печатает до backend ACK; падение после физической печати до сохранения printed snapshot может повторить печать. Это отдельная внешняя граница 099, Room сам не гарантирует exactly-once принтер.

## 088 — native primary WEB SSE

Configured WEB EventSource now uses Kotlin framing, reconnect, IDs/retry, cancellation and lifecycle, with single-event bridge backpressure. Reviewed order/owner-report handlers remain; webEvents business/storage authority is not claimed. Diagnostic shadow remains separate. Next: 089 manual catalogue/media transport. See [088](../specs/088-native-web-sse/spec.md).

## 089 — manual catalogue/media transport

Kotlin owns exact configured menu/media HTTP routes, unchanged JSON bytes, status/strict parsing, cancellation and bounded no-retry transport. Reviewed payload/business/UI and local-image update conditions remain. Next 090: availability publication and durable payment-only gate; reconcile older 033 ordinary-success triggers with current AGENTS 7. See [089](../specs/089-native-catalog-transport/spec.md).

## 090 — native availability, durable payment gate

Native Room catalogue calculations and one-use per-receipt permit precede OkHttp publication. Current AGENTS 7 replaces historical 033 ordinary-success stock/manual triggers; only post-commit payments publish, with no restart/online/foreground replay. Internal revision/ticket metadata remains outside v13, compatible revision is retained. Next 091: suppliers business commands and local storage. See [090](../specs/090-native-payment-availability/spec.md).


## 091 — suppliers

Native supply JSON ownership and expected/candidate supplier commands; persisted admin/open-shift delete gate, source create/edit rights retained explicitly. Next 092 purchase-order commands. [091](../specs/091-native-supplier-commands/spec.md).


## 092 — purchase commands

Native purchase lines/unit rounding and authoritative create/delete candidate verification, atomic products/order or order/audit history, supply ownership expanded to purchaseOrders/receivings. UI preview/export remain reviewed, receiving business is 093. [092](../specs/092-native-purchase-commands/spec.md).


## 093 — receiving confirmation

Native invoice/unit/valuation/shortage arithmetic and expected-state command verification, atomic stock/cost/order/history/draft clearing in Room. Preview/form presentation remains reviewed JS; 094 migrates draft business operations. [093](../specs/093-native-receiving-command/spec.md).


## 094 — receiving drafts

Native open/save/restoration from Room, legacy quantity/total defaults and captured cart, incomplete marker before rendering. Partial drafts retain their fields without invoice validation; source form presentation/rollback remain. Next 095: inventory recount/differences/atomic application. [094](../specs/094-native-receiving-drafts/spec.md).


## 095 — inventory recount and atomic application

Authoritative native fixation/difference/loss calculation and atomic stock/draft or history/config/clear; later sales survive completion. Inventory JSON ownership/projector keeps v13. Input/start/cancel/calendar/summary preview remain presentation/runtime compatibility; native command validates committed values. Next 096: warehouse period reports. [095](../specs/095-native-inventory-commands/spec.md).


## 096 — native warehouse model

Calendar periods/DST and movement/valuation/warning/supplier model read from authoritative Room sources. Existing page/export/monthly handlers await model, preserve role/send policy, capture export choices and reject stale renders. Android ICU RU ordering matches source. Presentation/unit grouping remains JS; historical estimates never mutate stock. Next 097 analytics. [096](../specs/096-native-warehouse-reports/spec.md).


## 097 — native sales analytics

Room-backed Kotlin aggregates and calendar bounds preserve raw receipt/payment/refund semantics, named groups and stock valuation. Read returns aggregates/count only; visible analytics updates its own screen and retains date focus/scroll, hidden tabs no longer scan receipts/prefetch loyalty. JS formatting/admin visibility/central loyalty remain; 107 owns full UI. Next 098 hall/bookings. [097](../specs/097-native-sales-analytics/spec.md).


## 098 — authoritative hall commands

Hall tables/bookings now use Room-owned full JSON and native commands with current-state conflict validation/CAS. Deleting a table cascades bookings atomically, without altering receipt/order snapshots. Reviewed permissions, cancelled statuses, local/DST windows and touching intervals remain. UI acknowledgement, temporary drag restoration, rollback handlers and v13 are preserved. Full hall UI remains 108; next business stage is 099 printing jobs/triggers. [098](../specs/098-native-hall-commands/spec.md). Physical pending.


## 099 — native print jobs and routing

Kotlin owns routing, exact category/flag rules, copies, per-endpoint FIFO and bounded admission/concurrency. Room stores compact outcomes before/after send; the TCP transport closes on lifecycle teardown. Completed payment prints read authoritative receipts. Reviewed trigger timings and manual reprints remain; settings still come from the authoritative compatibility snapshot until 100. User explicitly excludes automatic retry after connection breaks. Restart/import never replay queued/uncertain jobs; physical confirmation is not inferred from flush. Rollback routes through legacy planning with the new native direct transport. Next 100: native settings/employees UI. [099](../specs/099-native-print-jobs/spec.md). Physical pending.

## 100 — native settings/employee surfaces

Native hubs/forms use shared Manrope palettes and compact wrapping actions. Printer/notification authority cutover compares full expected snapshots and acknowledges disk persistence before memory/UI/test printing; delayed mirrors cannot overwrite it. v13 restore awaits this boundary. Mounted reviewed field/button tokens preserve employee auth/rights and other settings handlers without evaluating code or copying verifier constants. Presentation rollback retains native preference ownership. The reviewed DOM/auth runtime and fire-and-forget company/delivery/discount writes remain explicit compatibility boundaries; independent native auth is required before 109. This is a transitional UI architecture, with no measured performance claim. Next 101: native POS workspace/catalog/folders/cart. [100](../specs/100-native-settings-employees/spec.md). Physical pending.

## 101 — native normal-operation workspace

Native Kotlin tiles, source grid coordinates/spans, toolbar, folder browsing and cart/total/actions use shared palettes/Manrope. Reviewed mounted targets and known handlers retain native business command ownership; old/detached/disabled/forged callbacks cannot alter a new context. Matching native quote updates replace presentation without redoing money formulas; catalogue/cart scroll is retained where context matches. Layout drag/edit and configuration/customer/modifier/manual-price/payment/parked modal boundaries explicitly keep reviewed presentation and hide the overlay. Removal keeps cart-line identity. These residual presentation/DOM/auth boundaries must be resolved before 109; no measured speed/physical acceptance claim. Next 102 product/recipe/modifier editor. [101](../specs/101-native-workspace/spec.md).

101 verification: 463 JS / 384 full JVM passed; final workspace checks 5/5 after recovery-caption adjustment. Lint 0 errors / 15 existing warnings. No product APK assembled; physical acceptance pending.

## 102 — native product, recipe and modifier editor

Kotlin expanded editor/contextual forms cover all reviewed sections and mounted actions. Stable node identities, whole-draft assignment before events, native patches retaining focus/cursor/scroll, live search and sampled cancellable photo previews preserve editor interaction. Existing native 080/081 validation is retained. Dirty-exit saving locks follow modal replacement and propagate recovery feedback. Shared selected-section styling and both palettes match POS. Reviewed units/configuration/auth/delete/saveKey/photo runtime remains explicit compatibility until 109; no measured performance or physical-acceptance claim. Next 103: main payment surface. [102](../specs/102-native-product-editor/spec.md).

102 опубликован в main: после успешной отправки в резервную ветку повторное обновление main прошло. Ошибка GitHub устранена; дополнительная ручная публикация не требуется.

## 103 — native payment workspace

Main payment/receipt preview, tender/quick values/change, split parts, explicit card terminal confirmation, offline loyalty decision and completion receipt use native presentation. Mounted opaque node/token actions retain business ownership; async stock/reward/progress/settlement locks survive context replacement. Hardware back waits for reviewed split-exit refusal. Existing native cash/count/amount forms stay integrated. User explicitly retained the source ordinary-card-cancel → empty split behavior. Source formatting/DOM/payment compatibility persists until 109, no measured performance/physical acceptance claim. Next 104: receipt history/details/refunds. [103](../specs/103-native-payment-screen/spec.md).

## 104 — native receipt history/details/full returns

Native history retains 50-row Room paging, loading/error/retry, mounted selection/print identity. Kotlin provides independently scrolling responsive history/detail panels and full return confirmation/result. Existing 050 return authority and saved stock restore, source legacy fallback, current-shift cash accounting and bank-terminal refund semantics remain. Unknown local result blocks resubmit; native presentation rollback does not roll back storage authority. Web compatibility remains 109; physical acceptance 110 pending. [104](../specs/104-native-receipt-history/spec.md). Next 105 — parked orders/customer/loyalty presentation.

## 105 — native parked/customer/loyalty surfaces

Park label/list/resume/delete, customer picker/search/create/profile/gift and loyalty administrator program/client screens/editor/adjustment use shared Kotlin presentation. Source permissions, persisted command authority, gift allocation and outbox rules remain. Mutating/navigation promises are awaited even when native command adapters replace source functions after UI initialization; read search remains editable. Cashier stale-response guards preserved. Native overlay geometry patches keep stable fields/focus on keyboard resize. [105](../specs/105-native-parked-customer-loyalty/spec.md). Next 106 — warehouse/purchases/receiving/inventory; compatibility DOM removal 109 and physical acceptance 110 remain.

## 106 — native warehouse/purchase/receiving/inventory presentation

Shared Kotlin cards, date fields, recycled report tables and original mounted actions cover warehouse reports/export, suppliers, purchase quantities/history/detail/share, receiving drafts/confirmation/history and inventory configuration/recount/summary/cancel. Existing 091–096 persisted authorities and formulas remain; receiving Back waits for draft persistence. Inventory completion preserves sales after item fixation. Cancellation still removes draft without stock rollback; source text ambiguity pending clarification. [106](../specs/106-native-warehouse-screens/spec.md). Next 107 analytics, 108 hall, 109 compatibility DOM removal; physical acceptance 110 remains pending.

## 107 — native analytics presentation

Native responsive KPI/chart/date/preset/loading/retry screens retain 097 Room calculation authority, reviewed formatting/admin visibility and loyalty reads. Hidden cashier employee amounts are omitted from native model/accessibility. Charts recycle visible rows; picker focus defers async DOM replacement and releases only a still-current period result. User explicitly retained reversed inline-date state/warning semantics. [107](../specs/107-native-analytics-screens/spec.md). Next 108 hall/bookings; 109 compatibility DOM removal and 110 physical acceptance remain pending.

## 108 — native hall/tables/bookings presentation

Native relative floor map, table select/edit-only drag, table forms/menu, booking date/cards/create/edit/cancel and time/guest controls retain 098 native Room commands and reviewed rules. Drag runs through the original pointer boundary, restores durable baseline before native persistence and consumes the browser-style suppressed click once. Source shape/rotation/blue/amber states and offline Manrope palettes remain. Failure/duplicate/uncertain outcomes preserve native command safeguards. [108](../specs/108-native-hall-bookings/spec.md). Next 109 removes active WebView compatibility, while 110 physical acceptance and 111 update metadata remain.

## 109: native runtime — in progress

Native Room snapshot, current-order restore, workspace routes, paired shift/employee bootstrap and native opening command are connected to production paths. WebView, DOM screen models and remaining JS orchestration are still active. See [spec](../specs/109-native-runtime/spec.md). Completion remains 107/110; physical acceptance pending.

109 session increment: Kotlin now projects restored current-order fields on production startup; existing split-draft validation and print marks preserved, no print retry. Reviewed rollback remains. WebView/navigation removal pending; 107/110 completed.

109 workspace increment: Kotlin category/folder/Back/edit decisions now active; FIFO and stale-view guards preserve current state. No business document writes. Search/tab/rendering and WebView removal still pending. Engineering completion: 107/110.

109 active-session bootstrap: production startup now reads owned shifts/employees together and uses Kotlin normalization. Native first-match active-session model prepared; synchronous JS role helpers still remain until native handlers replace them. Read-only, rollback and v13 retained. Overall: 107/110; WebView removal pending.

109 opening authority: default native form verifies the reviewed administrator
credential and creates the shift directly in Kotlin/Room through the bounded
FIFO. Credentials stay outside the WebView; expected live documents, carryover,
journal and transactional lifecycle checks remain. JS screen/post-commit effects
and other authentication remain until subsequent 109 increments. Explicit
MPosNativeShiftOpenCommandEnabled=false rollback; v13/keys unchanged. Physical
acceptance pending, engineering progress remains 107/110 (97.27%).

109 opening verification: 551 JS / 438 JVM passed, 0 failed/errors/skips; lint
0 errors / 22 warnings in unchanged files (7 are online dependency advisories).
No local product APK assembly; physical acceptance pending. Progress 107/110.


109.06 завершена: Kotlin выбирает текущую смену/сотрудника и задаёт последовательность recover → hydrate → activate → ready. Повторный запуск/импорт сериализованы; старые поколения и ответы отбрасываются. Изменение root-документов во время загрузки блокирует активацию, следующий запуск читает свежую сессию. После root-операций выбранные записи обновляются до acknowledgement, без повторной передачи истории. Foreground читает свежий root через FIFO; команды по-прежнему проверяют права в своих транзакциях. 6/20 внутри 109; 113/129 детально; 107/110 крупных этапов. Следующая задача — 109.07. JS-адаптеры исполнения recovery/hydration/effects остаются в 109.09–109.18; WebView удаляется в 109.19.

## Исправление обратной связи интеграционных тестов — 08.10.2026

[Telegram / WEB / LAN-print](CONNECTION_TEST_FIX_RU.md): corrected callback, correlated terminal result, bounded wait, duplicate/stale guards. 572/572 JS, 457/457 JVM; lint 0 ошибок / 22 предупреждения. Физическая проверка pending. Счётчики после независимого завершения 109.06: 107/110, 109 6/20, детальный план 113/129 — 87,60%; исправление подключений не закрывает новые подэтапы.

109.07 — нативная авторизация сотрудников подключена: JS authorization marker не разрешает смену роли/удаление; пароль вводится в MPosEmployeeAuthorizationDialog и проверяется в транзакции MPosEmployeeCommand. Ordinary create/edit, last-admin demotion, self/admin deletion guards и supplier permissions сохранены. Проверки: 575 JS / 464 JVM; lint 0 ошибок / 15 прежних предупреждений. Полные критерии 109.07 ещё не выполнены: editor/product/category delete и settings gates остаются. Прогресс без увеличения: 6/20 внутри 109, 113/129 детально; физические кейсы pending.


### 109.07: реквизиты организации — 08.10.2026

`MPosCompanyCommand` сохраняет реквизиты в одной Room-транзакции с проверкой
актуальной роли администратора, открытой смены и ожидаемого документа.
Понижение сотрудника, закрытие/смена смены и устаревшая форма отклоняют запись.
JS обновляет состояние только после подтверждения commit; неопределённый
результат по таймауту блокирует повтор до перезапуска, автоматического повтора нет.
Ключ `company` принадлежит Room через существующий workspace storage; старый
shadow не перезаписывает его. Импорт/экспорт v13, JSON null/отсутствие и неизвестные
поля сохраняются. Ручное редактирование по прежней логике оставляет четыре поля.
Флаг `MPosNativeCompanyCommandsEnabled=false` возвращает прежние обработчики,
сохраняя нативное хранилище. Внешний вид формы не менялся.

Добавлены JS/JVM проверки подтверждения записи, прав, конфликта данных,
таймаута и отката Room. **Локально не запускались** согласно AGENTS.md §17;
результат проверяется в GitHub Actions (см. `docs/CI_AND_APK_SIGNING_RU.md`).
Физическая приёмка ожидается. 109.07 остаётся в работе: следующие границы —
защищённые операции редактора/категорий и остальные настройки.
Прогресс: **107/110 крупных этапов; 6/20 внутри 109; 113/129 детальных задач**.


### 109.07: удаление каталога и разрешения редактора — 08.10.2026

Подключены `MPosCatalogDeleteCommand` и `MPosProductEditorCommand`.
Удаление товара/пустой категории, плиток и ссылок навигации атомарно: ошибка
любого сохранения откатывает всю операцию. Защита чеков для возврата,
использования в составе и непустых категорий читает актуальную Room.
Администратор открытой смены удаляет товар без пароля; остальные — с паролем;
категория всегда требует пароль, включая администратора. Смена не требуется
при удалении с правильным паролем — существующее разделение сохранено.

`MPosEditorAuthorization` принимает пароль только из нативного поля.
Разрешения stock/no-stock временные, привязаны к товару и исходному документу,
не входят в Room/backup и теряются после перезапуска. Поддельный JS-флаг не
разрешает запись. Сохранение карточки повторно проверяет роль, документы,
защищённые остатки/конфигурацию/WEB и политику смены типа внутри транзакции.
Разрешение погашается после commit; при известном откате сохраняется для
исправления формы. Таймаут запрещает повтор до перезапуска.

Сбор полей, рецептов/фото и последующие эффекты остаются адаптерами, относящимися
к 109.09/109.16–109.18. Новый точечный hook сохранения в product-persistence.js
обратим; sync проверяет границу до замены файлов, parity-тест восстанавливает
только точный hook/timeout guard перед сверкой исходного SHA. Импорт и backup
v13 используют существующее хранилище без editor grants. Явные флаги rollback:
MPosNativeCatalogDeleteEnabled, MPosNativeEditorAuthorizationEnabled,
MPosNativeProductEditorCommitEnabled (false возвращает прежнюю границу).

Написаны проверки native transaction rollback, смены роли, stale documents,
поддельных/устаревших grants, таймаутов и UI acknowledgement. **Не запускались
локально:** GitHub Actions выполняет JS/JVM/lint по AGENTS.md §17.
Планшетная приёмка pending. Остались settings gates, WEB toggle товара и
защищённые операции лояльности; 109.07 остаётся in_progress.
Счётчик не завышен: **107/110; 109 — 6/20; детально 113/129 (87,60%)**.


### 109.07: реализация завершена — 08.10.2026; CI pending

Оставшиеся настройки backend/Telegram и WEB toggle товара проверяют актуальную
роль нативно перед Room commit. Панель администратора, реквизиты и защищённые
проверки Telegram/WEB читают свежие права перед действием. Backend-test и ручной
catalogue sync ждут acknowledgement настроек; автоматической синхронизации нет.
Network/telegram сохраняют исходные JSON/v13 и неизвестные поля при импорте;
первый save network сохраняет уже созданный startup device key. Редактирование
Telegram по прежней логике заменяет поля и сохраняет lastMonthlyWarehouseSent.

Корректировка лояльности использует нативный ввод пароля и HTTPS, прежний endpoint,
поля и серверную проверку. Идентификатор/имя администратора и подключение берутся
из свежей Room, а не из JS. Пароль не входит в bridge, журналы и backup. HTTP
запрос выполняется после закрытия DB-транзакции вне storage FIFO; timeout 5 секунд,
автоматического повтора нет. Создание/редактирование поставщиков и незащищённые
действия сохраняют прежние права; права лояльности на сервере не ужесточены.

**Согласованное пользователем правило:** после неподтверждённой корректировки
повтор возможен только после проверки актуального баланса. Нативная отметка
mpos_loyalty_adjustment_verification_v1 создаётся до запроса, сохраняется при
обрыве/timeout/5xx и после перезапуска. Она отдельно от кассового recovery и не
блокирует продажи. Успешный ответ/явное отклонение снимают её; при неопределённом
результате только успешный нативный GET баланса конкретной программы позволяет
вернуться к ручному вводу. Форма показывает актуальный прогресс/подарки и сбрасывает
дельты; проверка баланса не отправляет корректировку. Старый token не снимает
отметку новой операции. Отметка не содержит пароль/токен Telegram/device key;
внутренний служебный ключ не меняет формат бизнес-данных backup v13.

Rollback flags: MPosNativeAdminAccessEnabled, MPosNativeAdminSettingsEnabled,
MPosNativeProductWebEnabled, MPosNativeLoyaltyAuthorizationEnabled. Прежние
обработчики доступны явно до приёмки. Подготовленная, но уже закрытая нативная
форма не запускает поздний финансовый HTTP запрос.

Критерии реализации 109.07 закрыты; **новые проверки не запускались локально**,
результат GitHub Actions и физическая приёмка ещё не подтверждены. Это завершение
инженерной задачи, а не заявление об успешных тестах или полном уходе от WebView.
Прогресс: **107/110 крупных; 7/20 внутри 109; 114/129 детально (88,37%)**.
Далее 109.08 — нативное состояние навигации, выбора разделов, поиска и Back.

### 109.08 начата: владелец выбранного раздела

`MPosWorkspaceNavigationOwner` владеет tab и revision в StateFlow на время Activity.
Первое initialize принимает стартовый раздел; повторная инициализация не заменяет
нативный выбор. `setTab` отправляет прямую команду без DOM target, и только ответ
меняет совместимую JS-проекцию и вызывает render. При быстрых кликах старый ответ
не возвращает интерфейс к предыдущему разделу. Повторный выбор не создаёт историю
Back и не увеличивает revision; поиск/корзина/смена не меняются. Таблица разделов
не ужесточена: неизвестные строки сохраняются как в reviewed handler. Нестроковый
legacy input и явный MPosNativeWorkspaceNavigationEnabled=false используют rollback.

Проверки StateFlow, повторной инициализации, быстрых кликов и изоляции заказа
написаны, локально не запускались — выполняет Actions. 109.08 в работе:
поиск, выбор/Back, нативные controls/read models и остальные DOM route targets
ещё остаются. **114/129; 109 — 7/20; 107/110 крупных**, физическая приёмка pending.


### Исправление проверок и продолжение 109.08 — 08.10.2026

По запросу пользователя воспроизведены проверки Actions локально без сборки APK:
GitHub API вернул Forbidden, поэтому результат исходных удалённых запусков
не установлен. Найдены семь JS-сбоев тестовых окружений: отсутствовали
currentShiftEmployeeIsAdmin и criticalStorageRecoveryPending; тест ручного
menu sync не ожидал асинхронного сохранения сетевых настроек. Исправлены fixture
и ожидание создания HTTP-запроса, исходные assertions сохранены. После
исправления 626/626 JS и 2/2 Python проверок версий прошли.

В 109.08 добавлен нативный запрос поиска в MPosWorkspaceNavigationOwner:
первичная инициализация сохраняет query, повторная не заменяет его; изменение
только query увеличивает revision, повторное значение — нет. Таблицы и поиск
не изменяют корзину/смену/stock и не сохраняются в v13. JS сохраняет прежнее
String(value||'') и передаёт подтверждённый запрос reviewed-фильтру плиток.
Поздние ответы после нового ввода, смены раздела/категории/папки либо очистки
поиска не меняют экран. Explicit rollback сохраняет исходный onSearch.

Полный JS-набор после продолжения: **630/630**, JVM **509/509**, без ошибок
и пропусков; lint **0 ошибок / 15 предупреждений**, Gradle BUILD SUCCESSFUL.
Это локальное воспроизведение CI по прямому запросу пользователя; результат
нового удалённого запуска Actions ещё не подтверждён. APK не собиралась. DOM-фильтрация и переходы category/folder/Back ещё
сохраняются: это частичный шаг 109.08, а не завершение этапа.
Прогресс без увеличения: **114/129**, внутри 109 **7/20**, крупных **107/110**.
Физическая приёмка остаётся pending.


### 109.08 — владелец маршрутов workspace, 08.10.2026

Kotlin владеет category/folder/edit состоянием. Подготовка перехода не меняет
выбор; одноразовое подтверждение проверяет актуальность owner, а для папки —
согласованные текущие products/posNavigation из Room. Сохранены FIFO и порядок
Back (окно папки → inline folder → root), очищение поиска и edit effects.
Обычные переходы не перечитывают каталог; бизнес-документы и v13 не меняются.
Явные rollback-флаги сохранены. Новые проверки написаны для Actions, локально
не запускались. Предыдущий 98f5f73 подтверждён успешным Actions 37744808114.
Этап 109.08 ещё в работе: native controls/read models, Android Back, DOM
route targets и согласование нового runtime остаются. Поздний accept
отменяет только свой выбор, сохраняя более новые tab/query. **114/129**, 109 **7/20**.


### 109.08 — системный Back и restart/import, 08.10.2026

Прежний порядок системного Back теперь выбирает Kotlin: import/modal/warehouse/
receiving/background после native settings. Повторное нажатие и поздние ответы
после замены окна/pause/destroy не выполняют старые эффекты. Root startup
сбрасывает временного владельца навигации; только ready разрешает новый выбор,
старые tab/search/route ответы не возвращают предыдущий runtime.
DOM presence и экранные close handlers пока остаются переходными адаптерами.
Тестовый FIFO fixture исправлен; Actions 37747195877 для 3f87071 успешен
(tests/lint и APK). Новые Back/runtime проверки ожидают следующий Actions.
Этап 109.08 не закрыт; **114/129**, внутри 109 **7/20**.


Проверка кода 8ca9dd6: [Actions 37748093813](https://github.com/mendelev-main/M-POS-Android/actions/runs/37748093813) успешно завершил job Tests and lint (JS, правила версий, Kotlin и lint). Подписанная APK собирается отдельным job. Локальные тесты/сборка не запускались; физическая приёмка pending.


### 109.08 — нативная панель workspace, 08.10.2026

Добавлены MPosWorkspaceToolbarModel и toolbarView: заголовок, «Назад»,
«Раскладка» и «Закрыть папку» формируются из Kotlin owner; имя папки — из
актуального Room posNavigation. Эти кнопки отправляют типизированную команду
непосредственно в Kotlin через FIFO хранилища, без нажатия скрытого HTML узла.
Revision и expected state защищают от старой панели и двойного перехода;
неприменённый ответ отменяет только свой принятый маршрут. Документы товаров,
корзины, оплат и смен не записываются. Категория без папки не читает Room;
модель панели кешируется между изменениями заказа, сбрасывается при навигации
и смене runtime. Сетка теперь исключает hidden плитки текущего JS фильтра.

Используется прежний MPosNativeTheme/Manrope, светлая/тёмная палитры и 48dp
кнопки. При ошибке модели остаётся reviewed presentation; явный rollback —
MPosNativeWorkspaceToolbarEnabled=false. Написаны проверки модели/Room,
типизированного нажатия, устаревшего ответа, оплаты во время чтения панели,
hidden плиток, кеширования и rollback. Локальные тесты и APK не запускались;
выполнение этого изменения ожидается в Actions, физическая приёмка pending.

109.08 остаётся in_progress: общие вкладки/поле поиска и controls редактора
раскладки ещё используют DOM, global Back ещё получает presence из адаптера.
Каталог/корзина read models относятся к 109.09. Прогресс не увеличен:
**114/129 (88,37%)**, 109 **7/20**, крупных **107/110**.

Проверка toolbar-кода `7bd7ecc`: [Actions 37750512318](https://github.com/mendelev-main/M-POS-Android/actions/runs/37750512318) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK собирается отдельным job; физическая приёмка pending.


### 109.08 — исправление подключения native UI, 08.10.2026

Проверка по сообщению с планшета выявила дефект: приложение объявляет `let state`,
а workspace/settings UI читали `window.state`. В браузере эти значения различны;
моки прежних тестов помещали state в window и пропускали дефект. Адаптеры workspace,
settings UI, platform settings и employee confirmation теперь выбирают lexical
state, сохраняя property fallback для совместимости. Regression fixtures используют
`let state` без window.state: включение экрана/кнопок, граница подтверждения принтера,
тема и подтверждение сотрудника. Это исправление интеграции, не новый закрытый этап.

Экран смены больше не исчезает при открытии нативного cash Dialog: фон остаётся
нативным, кнопки блокируются до закрытия формы, повторное нажатие не вызывает действие.
Hidden compatibility overlay отличает native Dialog от видимого HTML modal;
для видимого HTML modal native screen скрывается, чтобы не перекрывать окно.
Ошибка открытия возвращает управление; смена раздела/rollback/скрытие приложения
по-прежнему скрывают экран. Добавлены JS и native view проверки фонового слоя,
блокировки, возврата и stale replies. Формулы и сохранение cash movement не изменены.

Tests authored, GitHub Actions pending; локальные тесты/сборка APK не запускались.
Физическая проверка исправлений pending. Прогресс **114/129 (88,37%)**, 109 **7/20**;
109.08 остаётся in_progress, следующие controls — вкладки/поиск и DOM route targets.


### 109.08 — типизированный выбор категории с native плитки

Native category tile теперь передаёт openCategory + стабильный ID прямо в Kotlin
через ту же FIFO/revision проверку. Mounted HTML click handler не вызывается;
название карточки не подменяет ID. Прежние trim, очистка поиска/folder и выход из
editMode сохранены; бизнес-документы не записываются. Старый view token и повторное
нажатие отклоняются. Product/folder tiles и явный rollback пока используют reviewed
handlers; их read models ещё впереди. Написаны JS, Room и native view проверки,
выполнение следующего коммита в Actions pending. 109.08 in_progress; **114/129**.

Исправление `8855327`: [Actions 37752933255](https://github.com/mendelev-main/M-POS-Android/actions/runs/37752933255) — Tests and lint успешно (JS, Kotlin, lint); APK отдельным job, проверка на планшете pending.

Native category tile `a645d39`: [Actions 37753269784](https://github.com/mendelev-main/M-POS-Android/actions/runs/37753269784) — Tests and lint успешно. APK исправления `8855327` уже опубликована как Actions artifact M-POS-Android-release-0.1.173-1; категория собирается отдельным job. Физическая проверка pending.

## Стабильное обновление рабочей зоны — 08.10.2026

[Подробности и проверка](NATIVE_WORKSPACE_SMOOTHNESS_RU.md): обновления остатков/количества/итогов и строк корзины сохраняют Views; действие/token обновляются без пересоздания сетки. Cash/card/secondary/outline передаются в shared theme; busy блокирует повторы без серого мигания рабочих кнопок. Новые тесты выполняет Actions, физическая плавность pending. Новых завершённых migration IDs нет: 114/129 (88,37%), 109 — 7/20.


## Рабочая зона — визуальный паритет iPad, 08.10.2026

См. [перенос исходной раскладки](NATIVE_WORKSPACE_IPAD_PARITY_RU.md). Изменения ограничены рабочей зоной; CI и физическое сравнение pending. Новых завершённых этапов нет: 114/129 (88,37%), осталось 15; 109 — 7/20.


### 109.08 — открытие папки с native плитки (08.10.2026)

Folder tile передаёт openFolder + ID непосредственно в Kotlin FIFO, без вызова
mounted HTML click. Repository проверяет expected revision и актуальные products/
posNavigation из Room при подготовке и принятии маршрута; удалённая/неизвестная
папка отклоняется. Обычная hydration уже инициализирует оба owned документа;
навигация не создаёт authority markers и не перезаписывает документы из UI.

Ответ renderFolder проверяется до изменения JS projection: category должна
совпадать с выбранной, ID — строка. После принятия остаётся прежняя category,
поиск очищается, открывается folder modal; stale/некорректный ответ отменяет только
свой маршрут. Back закрывает папку без выхода из категории. Shared appearance и
retained Views актуального main сохранены. Product tile/renderer/read models пока
сохраняют compatibility path (109.09). Добавлены JS/Room/native view проверки;
Actions следующего коммита pending, физическая приёмка pending.

109.08 in_progress: native tabs/search controls, редактор раскладки и global Back
presence остаются. Прогресс **114/129 (88,37%)**, 109 **7/20**.

Проверка folder-кода `c7e976a`: [Actions 37764445102](https://github.com/mendelev-main/M-POS-Android/actions/runs/37764445102) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK отдельным job; физическая приёмка pending.


### 109.08 — нативные основные вкладки верхней панели, 08.10.2026

MPosWorkspaceHeaderModel формирует reviewed destinations/labels и selected state
из Kotlin owner: M POS, Заказы, Приёмка, Чеки, Аналитика, Бронирования, Настройки.
MPosWorkspaceHeaderController отправляет selectHeaderTab прямо через native FIFO,
без нажатия HTML-кнопки. CAS по expected state/revision и token подтверждают
переход; discard отменяет только свой выбор, сохраняя новый search и последующие
явные selections (в том числе повторный выбор той же вкладки). Restart/import
сбрасывает outstanding selection. Native completion не разблокирует новую команду.
Header read не читает Room/catalog и не создаёт authority markers или документы.

Native controls используют navy/Manrope/selected pill и 48dp touch targets;
геометрия трёх групп ещё согласуется с source topbar. События, напоминание inventory
и shift pill остаются под их reviewed domain handlers. При оплате, редакторе,
модальном/складском overlay панель скрывается. Если source topbar требует внешнего
горизонтального скролла (группы за viewport), сохраняется исходный scroller;
при подходящей геометрии native controls возвращаются автоматически. Это временная
граница presentation, не утверждение полностью нативной верхней панели.

Rollback: MPosNativeWorkspaceHeaderEnabled=false возвращает исходные кнопки;
MPosNativeWorkspaceNavigationEnabled=false также выключает native header.
Добавлены Kotlin owner/model, Room boundary, Android View и production lexical
state JS проверки: busy/stale/runtime/late read, несовпавший DOM, modal/payment,
source rollback и narrow→wide. Локальные тесты/lint/APK не запускались; Actions
следующего коммита pending. Физическая оценка тем/портрета/font scale pending.

109.08 остаётся in_progress: поиск/filter controls, shift-tab control, controls
редактора раскладки и global Back presence ещё впереди. Domain/catalog read models
и удаление presentation geometry относятся к дальнейшим runtime этапам.
Прогресс **114/129 (88,37%)**, внутри 109 **7/20**, крупных **107/110**.

Fixed header `b0196c5`: [Actions 37767655391](https://github.com/mendelev-main/M-POS-Android/actions/runs/37767655391) — Tests and lint успешно (JS, правила версий, Kotlin, lint). Адаптер зарегистрирован в sync-pos-assets и parity fixtures; первый run 37767548247 до регистрации упал на JS проверках. APK отдельным job; физическая проверка pending.


### 109.08 — фильтрация текущих плиток в Kotlin, 08.10.2026

Live onSearch передаёт только типы/ID текущих sections-wrap плиток в
selectFilteredSearch. MPosWorkspaceSearchModel ищет по названиям авторитетного
каталога Room: trim по JavaScript whitespace, русский lower-case, substring,
строго строковые ID и первое совпадение getProduct. Пустой запрос показывает
все плитки, включая папки и отсутствующие товары; при непустом папки/категории
скрываются. Область поиска не расширена до всего каталога или folder modal.
Render-time фильтрация renderPosScreen остаётся в 109.09, нового search UI нет.

Одна native команда возвращает query/token/visibility. JS только проецирует
подтверждённую маску, без originalSearch и повторного render. Проверяются runtime,
последний запрос, прежний query/context, identity grid/плиток и type/ID. Stale или
некорректный ответ отменяет только свой query; новые query, routes, tab и import
не откатываются. Отмена последнего из нескольких неподтверждённых запросов
возвращает последний спроецированный query. Ошибка authority/модели не меняет query.

Repository сохраняется на время native FIFO; индекс имён перестраивается только
при изменении сырого Room catalog document. Каждая непустая фильтрация проверяет
текущий документ и authority; пустой запрос/отсутствующий grid не требуют SQL.
Данные каталога, корзина и финансовые документы не записываются. Rollback через
MPosNativeWorkspaceNavigationEnabled=false сохраняет reviewed onSearch.

Добавлены shared reference/Kotlin fixtures (русский текст, Unicode, whitespace,
строгие ID, дубликаты и coercion имён), Room freshness/no-writes/authority,
query token/runtime и JS stale/malformed projection проверки. Локальные тесты,
lint и APK не запускались (§17 AGENTS); GitHub Actions следующего коммита pending.
Физическая приёмка pending. 109.08 in_progress: native search presentation,
shift-tab control, редактор раскладки и global Back presence остаются.
Прогресс **114/129 (88,37%)**, внутри 109 **7/20**, крупных **107/110**.

Live search `1564939`: [Actions 37769761293](https://github.com/mendelev-main/M-POS-Android/actions/runs/37769761293) — Tests and lint успешно: JS/reference, правила версий, Kotlin и lint. APK собирается отдельным job; физическая приёмка pending.


### 109.08 — полный участок кнопки смены, 08.10.2026

Нативная shift-pill включена в MPosWorkspaceHeaderController: Manrope/navy,
white text, зелёный/красный индикатор, compact pill и 48dp. Label и status берутся
из первого открытого shift авторитетного Room document, не из текста/onclick DOM.
Формат employeeShortName сохранён, включая whitespace/первые два UTF-16 initials.
Source geometry и narrow topbar fallback остаются до native root presentation.

shiftHeaderView читает актуальный shifts document без записи/инициализации.
selectShiftHeader сравнивает его digest и expected navigation/revision в одной
Room transaction. При открытой смене подтверждается native tab=shift; при
закрытой — effect=openShift. stale changes не переключают tab/не запускают окно.
Устаревший принятый переход отменяется только своим headerToken.

NativeOpenForm.openNative запускает MPosShiftOpenDialog без openOriginal,
HTML form, select/password DOM fields. Это production путь новой кнопки; другие
legacy entry points пока относятся к следующему участку. Сумма переноса,
сотрудники и роли читаются existing native opening repository; credential
остаётся внутри Kotlin dialog и существующего native opening command.
Existing commit/ack, recovery gate и post-commit уведомления сохранены.
Cancel/fallback, pending busy и import/runtime invalidation обработаны;
SystemBack учитывает token нативного окна даже без HTML modal. Explicit fallback
возвращает reviewed форму. Global source geometry/Back presence других overlays
не удалены и этап 109.08 ещё не закрыт.

Добавлены JS boundary, Kotlin model/repository и View проверки: open/closed,
первый открытый shift, label/status, freshness/authority/no-writes, stale query,
дубли, busy/cancel/fallback/runtime/Back без DOM. Локальные тесты/lint/APK не
запускались (§17 AGENTS). Actions следующего коммита pending; tablet pending.
Список исполняемых участков: docs/NATIVE_MIGRATION_EXECUTION_RU.md.
Прогресс **114/129 (88,37%)**, 109 **7/20**. Участок не добавляет новую задачу
в общий счётчик: входит в стабильный ID 109.08.

Shift header `b38a2c4`: [Actions 37772473180](https://github.com/mendelev-main/M-POS-Android/actions/runs/37772473180) — Tests and lint успешно (JS, Kotlin, lint); участок вычеркнут в execution checklist. Tablet acceptance pending.


### 109.08 — все входы открытия смены без HTML формы, 08.10.2026

Production openShiftModal теперь сразу вызывает NativeOpenForm.openNative:
кнопка workspace, действие native shift screen и topbar используют один путь.
Hidden select/password/HTML modal не создаются; старый openOriginal вызывается
только при explicit compatibility flags или «Прежняя форма». Повторное нажатие
сохраняет тот же token/диалог и не отправляет вторую команду. Отказ bridge не
переключает молча на старую форму. Existing save/ack/recovery/effects не изменены.

NativeOpenForm уведомляет presentation через mpos-native-open-state на open и
abandon. Header скрывается и возвращается по состоянию окна; shift screen
остаётся native под диалогом, получает только block/unblock, без нового Room read.
Workspace сохраняет native фон и блокирует команды; закрытие окна разблокирует
его без HTML MutationObserver. Это блокировка окна, а не recovery failure:
сообщение о перезапуске при ней не появляется. Back и restart/import защищены
existing token/runtime guards предыдущего участка.

Написаны проверки всех default entries, повторного нажатия, отказа bridge,
событий без DOM, блокировки/разблокировки native shift/workspace/header, сохранения
и explicit compatibility path. Локально JS/JVM/lint/APK не запускались (§17);
Actions следующего коммита pending. Физическая приёмка pending.
109.08 остаётся in_progress; **114/129 (88,37%)**, внутри 109 **7/20**.

All opening entries `4c44c33`: [Actions 37773405619](https://github.com/mendelev-main/M-POS-Android/actions/runs/37773405619) — Tests and lint и Build APK успешно. Второй полный участок вычеркнут в execution checklist; следующий — раскладка рабочей зоны с полным набором действий. Физическая приёмка pending.

### 109.08 завершён — 08.10.2026

Полностью закрыт стабильный этап навигации workspace: native owner вкладок,
query/live search, типизированные category/folder routes, complete shift control,
все default входы открытия смены, complete native layout editor и lifecycle Back.
Layout read/commit используют authoritative Room, transaction/digest CAS/FIFO;
права, metadata, лимит 20, folder rules, strict IDs, ключи и backup v13 сохранены.
Доказательства и граница: [109.08](NATIVE_WORKSPACE_NAVIGATION_10908_RU.md).

Actions **37782566450**, main **c671e38**: JS/Kotlin tests и lint success;
Подписанная APK сборка success. Ранее e867895 прошёл полный Actions 37781537606
с APK, 1e27c5f — rollback SQLite и folder validation checks. Локальная APK
не собиралась. Physical acceptance pending в 110.

**115/129 — 89,15%; внутри 109 8/20; крупных 107/110.** Это количество
инженерных задач; знаменатель и scope не изменены, не native coverage.
Следующий этап **109.09** — каталог/корзина/product editor без HTML extraction
и mounted JS handlers. Geometry/DOM projection пока presentation зависимости;
runtime WebView будет удалён по 109.19, физический gate — 110.

### 109.09 — native workspace read model и типизированные действия, 08.10.2026

Default workspace теперь MPosWorkspaceReadRepository/ReadModel: products, layout,
posNavigation, root shift/recovery и parked count читаются из authoritative Room
в согласованной transaction. Плитки, имена, цвета, символы, координаты, units,
доступность simple/composite/unlimited, строки/скидки/итоги/доставка и кнопки
формирует Kotlin. Pricing/loyalty/availability переиспользуют parity engines.
No DOM labels/tile/cart extraction; только bounds и CSS geometry оболочки.
Typed action table вызывает явные allowlisted команды без скрытых node.click()
и simulated DOM events. Payment/WEB/parked/customer/settings пока handoff своих
этапов; права, retry, persist-before-effects правила не менялись.

Live search сохраняет initial mounted scope через native catalogScope, а render
поиск категории пересекает папки. Folder query не меняет содержимое папки, как
reviewed POS. IDs и dataset string coercion, fractional units, metadata/layout,
manual-price products и legacy qty string суммирование сохранены. Старый renderer
в отдельном native-workspace-legacy.js запускается только explicit
MPosNativeWorkspaceReadModelsEnabled=false. Source sync и baseline restoration
учитывают adapter без изменения reviewed source hash.

**Честная оставшаяся граница:** корзина/config ещё передаются явным runtime order
snapshot, это не native draft authority. Product modifier/manual-price/cart-item
forms и product editor ещё source; они остаются внутри 109.09. Этап не закрыт,
прогресс **115/129, 109 — 8/20**. JS/Room/native read-model проверки добавлены;
выполняет Actions, локальные тесты/lint/APK не запускались.

### 109.09 — редактор строки корзины, 08.10.2026

Каталог/read models проверены в Actions 37787596243 (5b94b88): JS/Kotlin/lint
и signed APK success. Подключены native количество/комментарий/скидка и
atomic currentOrderSession save с CAS, recovery gate и stock preflight.
Нет HTML формы или source save handler; отмена не пишет, фон остаётся native.
Новые Room/controller/JS checks pending Actions. [Границы и проверки](NATIVE_WORKSPACE_DOMAIN_10909_RU.md).
109.09 остаётся in_progress, **115/129 (89,15%), 109 — 8/20**.

109.09 проверка текущего участка: main **1b74e9a**, [Actions 37791354617](https://github.com/mendelev-main/M-POS-Android/actions/runs/37791354617) — JS/Kotlin/lint success. Каталог, native cart presentation и полный редактор строки вычеркнуты в execution checklist. Availability рассчитывается для видимых рецептов с одним ingredient lookup; отрицательные остатки и folder exit сохраняют source parity. Полный signed run 37791017299 на 5281321 — success; текущая APK сборка также success. Остальные cart drafts/commands/forms и product editor остаются в 109.09; **115/129, 8/20**. Physical acceptance pending 110.

### 109.09 — native configured add, модификаторы и ручная цена

Подключены MPosCartAddModel/Repository/Controller: authoritative каталог/корзина,
нормализация и выбор modifiers, min/max, price formation, merge и atomic
currentOrderSession + projection save. Source handlers/HTML inputs на этом пути
не вызываются; normal/manual/folder правила сохранены. Barrier не позволяет
фоновому JS save перезаписать корзину до native ack; uncertain commit блокирует
повтор/legacy save до восстановления. [Граница, rollback и физические кейсы](NATIVE_CART_ADD_10909_RU.md).
JS/Room/controller parity/failure проверки pending Actions; local tests/APK
не запускались (§17). 109.09 in_progress, **115/129, внутри 109 — 8/20**.
