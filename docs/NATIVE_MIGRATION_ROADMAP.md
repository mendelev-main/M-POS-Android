# Native Migration Roadmap

## Актуальный план и счётчик

[Реестр задач](KOTLIN_MIGRATION_TASKS.md) — **103/110 выполнено (93,64%)**
после 104. Source of truth: `kotlin-migration-tasks.json` и
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
